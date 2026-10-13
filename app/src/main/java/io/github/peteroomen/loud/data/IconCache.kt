package io.github.peteroomen.loud.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class IconCache internal constructor(private val context: Context,private val apps: AppRepository) {
    val generation: kotlinx.coroutines.flow.StateFlow<Long> = apps.iconGeneration
    private val mutex=Mutex()
    private val cache=object: LruCache<String,Bitmap>(16*1024*1024) { override fun sizeOf(key: String,value: Bitmap)=value.byteCount }
    private fun key(id: String,size: Int)="${apps.iconGeneration.value}:$id@$size"
    fun cached(id: String,sizePx: Int): Bitmap?=cache.get(key(id,sizePx))
    fun clear() { cache.evictAll() }
    suspend fun load(app: AppEntry,sizePx: Int): Bitmap?=withContext(Dispatchers.IO) {
        require(sizePx in 1..512)
        mutex.withLock {
            val key=key(app.id,sizePx); cache.get(key)?.let { return@withLock it }
            val info=apps.activityInfo(app.id) ?: return@withLock null
            runCatching { val drawable=info.getBadgedIcon(context.resources.displayMetrics.densityDpi); val bitmap=Bitmap.createBitmap(sizePx,sizePx,Bitmap.Config.ARGB_8888); drawable.setBounds(0,0,sizePx,sizePx); drawable.draw(Canvas(bitmap)); cache.put(key,bitmap); bitmap }.getOrNull()
        }
    }
}
