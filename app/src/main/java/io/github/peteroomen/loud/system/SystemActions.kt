package io.github.peteroomen.loud.system

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.time.Instant

/** Supported public intents only. false is visible setup/unavailable state, never fake success. */
object SystemActions {
    const val POWER_BUBBLE_PACKAGE = "io.github.peteroomen.powerbubble"
    const val ACTION_SCREEN_OFF = "$POWER_BUBBLE_PACKAGE.action.SCREEN_OFF"
    const val ACTION_NOTIFICATIONS = "$POWER_BUBBLE_PACKAGE.action.NOTIFICATIONS"
    fun start(context: Context, intent: Intent): Boolean = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    fun screenOff(context: Context) = start(context, Intent(ACTION_SCREEN_OFF).setPackage(POWER_BUBBLE_PACKAGE))
    fun expandNotifications(context: Context) = start(context, Intent(ACTION_NOTIFICATIONS).setPackage(POWER_BUBBLE_PACKAGE))
    fun openAlarms(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun openCalendar(context: Context, at: Instant = Instant.now()) = start(context, Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(at.toEpochMilli().toString()).build()))
    fun openCalendarEvent(context: Context, event: CalendarEvent) = start(context, Intent(Intent.ACTION_VIEW, CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(event.id.toString()).build()).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli()).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli()))
    fun openApp(context: Context, packageName: String): Boolean = context.packageManager.getLaunchIntentForPackage(packageName)?.let { start(context, it) } ?: false
    fun openWeb(context: Context, url: String): Boolean { val uri = Uri.parse(url); return if (uri.scheme == "https" || uri.scheme == "http") start(context, Intent(Intent.ACTION_VIEW, uri)) else false }
    fun webSearch(context: Context, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val url = Uri.parse("https://www.google.com/search?q=${Uri.encode(trimmed)}")
        return start(context, Intent(Intent.ACTION_VIEW, url).setPackage("com.android.chrome")) ||
            start(context, Intent(Intent.ACTION_VIEW, url)) ||
            start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, trimmed))
    }
    fun openNotificationSettings(context: Context) = start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openUsageSettings(context: Context) = start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun openAppSettings(context: Context) = start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    fun powerBubbleAvailable(context: Context): Boolean = context.packageManager.resolveActivity(Intent(ACTION_SCREEN_OFF).setPackage(POWER_BUBBLE_PACKAGE), PackageManager.MATCH_DEFAULT_ONLY) != null
    fun installedDeveloperApps(context: Context): List<DeveloperApp> {
        val known = listOf("com.anthropic.claude", "com.openai.chatgpt", "com.github.android")
        val launchable = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return known.mapNotNull { pkg -> launchable.firstOrNull { it.activityInfo.packageName == pkg }?.let { DeveloperApp(pkg, it.loadLabel(context.packageManager).toString()) } }
    }
}
