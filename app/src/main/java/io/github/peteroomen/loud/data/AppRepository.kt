package io.github.peteroomen.loud.data
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import io.github.peteroomen.loud.model.SearchApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class AppEntry(val id: String,val label: String,val packageName: String,val component: ComponentName,val user: UserHandle,val profileSerial: Long,val installedAt: Long=0) {
    fun searchApp()=SearchApp(id,label,packageName,installedAt)
}
data class AppShortcut(val id: String,val label: String)
class AppRepository internal constructor(context: Context,private val scope: CoroutineScope,private val layout: LayoutRepository) {
    private val context=context.applicationContext
    private val launcher=this.context.getSystemService(LauncherApps::class.java)
    private val users=this.context.getSystemService(UserManager::class.java)
    private val refreshLock=Mutex()
    @Volatile private var infos: Map<String,LauncherActivityInfo> = emptyMap()
    @Volatile private var closed=false
    private val appState=MutableStateFlow<List<AppEntry>>(emptyList()); val apps: StateFlow<List<AppEntry>> = appState.asStateFlow()
    private val loadingState=MutableStateFlow(true); val loading: StateFlow<Boolean> = loadingState.asStateFlow()
    private val errorState=MutableStateFlow<String?>(null); val error: StateFlow<String?> = errorState.asStateFlow()
    internal val iconGeneration=MutableStateFlow(0L)
    private val callback=object: LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String,user: UserHandle)=refresh()
        override fun onPackageAdded(packageName: String,user: UserHandle)=refresh()
        override fun onPackageChanged(packageName: String,user: UserHandle)=refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>,user: UserHandle,replacing: Boolean)=refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>,user: UserHandle,replacing: Boolean)=refresh()
        override fun onPackagesSuspended(packageNames: Array<out String>,user: UserHandle)=refresh()
        override fun onPackagesUnsuspended(packageNames: Array<out String>,user: UserHandle)=refresh()
        override fun onShortcutsChanged(packageName: String,shortcuts: MutableList<ShortcutInfo>,user: UserHandle)=refresh()
    }
    private var callbackRegistered=false
    init {
        runCatching { launcher.registerCallback(callback,Handler(Looper.getMainLooper())); callbackRegistered=true }.onFailure { errorState.value=it.message ?: "Package updates unavailable" }
        refresh()
    }
    fun refresh() {
        if(closed) return
        scope.launch { refreshLock.withLock {
            if(closed) return@withLock
            loadingState.value=true
            try {
                val found=LinkedHashMap<String,LauncherActivityInfo>(); val entries=mutableListOf<AppEntry>()
                val profiles=runCatching { launcher.profiles }.getOrElse { listOf(Process.myUserHandle()) }
                profiles.forEach { user ->
                    val serial=users.getSerialNumberForUser(user)
                    if(serial>=0) {
                        val list=runCatching { launcher.getActivityList(null,user) }.getOrElse { emptyList() }
                        list.filter { it.componentName.packageName!=context.packageName }.forEach { info ->
                            val id="$serial:${info.componentName.flattenToShortString()}"; found[id]=info
                            entries+=AppEntry(id,info.label.toString(),info.componentName.packageName,info.componentName,user,serial,info.firstInstallTime)
                        }
                    }
                }
                infos=found; iconGeneration.value++
                appState.value=entries.sortedWith(compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }.thenBy { it.id }); errorState.value=null
            } catch(e: CancellationException) { throw e } catch(e: Exception) { errorState.value=e.message ?: "Unable to list apps" } finally { loadingState.value=false }
        } }
    }
    internal fun activityInfo(id: String)=infos[id]
    fun launch(app: AppEntry,fromSearch: Boolean=false): Result<Unit> = runCatching {
        check(infos.containsKey(app.id)) { "App unavailable" }; launcher.startMainActivity(app.component,app.user,null,null)
        scope.launch { runCatching { layout.recordLaunch(app.id,fromSearch) } }; Unit
    }
    fun openAppInfo(app: AppEntry): Result<Unit> = runCatching { launcher.startAppDetailsActivity(app.component,app.user,null,null) }
    fun uninstall(app: AppEntry): Result<Unit> = runCatching {
        check(app.user==Process.myUserHandle()) { "Remove work-profile apps through App info" }
        context.startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    suspend fun shortcuts(app: AppEntry): List<AppShortcut> = withContext(Dispatchers.IO) {
        if(!launcher.hasShortcutHostPermission()) return@withContext emptyList()
        runCatching { launcher.getShortcuts(LauncherApps.ShortcutQuery().setPackage(app.packageName).setActivity(app.component).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),app.user).orEmpty().filter { it.isEnabled }.map { AppShortcut(it.id,it.shortLabel?.toString() ?: it.id) } }.getOrDefault(emptyList())
    }
    fun launchShortcut(app: AppEntry,shortcutId: String): Result<Unit> = runCatching {
        check(launcher.hasShortcutHostPermission()) { "Make Loud default home to use app shortcuts" }; launcher.startShortcut(app.packageName,shortcutId,null,null,app.user)
        scope.launch { runCatching { layout.recordLaunch(app.id) } }; Unit
    }
    fun close() { if(!closed) { closed=true; if(callbackRegistered) runCatching { launcher.unregisterCallback(callback) } } }
}
