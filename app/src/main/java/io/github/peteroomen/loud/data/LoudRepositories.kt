package io.github.peteroomen.loud.data
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import io.github.peteroomen.loud.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retain one application instance across Activity recreation. */
class LoudRepositories(context: Context) {
    private val context=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex(); private val ready=CompletableDeferred<Unit>()
    private val db=Room.databaseBuilder(this.context,LauncherDatabase::class.java,"loud.db").build()
    val settings=SettingsRepository(this.context,mutex,ready)
    val layout=LayoutRepository(db,scope,mutex,ready)
    val apps=AppRepository(this.context,scope,layout)
    val icons=IconCache(this.context,apps)
    private val errorState=MutableStateFlow<String?>(null); val initializationError: StateFlow<String?> = errorState
    init { scope.launch { try {
        settings.initialize()
        db.dao().meta("pending-settings")?.let { settings.replace(BackupCodec.decodeSettings(it)); db.dao().deleteMeta("pending-settings") }
        db.withTransaction { if(db.dao().meta("initialized")==null) { db.dao().putTiles(defaultTiles().mapIndexed { i,t -> t.entity(i) }); db.dao().putMeta(MetaEntity("initialized","1")) } }
        ready.complete(Unit)
    } catch(e: Exception) { errorState.value=e.message ?: "Unable to load launcher data"; ready.completeExceptionally(e) } } }
    private suspend fun snapshot()=LauncherBackup(settings.settings.value,db.dao().tiles().map { it.model() },db.dao().history().associate { it.appId to it.model() },db.dao().web().map { it.model() })
    suspend fun exportBackup(): String { ready.await(); return mutex.withLock { BackupCodec.encode(snapshot()) } }
    suspend fun importBackup(json: String): Result<Unit> {
        val parsed=try { withContext(Dispatchers.Default) { BackupCodec.decode(json) } } catch(e: CancellationException) { throw e } catch(e: Exception) { return Result.failure(e) }
        return try { ready.await(); mutex.withLock { replaceCoordinated(parsed) }; Result.success(Unit) } catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }
    }
    private suspend fun writeDb(next: LauncherBackup) { val dao=db.dao(); db.withTransaction {
        dao.putMeta(MetaEntity("pending-settings",BackupCodec.encodeSettings(next.settings)))
        dao.clearTiles(); dao.putTiles(next.tiles.mapIndexed { i,t -> t.entity(i) })
        dao.clearHistory(); dao.putHistory(next.history.map { (id,h) -> HistoryEntity(id,h.count,h.searchCount,h.lastOpened) })
        dao.clearWeb(); dao.putWeb(next.webHistory.map { WebEntity(it.url,it.title,it.count,it.lastOpened) })
    } }
    /** Crash-safe cross-store commit: Room journal finishes DataStore update on next startup. */
    private suspend fun replaceCoordinated(next: LauncherBackup)=withContext(NonCancellable) {
        val old=snapshot(); writeDb(next)
        try { settings.replace(next.settings); db.dao().deleteMeta("pending-settings") }
        catch(e: Exception) { writeDb(old); settings.replace(old.settings); db.dao().deleteMeta("pending-settings"); throw e }
    }
    fun openWebsite(url: String,title: String=""): Result<Unit> = runCatching {
        BackupCodec.validateUrl(url); require(title.length<=200)
        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        scope.launch { runCatching { layout.recordWebsite(url,title) } }; Unit
    }
    suspend fun reset() { ready.await(); mutex.withLock { replaceCoordinated(LauncherBackup(LauncherSettings(),defaultTiles(),emptyMap())) }; icons.clear() }
    fun close() { apps.close(); scope.cancel(); db.close() }
}
