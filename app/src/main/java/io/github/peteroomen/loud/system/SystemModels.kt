package io.github.peteroomen.loud.system

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class ClockState(val now: Instant = Instant.now(), val zone: ZoneId = ZoneId.systemDefault(), val nextAlarm: Instant? = null)
data class BatteryState(val levelPercent: Int? = null, val charging: Boolean = false)
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double, val country: String = "", val timezone: String = "auto", val usesLocation: Boolean = false) {
    companion object { val HAMILTON = WeatherPlace("Hamilton", -37.787, 175.279, "New Zealand", "Pacific/Auckland") }
}
data class WeatherReading(val temperatureC: Double, val weatherCode: Int)
data class WeatherState(val place: WeatherPlace = WeatherPlace.HAMILTON, val reading: WeatherReading? = null, val lastUpdated: Instant? = null, val loading: Boolean = false, val offline: Boolean = false, val error: String? = null)
data class CalendarEvent(val id: Long, val title: String, val start: Instant, val end: Instant, val allDay: Boolean, val calendarName: String)
data class CalendarState(val month: YearMonth = YearMonth.now(), val events: List<CalendarEvent> = emptyList(), val permissionGranted: Boolean = false, val loading: Boolean = false, val error: String? = null)
data class NotificationState(val connected: Boolean = false, val counts: Map<String, Int> = emptyMap()) { val total: Int get() = counts.values.sum() }
data class AppUsage(val packageName: String, val foregroundMs: Long, val lastUsed: Instant)
data class UsageState(val permissionGranted: Boolean = false, val entries: List<AppUsage> = emptyList(), val error: String? = null)
data class PermissionState(val calendar: Boolean = false, val notificationAccess: Boolean = false, val usageAccess: Boolean = false, val coarseLocation: Boolean = false, val powerBubbleAvailable: Boolean = false)
data class DeveloperApp(val packageName: String, val label: String)

/** Group summaries never create a second badge alongside their children. */
data class NotificationCandidate(val packageName: String, val group: String?, val summary: Boolean, val clearable: Boolean, val badgeAllowed: Boolean)
fun notificationCounts(items: List<NotificationCandidate>): Map<String, Int> = items
    .filter { it.clearable && it.badgeAllowed && !it.summary }.groupingBy { it.packageName }.eachCount()
fun weatherRefreshDue(lastUpdated: Instant?, now: Instant, force: Boolean): Boolean =
    force || lastUpdated == null || now.isBefore(lastUpdated) || java.time.Duration.between(lastUpdated, now).toMinutes() >= 30
internal data class CalendarWindow(val localStart: Instant, val localEnd: Instant, val utcStart: Instant, val utcEnd: Instant) {
    val queryStart: Instant get() = minOf(localStart, utcStart)
    val queryEnd: Instant get() = maxOf(localEnd, utcEnd)
    fun includes(event: CalendarEvent): Boolean = if (event.allDay) event.end > utcStart && event.start < utcEnd else event.end > localStart && event.start < localEnd
}
internal fun calendarWindow(month: YearMonth, zone: ZoneId): CalendarWindow = CalendarWindow(
    month.atDay(1).atStartOfDay(zone).toInstant(), month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
    month.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), month.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
)
