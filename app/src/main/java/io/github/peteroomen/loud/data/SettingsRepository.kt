package io.github.peteroomen.loud.data
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.peteroomen.loud.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
private val Context.loudPreferences by preferencesDataStore("loud_settings")
class SettingsRepository internal constructor(context: Context,private val mutex: Mutex,private val ready: CompletableDeferred<Unit>) {
    private val store=context.applicationContext.loudPreferences
    private val key=stringPreferencesKey("settings_v1")
    private val state=MutableStateFlow(LauncherSettings())
    val settings: StateFlow<LauncherSettings> = state.asStateFlow()
    internal suspend fun initialize() { state.value=store.data.first()[key]?.let { BackupCodec.decodeSettings(it) } ?: LauncherSettings() }
    internal suspend fun replace(value: LauncherSettings) { val json=BackupCodec.encodeSettings(value); store.edit { it[key]=json }; state.value=value }
    suspend fun update(transform: (LauncherSettings)->LauncherSettings) { ready.await(); mutex.withLock { withContext(NonCancellable) { replace(transform(state.value)) } } }
    suspend fun renameApp(id: String,name: String?)=update { old -> val names=old.appPreferences.renames.toMutableMap(); if(name.isNullOrBlank()) names.remove(id) else names[id]=name.trim(); old.copy(appPreferences=old.appPreferences.copy(renames=names)) }
    suspend fun hideApp(id: String,hidden: Boolean)=update { old -> old.copy(appPreferences=old.appPreferences.copy(hiddenIds=if(hidden) old.appPreferences.hiddenIds+id else old.appPreferences.hiddenIds-id)) }
}
