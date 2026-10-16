package io.github.peteroomen.loud.data
import io.github.peteroomen.loud.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class LauncherBackup(val settings: LauncherSettings,val tiles: List<HomeTile>,val history: Map<String,LaunchStats>,val webHistory: List<WebVisit> = emptyList())
object BackupCodec {
    const val VERSION=1
    private val palettes=setOf("Taxi","Riso","Acid","Night Brutal","Candy","Pop Garden","Baby Blue","Rave")
    private val configKeys=setOf("appId","text","url","title","urls","city","latitude","longitude","minutes","checked","widgetId","provider","profileSerial","deadline","remainingSeconds","minWidthDp","minHeightDp")
    private fun validateId(id: String) { require(id.isNotBlank() && id.length<=512 && id.none { it.isISOControl() }) { "Invalid ID" } }
    fun validateSettings(s: LauncherSettings) {
        require(s.palette in palettes) { "Unknown palette" }; require(s.gridColumns in 2..4)
        s.wallpaperUri?.let { require(it.length<=4096 && URI(it).scheme=="content") { "Wallpaper must be a content URI" } }
        require(s.appPreferences.hiddenIds.size<=10_000 && s.appPreferences.renames.size<=10_000)
        s.appPreferences.hiddenIds.forEach(::validateId)
        s.appPreferences.renames.forEach { (id,name) -> validateId(id); require(name.isNotBlank() && name.length<=120) }
    }
    fun validateTiles(tiles: List<HomeTile>) {
        require(tiles.size<=300) { "Too many tiles" }; require(tiles.map { it.id }.distinct().size==tiles.size) { "Duplicate tile IDs" }
        tiles.forEach { t ->
            validateId(t.id); require(t.columns in 1..4 && t.rows in 1..4) { "Invalid tile size" }
            t.color?.let { require(it in 0..0xFFFFFFFFL) }; require(t.config.size<=20 && t.config.keys.all { it in configKeys }) { "Unknown tile configuration" }
            t.config.values.forEach { require(it.length<=16384) }
            t.config["url"]?.let(::validateUrl); t.config["urls"]?.lineSequence()?.filter { it.isNotBlank() }?.forEach(::validateUrl)
            listOf("minWidthDp","minHeightDp").forEach { key -> t.config[key]?.let { require((it.toIntOrNull() ?: -1) in 0..4096) } }
            t.config["widgetId"]?.let { require((it.toIntOrNull() ?: -1)>0) }
            t.config["profileSerial"]?.let { require((it.toLongOrNull() ?: -1)>=0) }
            t.config["minutes"]?.let { require((it.toIntOrNull() ?: -1) in 1..1440) }
            t.config["deadline"]?.let { require((it.toLongOrNull() ?: -1)>=0) }
            t.config["remainingSeconds"]?.let { require((it.toLongOrNull() ?: -1) in 0..86400) }
            t.config["latitude"]?.let { require(it.toDoubleOrNull()?.let { n -> n.isFinite() && n in -90.0..90.0 }==true) }
            t.config["longitude"]?.let { require(it.toDoubleOrNull()?.let { n -> n.isFinite() && n in -180.0..180.0 }==true) }
        }
    }
    fun validateUrl(value: String) {
        val uri=URI(value)
        require(value.length<=4096 && uri.scheme?.lowercase() in setOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo==null) { "Only public HTTP(S) links supported" }
        require(listOf(uri.rawQuery.orEmpty(),uri.rawFragment.orEmpty()).all { params -> params.split('&').none { URLDecoder.decode(it.substringBefore('='),"UTF-8").lowercase() in setOf("token","access_token","refresh_token","api_key","apikey","authorization") } }) { "Links must not contain authentication tokens" }
    }
    fun encodeSettings(s: LauncherSettings): String {
        validateSettings(s)
        return JSONObject().put("palette",s.palette).put("shape",s.shape.name).put("gridColumns",s.gridColumns).put("haptics",s.haptics).put("decorations",s.decorations).put("intensity",s.intensity.name).put("wallpaperUri",s.wallpaperUri ?: JSONObject.NULL).put("hiddenIds",JSONArray(s.appPreferences.hiddenIds.sorted())).put("renames",JSONObject(s.appPreferences.renames)).toString()
    }
    fun decodeSettings(json: String): LauncherSettings {
        val o=JSONObject(json); keys(o,setOf("palette","shape","gridColumns","haptics","decorations","intensity","wallpaperUri","hiddenIds","renames"))
        val h=o.getJSONArray("hiddenIds"); val r=o.getJSONObject("renames")
        require(o.get("haptics") is Boolean && o.get("decorations") is Boolean)
        return LauncherSettings(o.getString("palette"),AppearanceShape.valueOf(o.getString("shape")),int(o,"gridColumns"),o.getBoolean("haptics"),o.getBoolean("decorations"),if(o.isNull("wallpaperUri")) null else o.getString("wallpaperUri"),AppPreferences((0 until h.length()).map { h.getString(it) }.toSet(),r.keys().asSequence().associateWith { r.getString(it) }),if(o.has("intensity")) AppearanceIntensity.valueOf(o.getString("intensity")) else AppearanceIntensity.Standard).also(::validateSettings)
    }
    fun encodeConfig(c: Map<String,String>)=JSONObject(c).toString()
    fun decodeConfig(json: String)=JSONObject(json).let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
    private fun validateHistory(history: Map<String,LaunchStats>) {
        require(history.size<=10000); history.forEach { (id,h) -> validateId(id); require(h.count in 0..1_000_000_000 && h.searchCount in 0..h.count && h.lastOpened>=0) { "Invalid launch history" } }
    }
    private fun validateWeb(web: List<WebVisit>) { require(web.size<=200 && web.map { it.url }.distinct().size==web.size); web.forEach { validateUrl(it.url); require(it.title.length<=200 && it.count in 0..1_000_000_000 && it.lastOpened>=0) } }
    fun encode(b: LauncherBackup): String {
        validateSettings(b.settings); validateTiles(b.tiles); validateHistory(b.history); validateWeb(b.webHistory)
        val t=JSONArray(); b.tiles.forEach { t.put(JSONObject().put("id",it.id).put("kind",it.kind.name).put("columns",it.columns).put("rows",it.rows).put("color",it.color ?: JSONObject.NULL).put("config",JSONObject(it.config))) }
        val h=JSONObject(); b.history.toSortedMap().forEach { (id,s) -> h.put(id,JSONObject().put("count",s.count).put("searchCount",s.searchCount).put("lastOpened",s.lastOpened)) }
        val w=JSONArray(); b.webHistory.forEach { w.put(JSONObject().put("url",it.url).put("title",it.title).put("count",it.count).put("lastOpened",it.lastOpened)) }
        return JSONObject().put("version",VERSION).put("settings",JSONObject(encodeSettings(b.settings))).put("tiles",t).put("history",h).put("webHistory",w).toString(2)
    }
    fun decode(json: String): LauncherBackup {
        require(json.toByteArray(Charsets.UTF_8).size<=2_000_000) { "Backup exceeds 2MB" }
        validateJsonDepth(json)
        val o=JSONObject(json); keys(o,setOf("version","settings","tiles","history","webHistory")); require(int(o,"version")==VERSION) { "Unsupported backup version" }
        val s=decodeSettings(o.getJSONObject("settings").toString()); val ta=o.getJSONArray("tiles"); require(ta.length()<=300)
        val t=(0 until ta.length()).map { i -> val x=ta.getJSONObject(i); keys(x,setOf("id","kind","columns","rows","color","config")); HomeTile(x.getString("id"),HomeTileKind.valueOf(x.getString("kind")),int(x,"columns"),int(x,"rows"),if(x.isNull("color")) null else long(x,"color"),decodeConfig(x.getJSONObject("config").toString())) }; validateTiles(t)
        val ho=o.getJSONObject("history"); require(ho.length()<=10000)
        val h=ho.keys().asSequence().associateWith { id -> val x=ho.getJSONObject(id); keys(x,setOf("count","searchCount","lastOpened")); LaunchStats(long(x,"count"),long(x,"searchCount"),long(x,"lastOpened")) }; validateHistory(h)
        val wa=o.optJSONArray("webHistory") ?: JSONArray(); require(wa.length()<=200)
        val w=(0 until wa.length()).map { i -> val x=wa.getJSONObject(i); keys(x,setOf("url","title","count","lastOpened")); WebVisit(x.getString("url"),x.getString("title"),long(x,"count"),long(x,"lastOpened")) }; validateWeb(w)
        return LauncherBackup(s.copy(wallpaperUri=null),t.filter { it.kind!=HomeTileKind.Widget },h,w)
    }
    private fun validateJsonDepth(json: String) {
        var depth=0; var inString=false; var escaped=false
        for(c in json) {
            if(inString) {
                if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') inString=false
            } else when(c) {
                '"' -> inString=true
                '{','[' -> { depth++; require(depth<=16) { "Backup nesting exceeds limit" } }
                '}',']' -> { depth--; require(depth>=0) { "Malformed backup nesting" } }
            }
        }
        require(depth==0 && !inString) { "Malformed backup nesting" }
    }
    private fun keys(o: JSONObject,allowed: Set<String>) { require(o.keys().asSequence().all { it in allowed }) { "Unknown backup fields" } }
    private fun long(o: JSONObject,key: String): Long { val n=o.get(key); require(n is Int || n is Long) { "$key must be integer" }; return (n as Number).toLong() }
    private fun int(o: JSONObject,key: String)=long(o,key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
}
