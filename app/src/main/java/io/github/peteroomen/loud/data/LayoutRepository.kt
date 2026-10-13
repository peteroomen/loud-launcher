package io.github.peteroomen.loud.data
import androidx.room.withTransaction
import io.github.peteroomen.loud.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
internal fun TileEntity.model()=HomeTile(id,HomeTileKind.valueOf(kind),columns,rows,color,BackupCodec.decodeConfig(configJson))
internal fun HomeTile.entity(position: Int)=TileEntity(id,kind.name,columns,rows,color,BackupCodec.encodeConfig(config),position)
internal fun HistoryEntity.model()=LaunchStats(count,searchCount,lastOpened)
internal fun WebEntity.model()=WebVisit(url,title,count,lastOpened)
class LayoutRepository internal constructor(private val db: LauncherDatabase,scope: CoroutineScope,private val mutex: Mutex,private val ready: CompletableDeferred<Unit>) {
    private val dao=db.dao()
    private val tileState=MutableStateFlow<List<HomeTile>>(emptyList()); val tiles: StateFlow<List<HomeTile>> = tileState.asStateFlow()
    private val historyState=MutableStateFlow<Map<String,LaunchStats>>(emptyMap()); val history: StateFlow<Map<String,LaunchStats>> = historyState.asStateFlow()
    private val webState=MutableStateFlow<List<WebVisit>>(emptyList()); val webHistory: StateFlow<List<WebVisit>> = webState.asStateFlow()
    private val loadedState=MutableStateFlow(false); val loaded: StateFlow<Boolean> = loadedState.asStateFlow()
    init { scope.launch {
        try { ready.await() } catch(_: Exception) { return@launch }
        tileState.value=dao.tiles().map { it.model() }; historyState.value=dao.history().associate { it.appId to it.model() }; webState.value=dao.web().map { it.model() }; loadedState.value=true
        launch { dao.observeTiles().collect { tileState.value=it.map { t -> t.model() } } }
        launch { dao.observeHistory().collect { historyState.value=it.associate { h -> h.appId to h.model() } } }
        launch { dao.observeWeb().collect { webState.value=it.map { w -> w.model() } } }
    } }
    private suspend fun mutate(transform: (List<HomeTile>)->List<HomeTile>) { ready.await(); mutex.withLock { db.withTransaction { val next=transform(dao.tiles().map { it.model() }); BackupCodec.validateTiles(next); dao.clearTiles(); dao.putTiles(next.mapIndexed { i,t -> t.entity(i) }) } } }
    suspend fun add(tile: HomeTile)=mutate { current -> require(current.none { it.id==tile.id }) { "Tile already exists" }; current+tile }
    suspend fun update(tile: HomeTile)=mutate { current -> require(current.any { it.id==tile.id }) { "Tile no longer exists" }; current.map { if(it.id==tile.id) tile else it } }
    suspend fun remove(id: String)=mutate { it.filterNot { t -> t.id==id } }
    suspend fun reorder(ids: List<String>)=mutate { current -> require(ids.size==current.size && ids.toSet()==current.map { it.id }.toSet()) { "Reorder must include every tile once" }; val byId=current.associateBy { it.id }; ids.map { byId.getValue(it) } }
    suspend fun recordLaunch(id: String,fromSearch: Boolean=false) { ready.await(); require(id.isNotBlank() && id.length<=512); mutex.withLock { db.withTransaction {
        val h=dao.history(id) ?: HistoryEntity(id,0,0,0); dao.putHistory(h.copy(count=(h.count+1).coerceAtMost(1_000_000_000),searchCount=(h.searchCount+if(fromSearch) 1 else 0).coerceAtMost(1_000_000_000),lastOpened=System.currentTimeMillis()))
    } } }
    suspend fun recordWebsite(url: String,title: String="") { BackupCodec.validateUrl(url); require(title.length<=200); ready.await(); mutex.withLock { db.withTransaction { val h=dao.web(url); dao.putWeb(WebEntity(url,title,((h?.count ?: 0)+1).coerceAtMost(1_000_000_000),System.currentTimeMillis())); dao.pruneWeb() } } }
}
