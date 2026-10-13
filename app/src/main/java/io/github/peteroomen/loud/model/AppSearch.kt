package io.github.peteroomen.loud.model
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

object AppSearch {
    private fun normalized(value: String) = Normalizer.normalize(value,Normalizer.Form.NFD).replace("\\p{M}+".toRegex(),"").lowercase(Locale.ROOT).trim()
    fun rank(apps: List<SearchApp>, query: String, preferences: AppPreferences, history: Map<String,LaunchStats> = emptyMap(), now: Long = System.currentTimeMillis()): List<SearchApp> {
        if(query.length > 200) return emptyList()
        val q = normalized(query)
        return apps.asSequence().filter { it.id !in preferences.hiddenIds || q.length >= 2 }.mapNotNull { app ->
            val name = normalized(preferences.displayName(app.id,app.label))
            val match = if(q.isEmpty()) 0 else matchScore(name,q) ?: matchScore(normalized(app.packageName),q)?.minus(300)
            match?.let { Triple(app,name,it + if(q.isEmpty()) 0.0 else frecency(history[app.id],now).coerceAtMost(25.0)) }
        }.sortedWith(compareByDescending<Triple<SearchApp,String,Double>> { it.third }.thenBy { it.second }.thenBy { it.first.id }).map { it.first }.toList()
    }
    private fun matchScore(name: String,q: String): Int? {
        if(name==q) return 1000
        if(name.startsWith(q)) return 850-(name.length-q.length).coerceAtMost(80)
        val words=name.split("[^a-z0-9]+".toRegex()).filter { it.isNotBlank() }
        if(words.any { it.startsWith(q) }) return 720
        val parts=q.split(' ').filter { it.isNotBlank() }
        if(parts.size>1 && parts.all { p -> words.any { it.startsWith(p) } }) return 700
        if(name.contains(q)) return 600
        if(q.length>=2 && words.map { it.first() }.joinToString("").startsWith(q)) return 550
        if(q.length>=2) {
            var i=0; var gaps=0
            for(c in name) { if(i<q.length && c==q[i]) i++ else if(i>0 && i<q.length) gaps++ }
            if(i==q.length) return 400-gaps.coerceAtMost(100)
        }
        if(q.length>=3 && words.any { editDistance(it,q)<=if(q.length>=6) 2 else 1 }) return 250
        return null
    }
    internal fun editDistance(a: String,b: String): Int {
        var previous=IntArray(b.length+1) { it }
        for(i in a.indices) { val current=IntArray(b.length+1); current[0]=i+1
            for(j in b.indices) current[j+1]=minOf(current[j]+1,previous[j+1]+1,previous[j]+if(a[i]==b[j]) 0 else 1)
            previous=current
        }; return previous[b.length]
    }
    fun frecency(stats: LaunchStats?,now: Long): Double {
        if(stats==null || stats.count==0L) return 0.0
        val days=(now-stats.lastOpened).coerceAtLeast(0)/86_400_000.0
        return (ln(1.0+stats.count)*6+ln(1.0+stats.searchCount)*2)*exp(-days/14.0)
    }
    fun suggestions(apps: List<SearchApp>,preferences: AppPreferences,history: Map<String,LaunchStats>,pinnedIds: List<String> = emptyList(),now: Long = System.currentTimeMillis(),limit: Int = 8): List<SearchApp> {
        val visible=apps.filter { it.id !in preferences.hiddenIds }.associateBy { it.id }
        val result=LinkedHashMap<String,SearchApp>()
        fun add(id: String) { if(result.size<limit.coerceAtLeast(0)) visible[id]?.let { result[id]=it } }
        history.entries.filter { it.value.count>0 }.sortedWith(compareByDescending<Map.Entry<String,LaunchStats>> { frecency(it.value,now) }.thenBy { it.key }).forEach { add(it.key) }
        pinnedIds.forEach(::add)
        return result.values.toList()
    }
}
