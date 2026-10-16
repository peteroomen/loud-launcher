package io.github.peteroomen.loud.model
import java.util.UUID

enum class AppearanceShape { Square, Round }
enum class AppearanceIntensity { Calm, Standard, Loud }
data class AppPreferences(val hiddenIds: Set<String> = emptySet(), val renames: Map<String, String> = emptyMap()) {
    fun displayName(id: String, fallback: String) = renames[id] ?: fallback
}
data class LauncherSettings(val palette: String = "Taxi", val shape: AppearanceShape = AppearanceShape.Round,
    val gridColumns: Int = 4, val haptics: Boolean = true, val decorations: Boolean = true,
    val wallpaperUri: String? = null, val appPreferences: AppPreferences = AppPreferences(),
    val intensity: AppearanceIntensity = AppearanceIntensity.Standard)
enum class HomeTileKind { Clock, Weather, Calendar, Music, Frequent, Web, Dev, App, Notifications, Note, Battery, Focus, Habit, Widget }
data class HomeTile(val id: String = UUID.randomUUID().toString(), val kind: HomeTileKind,
    val columns: Int = 4, val rows: Int = 1, val color: Long? = null, val config: Map<String, String> = emptyMap())
data class LaunchStats(val count: Long = 0, val searchCount: Long = 0, val lastOpened: Long = 0)
data class SearchApp(val id: String, val label: String, val packageName: String, val installedAt: Long = 0)
data class WebVisit(val url: String, val title: String = "", val count: Long = 0, val lastOpened: Long = 0)
fun defaultTiles() = listOf(
    HomeTile("default-clock",HomeTileKind.Clock,rows=2),
    HomeTile("default-weather",HomeTileKind.Weather,columns=2,rows=2),
    HomeTile("default-note",HomeTileKind.Note,columns=2,rows=2,config=mapOf("text" to "")),
    HomeTile("default-music",HomeTileKind.Music,rows=2),
    HomeTile("default-calendar",HomeTileKind.Calendar,rows=2),
    HomeTile("default-frequent",HomeTileKind.Frequent),
    HomeTile("default-dev",HomeTileKind.Dev,config=mapOf("urls" to "https://claude.ai\nhttps://chatgpt.com\nhttps://github.com\nhttps://vercel.com")))
