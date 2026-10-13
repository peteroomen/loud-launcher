package io.github.peteroomen.loud.system

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

internal object WeatherStorage {
    private val mutex = Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences("loud_weather", Context.MODE_PRIVATE)
    fun preferences(context: Context): SharedPreferences = prefs(context)
    fun read(context: Context): WeatherState {
        val p = prefs(context)
        val place = runCatching { JSONObject(p.getString("place", null) ?: "").toPlace() }.getOrDefault(WeatherPlace.HAMILTON)
        val temperature = p.getString("temperature", null)?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val reading = temperature?.let { WeatherReading(it, p.getInt("code", 0)) }
        return WeatherState(place, reading, p.getLong("updated", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli), offline = p.getBoolean("offline", false), error = p.getString("error", null))
    }
    fun select(context: Context, place: WeatherPlace) {
        if (!place.latitude.isFinite() || !place.longitude.isFinite() || place.latitude !in -90.0..90.0 || place.longitude !in -180.0..180.0) return
        prefs(context).edit().clear().putString("place", place.toJson().toString()).apply()
    }
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(45, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("loud-weather", ExistingPeriodicWorkPolicy.KEEP, request)
    }
    suspend fun refresh(context: Context, force: Boolean = false): WeatherState = mutex.withLock {
        withContext(Dispatchers.IO) {
            val p = prefs(context)
            if (read(context).place.usesLocation && context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) select(context, WeatherPlace.HAMILTON)
            val old = read(context)
            val now = Instant.now()
            val attempt = p.getLong("attempt", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            if (!weatherRefreshDue(attempt ?: old.lastUpdated, now, force)) return@withContext old
            p.edit().putLong("attempt", now.toEpochMilli()).apply()
            if (!online(context)) {
                p.edit().putBoolean("offline", true).putString("error", "No internet connection").apply()
                return@withContext read(context)
            }
            runCatching {
                val current = getJson("https://api.open-meteo.com/v1/forecast?latitude=${old.place.latitude}&longitude=${old.place.longitude}&current=temperature_2m,weather_code&temperature_unit=celsius&timezone=auto").getJSONObject("current")
                val temperature = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                check(temperature.isFinite())
                // Never apply the response for a previous city after selection changes.
                if (read(context).place == old.place) p.edit().putString("temperature", temperature.toString()).putInt("code", code)
                    .putLong("updated", now.toEpochMilli()).putBoolean("offline", false).remove("error").apply()
            }.onFailure { failure ->
                if (read(context).place == old.place) p.edit().putBoolean("offline", !online(context) || failure is java.io.IOException).putString("error", "Weather unavailable. Try again later.").apply()
            }
            if (read(context).place.usesLocation && context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) select(context, WeatherPlace.HAMILTON)
            read(context)
        }
    }
    suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val results = getJson("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=8&language=en&format=json").optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i -> val item = results.getJSONObject(i)
            WeatherPlace(item.getString("name"), item.getDouble("latitude"), item.getDouble("longitude"), item.optString("country"), item.optString("timezone", "auto"))
        }
    }
    private fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000; connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("User-Agent", "LoudLauncher/1.0")
            check(connection.responseCode in 200..299) { "Weather server unavailable" }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    private fun WeatherPlace.toJson() = JSONObject().put("name", name).put("latitude", latitude).put("longitude", longitude).put("country", country).put("timezone", timezone).put("usesLocation", usesLocation)
    private fun JSONObject.toPlace() = WeatherPlace(getString("name"), getDouble("latitude"), getDouble("longitude"), optString("country"), optString("timezone", "auto"), optBoolean("usesLocation", false))
}
class WeatherRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val state = WeatherStorage.refresh(applicationContext)
        if (state.error == null && state.reading != null) Result.success() else Result.failure()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { Result.failure() }
}
