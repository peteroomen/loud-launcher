package io.github.peteroomen.loud.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil

data class WidgetProvider(
    val label: String, val appLabel: String, val providerComponent: String,
    val profileSerial: Long, val profileLabel: String, val minWidthDp: Int,
    val minHeightDp: Int, val info: AppWidgetProviderInfo,
)

data class WidgetProviderCatalog(val providers: List<WidgetProvider>, val message: String? = null)

data class WidgetSelection(
    val appWidgetId: Int, val providerComponent: String, val profileSerial: Long, val label: String,
    val minWidthDp: Int = 1, val minHeightDp: Int = 1,
) {
    fun toTileConfig(): Map<String, String> = mapOf(
        "widgetId" to appWidgetId.toString(), "provider" to providerComponent,
        "profileSerial" to profileSerial.toString(), "title" to label,
        "minWidthDp" to minWidthDp.toString(), "minHeightDp" to minHeightDp.toString(),
    )
}

/** One per Activity. Mutations run on main; installedProviders() may run on IO. */
class WidgetHostController(private val activity: Activity) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val users = activity.getSystemService(UserManager::class.java)
    private val prefs = activity.getSharedPreferences("loud_widget_host", Context.MODE_PRIVATE)
    private val host = object : AppWidgetHost(activity, HOST_ID) {
        override fun onAppWidgetRemoved(appWidgetId: Int) { super.onAppWidgetRemoved(appWidgetId); revision++ }
        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo) {
            super.onProviderChanged(appWidgetId, appWidget); revision++
        }
        override fun onProvidersChanged() { super.onProvidersChanged(); revision++ }
    }
    internal var revision by mutableIntStateOf(0)
        private set
    var onWidgetReady: (WidgetSelection) -> Unit = {}
    var onMessage: (String) -> Unit = {}
    private var listening = false
    private val pendingId get() = prefs.getInt("pending_id", AppWidgetManager.INVALID_APPWIDGET_ID)
    private val pendingStage get() = prefs.getString("stage", null)

    fun installedProviders(): WidgetProviderCatalog {
        val providers = mutableListOf<WidgetProvider>()
        var profileUnavailable = false
        val profiles = runCatching { users.userProfiles }.getOrElse {
            profileUnavailable = true; listOf(Process.myUserHandle())
        }
        profiles.forEach { profile ->
            try {
                manager.getInstalledProvidersForProfile(profile).forEach providerLoop@{ info ->
                    if (info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN == 0 ||
                        info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER != 0) return@providerLoop
                    val serial = users.getSerialNumberForUser(profile)
                    val appLabel = runCatching { info.activityInfo.applicationInfo.loadLabel(activity.packageManager).toString() }
                        .getOrDefault(info.provider.packageName)
                    providers += WidgetProvider(
                        info.loadLabel(activity.packageManager).orEmpty().ifBlank { appLabel }, appLabel,
                        info.provider.flattenToString(), serial,
                        if (profile == Process.myUserHandle()) {
                            if (users.isManagedProfile) "Current work profile ($serial)" else "Personal profile ($serial)"
                        } else "Other / work profile ($serial)",
                        pixelsToDp(info.minWidth), pixelsToDp(info.minHeight), info,
                    )
                }
            } catch (_: RuntimeException) { profileUnavailable = true }
        }
        val message = when {
            providers.isEmpty() -> "Android is not offering any home-screen widget providers. Install or enable an app with widgets; work-profile widgets may be blocked by your administrator. Google Calendar and Teams appear here only if they offer widgets on this device."
            profileUnavailable -> "Some profile widgets are unavailable. Enable the work profile or check its administrator policy."
            else -> null
        }
        return WidgetProviderCatalog(providers.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }, { it.profileSerial })), message)
    }

    @Suppress("DEPRECATION")
    fun beginAdd(provider: WidgetProvider, widthDp: Int = 280, heightDp: Int = 180) {
        if (provider.minWidthDp > MAX_CONTENT_DP || provider.minHeightDp > MAX_CONTENT_DP) {
            onMessage("This widget requires an unusually large area (${provider.minWidthDp} × ${provider.minHeightDp} dp). Choose another widget provider.")
            return
        }
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            onMessage("Finish or cancel the widget you are already adding."); return
        }
        try {
            val id = host.allocateAppWidgetId()
            // Persist before crossing app boundaries; READY is retained until Room confirms its save.
            val recorded = prefs.edit().putInt("pending_id", id).putString("stage", BIND)
                .putString("provider", provider.providerComponent).putLong("profile", provider.profileSerial)
                .putString("label", provider.label).putInt("min_width", provider.minWidthDp)
                .putInt("min_height", provider.minHeightDp).commit()
            if (!recorded) {
                host.deleteAppWidgetId(id); clearPending(); onMessage("Widget setup could not be saved. Try again."); return
            }
            val options = sizeOptions(maxOf(widthDp, provider.minWidthDp).coerceAtMost(MAX_CONTENT_DP),
                maxOf(heightDp, provider.minHeightDp).coerceAtMost(MAX_CONTENT_DP))
            if (manager.bindAppWidgetIdIfAllowed(id, provider.info.profile, provider.info.provider, options)) configurePending()
            else activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }, REQUEST_BIND)
        } catch (_: RuntimeException) {
            cancelPending(); onMessage("Android could not start widget binding. The provider may be unavailable or blocked by profile policy.")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == REQUEST_RECONFIGURE) {
            if (resultCode != Activity.RESULT_OK) onMessage("Widget settings cancelled; your existing widget was kept.")
            revision++; return true
        }
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return true
        if (requestCode == REQUEST_BIND && pendingStage != BIND) return true
        if (requestCode == REQUEST_CONFIGURE && pendingStage != CONFIGURE) return true
        if (resultCode != Activity.RESULT_OK) { cancelPending(); onMessage("Widget addition cancelled.") }
        else if (requestCode == REQUEST_BIND) configurePending() else finishPending()
        return true
    }

    private fun configurePending() {
        val info = providerInfo(pendingId)
        if (info == null || info.provider.flattenToString() != prefs.getString("provider", null) ||
            users.getSerialNumberForUser(info.profile) != prefs.getLong("profile", -1)) {
            cancelPending(); onMessage("Widget binding did not complete or the provider is unavailable."); return
        }
        if (info.configure == null) { finishPending(); return }
        prefs.edit().putString("stage", CONFIGURE).commit()
        try {
            // Public host API routes personal and work-profile configuration correctly.
            host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQUEST_CONFIGURE, null)
        } catch (_: RuntimeException) {
            cancelPending(); onMessage("This widget's setup activity could not be opened. Try enabling the app or its profile.")
        }
    }

    private fun finishPending() {
        if (providerInfo(pendingId) == null) {
            cancelPending(); onMessage("The widget provider is no longer available."); return
        }
        prefs.edit().putString("stage", READY).commit()
        pendingReady()?.let(onWidgetReady); revision++
    }

    /** Recover only after Room is loaded; deduplicate by widgetId before adding a tile. */
    fun pendingReady(): WidgetSelection? = if (pendingStage == READY && pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        WidgetSelection(pendingId, prefs.getString("provider", "").orEmpty(), prefs.getLong("profile", -1),
            prefs.getString("label", "Widget").orEmpty(), prefs.getInt("min_width", 1).coerceIn(1, MAX_CONTENT_DP),
            prefs.getInt("min_height", 1).coerceIn(1, MAX_CONTENT_DP))
    } else null

    fun confirmPersisted(appWidgetId: Int) {
        if (pendingId == appWidgetId && pendingStage == READY) clearPending()
    }

    /** User cancellation is offered only before persistence; READY may be saving in Room. */
    fun hasUnfinishedSetup(): Boolean = pendingStage == BIND || pendingStage == CONFIGURE

    fun cancelPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) deleteWidget(pendingId)
        clearPending()
    }

    private fun clearPending() {
        prefs.edit().remove("pending_id").remove("stage").remove("provider").remove("profile").remove("label")
            .remove("min_width").remove("min_height").commit()
    }

    fun deleteWidget(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        runCatching { host.deleteAppWidgetId(appWidgetId) }.onFailure {
            onMessage("Android could not release this widget yet; Loud will retry when the home screen loads.")
        }; revision++
    }

    fun reconcileWidgets(persistedIds: Set<Int>) {
        if (pendingId in persistedIds && pendingStage == READY) confirmPersisted(pendingId)
        runCatching { host.appWidgetIds }.getOrNull()?.forEach { id ->
            if (id !in persistedIds && id != pendingId) deleteWidget(id)
        }
    }

    fun reconfigure(appWidgetId: Int) {
        if (providerInfo(appWidgetId)?.configure == null) {
            onMessage("This widget does not offer a settings activity."); return
        }
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_RECONFIGURE, null) }
            .onFailure { onMessage("Widget settings are unavailable. Enable the app or its work profile.") }
    }

    fun startListening() {
        if (listening) return
        runCatching { host.startListening(); listening = true; revision++ }
            .onFailure { onMessage("Android widget updates are temporarily unavailable.") }
    }
    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }; listening = false
    }
    internal fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()
    internal fun createView(appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView = host.createView(activity, appWidgetId, info)
    internal fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        if (widthDp > 0 && heightDp > 0) runCatching { view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp) }
    }
    private fun pixelsToDp(pixels: Int) = ceil(pixels / activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun sizeOptions(widthDp: Int, heightDp: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.coerceAtLeast(1))
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.coerceAtLeast(1))
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.coerceAtLeast(1))
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.coerceAtLeast(1))
    }
    companion object {
        const val HOST_ID = 0x4c4f5544
        const val REQUEST_BIND = 6101
        const val REQUEST_CONFIGURE = 6102
        const val REQUEST_RECONFIGURE = 6103
        const val MAX_CONTENT_DP = 4096
        private const val BIND = "bind"
        private const val CONFIGURE = "configure"
        private const val READY = "ready"
    }
}
