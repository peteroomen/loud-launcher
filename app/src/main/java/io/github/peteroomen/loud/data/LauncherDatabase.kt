package io.github.peteroomen.loud.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName="tiles") internal data class TileEntity(@PrimaryKey val id: String,val kind: String,val columns: Int,val rows: Int,val color: Long?,val configJson: String,val position: Int)
@Entity(tableName="launch_history") internal data class HistoryEntity(@PrimaryKey val appId: String,val count: Long,val searchCount: Long,val lastOpened: Long)
@Entity(tableName="web_history") internal data class WebEntity(@PrimaryKey val url: String,val title: String,val count: Long,val lastOpened: Long)
@Entity(tableName="core_meta") internal data class MetaEntity(@PrimaryKey val key: String,val value: String)
@Dao internal interface LauncherDao {
    @Query("SELECT * FROM tiles ORDER BY position, id") fun observeTiles(): Flow<List<TileEntity>>
    @Query("SELECT * FROM launch_history") fun observeHistory(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM web_history ORDER BY lastOpened DESC") fun observeWeb(): Flow<List<WebEntity>>
    @Query("SELECT * FROM tiles ORDER BY position, id") suspend fun tiles(): List<TileEntity>
    @Query("SELECT * FROM launch_history") suspend fun history(): List<HistoryEntity>
    @Query("SELECT * FROM launch_history WHERE appId = :id") suspend fun history(id: String): HistoryEntity?
    @Query("SELECT * FROM web_history ORDER BY lastOpened DESC") suspend fun web(): List<WebEntity>
    @Query("SELECT * FROM web_history WHERE url = :url") suspend fun web(url: String): WebEntity?
    @Query("SELECT value FROM core_meta WHERE `key` = :key") suspend fun meta(key: String): String?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putMeta(value: MetaEntity)
    @Query("DELETE FROM core_meta WHERE `key` = :key") suspend fun deleteMeta(key: String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putTiles(values: List<TileEntity>)
    @Query("DELETE FROM tiles") suspend fun clearTiles()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putHistory(value: HistoryEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putHistory(values: List<HistoryEntity>)
    @Query("DELETE FROM launch_history") suspend fun clearHistory()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putWeb(value: WebEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putWeb(values: List<WebEntity>)
    @Query("DELETE FROM web_history") suspend fun clearWeb()
    @Query("DELETE FROM web_history WHERE url NOT IN (SELECT url FROM web_history ORDER BY lastOpened DESC LIMIT 200)") suspend fun pruneWeb()
}
@Database(entities=[TileEntity::class,HistoryEntity::class,WebEntity::class,MetaEntity::class],version=1,exportSchema=false)
internal abstract class LauncherDatabase: RoomDatabase() { abstract fun dao(): LauncherDao }
