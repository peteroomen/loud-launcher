package io.github.peteroomen.loud.system

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.location.LocationManager
import android.os.BatteryManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class SystemRepository(context: Context) : DefaultLifecycleObserver, Closeable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableClock = MutableStateFlow(ClockState())
    val clock: StateFlow<ClockState> = mutableClock.asStateFlow()
    private val mutableBattery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = mutableBattery.asStateFlow()
    private val mutableWeather = MutableStateFlow(WeatherStorage.read(app))
    val weather: StateFlow<WeatherState> = mutableWeather.asStateFlow()
    private val mutableCalendar = MutableStateFlow(CalendarState())
    val calendar: StateFlow<CalendarState> = mutableCalendar.asStateFlow()
    val notifications = NotificationStateSource.state
    val media = MediaState.nowPlaying
    private val mutableUsage = MutableStateFlow(UsageState())
    val usage: StateFlow<UsageState> = mutableUsage.asStateFlow()
    private val mutablePermissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = mutablePermissions.asStateFlow()
    private var started = false
    private var closed = false
    private var calendarObserving = false
    private var calendarJob: Job? = null
    private var usageJob: Job? = null
    private var weatherJob: Job? = null
    private var locationCancellation: CancellationSignal? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post {
        if (!closed) mutableWeather.value = WeatherStorage.read(app).copy(loading = weatherJob?.isActive == true)
    } }
    private val calendarObserver = object : ContentObserver(handler) { override fun onChange(selfChange: Boolean) { refreshPermissions(); loadCalendar() } }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClock()
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                mutableBattery.value = BatteryState(if (level >= 0 && scale > 0) level * 100 / scale else null,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            }
            if (intent?.action == Intent.ACTION_DATE_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED) loadCalendar()
        }
    }
    init {
        WeatherStorage.preferences(app).registerOnSharedPreferenceChangeListener(preferenceListener)
        WeatherStorage.schedule(app)
        refreshPermissions(); updateClock()
    }
    override fun onStart(owner: LifecycleOwner) {
        if (started || closed) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else app.registerReceiver(receiver, filter)
        refresh()
    }
    override fun onResume(owner: LifecycleOwner) = refresh()
    override fun onStop(owner: LifecycleOwner) {
        if (!started) return
        started = false
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { app.contentResolver.unregisterContentObserver(calendarObserver) }; calendarObserving = false
        locationCancellation?.cancel(); locationCancellation = null
        calendarJob?.cancel(); usageJob?.cancel()
    }
    fun refresh() { if (closed) return; refreshPermissions(); updateClock(); loadCalendar(); loadUsage(); refreshWeather() }
    private fun allowed(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun usageAllowed(): Boolean = runCatching {
        app.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
    private fun refreshPermissions() {
        val notificationAccess = app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(app, LoudNotificationListener::class.java))
        mutablePermissions.value = PermissionState(allowed(Manifest.permission.READ_CALENDAR), notificationAccess, usageAllowed(), allowed(Manifest.permission.ACCESS_COARSE_LOCATION), SystemActions.powerBubbleAvailable(app))
        if (!notificationAccess) NotificationStateSource.clear()
        if (!mutablePermissions.value.calendar) {
            calendarJob?.cancel(); mutableCalendar.value = CalendarState(month = mutableCalendar.value.month)
            if (calendarObserving) { runCatching { app.contentResolver.unregisterContentObserver(calendarObserver) }; calendarObserving = false }
        } else if (started && !calendarObserving) {
            calendarObserving = runCatching { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver); true }.getOrDefault(false)
        }
        if (!mutablePermissions.value.usageAccess) { usageJob?.cancel(); mutableUsage.value = UsageState() }
        if (!mutablePermissions.value.coarseLocation && mutableWeather.value.place.usesLocation) {
            locationCancellation?.cancel(); locationCancellation = null
            WeatherStorage.select(app, WeatherPlace.HAMILTON); mutableWeather.value = WeatherStorage.read(app)
        }
    }
    private fun updateClock() {
        val alarm = runCatching { app.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let(Instant::ofEpochMilli) }.getOrNull()
        mutableClock.value = ClockState(Instant.now(), ZoneId.systemDefault(), alarm)
    }
    fun setCalendarMonth(month: YearMonth) { mutableCalendar.value = mutableCalendar.value.copy(month = month); loadCalendar() }
    private fun loadCalendar() {
        calendarJob?.cancel()
        if (!allowed(Manifest.permission.READ_CALENDAR)) { mutableCalendar.value = CalendarState(month = mutableCalendar.value.month); return }
        val month = mutableCalendar.value.month
        mutableCalendar.value = mutableCalendar.value.copy(permissionGranted = true, loading = true, error = null)
        calendarJob = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { queryCalendar(month) } }
            if (result.exceptionOrNull() is CancellationException) return@launch
            if (!allowed(Manifest.permission.READ_CALENDAR)) { refreshPermissions(); return@launch }
            mutableCalendar.value = CalendarState(month, result.getOrDefault(emptyList()), true, false, result.exceptionOrNull()?.let { "Calendar provider unavailable" })
        }
    }
    private fun queryCalendar(month: YearMonth): List<CalendarEvent> {
        val window = calendarWindow(month, ZoneId.systemDefault())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, window.queryStart.toEpochMilli()); ContentUris.appendId(it, window.queryEnd.toEpochMilli()) }.build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
        val cursor = app.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE}=1", null, "${CalendarContract.Instances.BEGIN} ASC") ?: error("Calendar provider unavailable")
        return cursor.use { c -> buildList {
            while (c.moveToNext()) {
                val event = CalendarEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Untitled event" }, Instant.ofEpochMilli(c.getLong(2)), Instant.ofEpochMilli(c.getLong(3)), c.getInt(4) != 0, c.getString(5).orEmpty())
                if (window.includes(event)) add(event)
            }
        } }
    }
    private fun loadUsage() {
        usageJob?.cancel()
        if (!usageAllowed()) { mutableUsage.value = UsageState(); return }
        usageJob = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                app.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(now - 7 * 24 * 60 * 60 * 1000L, now)
                    .values.filter { it.totalTimeInForeground > 0 && it.packageName != app.packageName }
                    .map { AppUsage(it.packageName, it.totalTimeInForeground, Instant.ofEpochMilli(it.lastTimeUsed)) }.sortedByDescending { it.foregroundMs }
            } }
            if (result.exceptionOrNull() is CancellationException) return@launch
            if (!usageAllowed()) { mutableUsage.value = UsageState(); return@launch }
            mutableUsage.value = UsageState(true, result.getOrDefault(emptyList()), result.exceptionOrNull()?.let { "Usage provider unavailable" })
        }
    }
    fun refreshWeather(force: Boolean = false) {
        if (weatherJob?.isActive == true || closed) return
        mutableWeather.value = mutableWeather.value.copy(loading = true)
        weatherJob = scope.launch {
            try { mutableWeather.value = WeatherStorage.refresh(app, force) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableWeather.value = mutableWeather.value.copy(error = "Weather storage unavailable. Try again later.") }
            finally { mutableWeather.value = mutableWeather.value.copy(loading = false) }
        }
    }
    suspend fun searchWeatherPlaces(query: String): List<WeatherPlace> = WeatherStorage.search(query)
    fun selectWeatherPlace(place: WeatherPlace) {
        WeatherStorage.select(app, place); mutableWeather.value = WeatherStorage.read(app)
        weatherJob?.cancel(); weatherJob = null; refreshWeather(force = true)
    }
    fun useCoarseLocation(): Boolean {
        refreshPermissions()
        if (!mutablePermissions.value.coarseLocation || !started) return false
        val manager = app.getSystemService(LocationManager::class.java)
        if (!runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) return false
        locationCancellation?.cancel()
        val cancellation = CancellationSignal().also { locationCancellation = it }
        return runCatching {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, cancellation, app.mainExecutor) { location ->
                if (!closed && allowed(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (location != null) selectWeatherPlace(WeatherPlace("Current location", location.latitude, location.longitude, usesLocation = true))
                    else mutableWeather.value = mutableWeather.value.copy(error = "Location unavailable. Choose a city.")
                }
                locationCancellation = null
            }; true
        }.getOrDefault(false)
    }
    fun togglePlayPause() = MediaState.togglePlayPause()
    fun skipNext() = MediaState.skipNext()
    fun skipPrevious() = MediaState.skipPrevious()
    fun openPlayer(): Boolean = MediaState.openPlayer()
    override fun close() {
        if (closed) return
        if (started) { runCatching { app.unregisterReceiver(receiver) }; runCatching { app.contentResolver.unregisterContentObserver(calendarObserver) } }
        started = false; calendarObserving = false; closed = true
        locationCancellation?.cancel()
        WeatherStorage.preferences(app).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        scope.cancel()
    }
}
