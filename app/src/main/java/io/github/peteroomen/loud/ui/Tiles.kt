package io.github.peteroomen.loud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.loud.ui.theme.*

enum class TileKind(val title: String, val sizes: List<TileSize>) {
    Clock("Clock", listOf(TileSize(2,1),TileSize(4,1),TileSize(4,2))),
    Weather("Weather", listOf(TileSize(2,1),TileSize(2,2),TileSize(4,2))),
    Calendar("Calendar", listOf(TileSize(2,2),TileSize(4,2),TileSize(4,3))),
    Music("Music", listOf(TileSize(2,2),TileSize(4,1),TileSize(4,2))),
    Frequent("Frequent apps", listOf(TileSize(2,2),TileSize(4,1),TileSize(4,2))),
    Web("Web", listOf(TileSize(2,1),TileSize(4,1),TileSize(4,2))),
    Dev("Dev desk", listOf(TileSize(2,2),TileSize(4,1),TileSize(4,2))),
    App("App", listOf(TileSize(1,1),TileSize(2,1))),
    Notifications("Notifications", listOf(TileSize(2,1),TileSize(4,1),TileSize(4,2))),
    Note("Note", listOf(TileSize(2,1),TileSize(2,2),TileSize(4,2))),
    Battery("Battery", listOf(TileSize(1,1),TileSize(2,1))),
    Focus("Focus timer", listOf(TileSize(2,1),TileSize(2,2))),
    Habit("Habit", listOf(TileSize(2,1),TileSize(2,2))),
}
data class TileSize(val columns: Int, val rows: Int) { override fun toString() = "$columns × $rows" }
enum class TileState { Content, Loading, Empty, Error, Permission }

@Composable
private fun Eyebrow(label: String) {
    Text(label.uppercase(), fontFamily = MonoFont, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
}
@Composable
private fun Small(text: String) { Text(text, fontSize = 12.sp) }
@Composable
private fun Heading(text: String) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable
private fun Control(icon: ImageVector, description: String, onClick: () -> Unit) {
    val s = LocalLoud.current
    IconButton(onClick, Modifier.size(48.dp).border(2.dp, Color(s.palette.ink), s.corners)) { Icon(icon, description) }
}

/** Minimum row height, never a fixed height: grows at larger font scales. Sample data throughout. */
@Composable
fun LoudTile(kind: TileKind, size: TileSize, modifier: Modifier = Modifier, state: TileState = TileState.Content, fillOverride: Long? = null, onSampleAction: (String) -> Unit = {}) {
    val s = LocalLoud.current
    val p = s.palette
    val compact = size.rows == 1
    val tiny = size.columns == 1
    val wide = size.columns == 4
    val fill = fillOverride ?: when(kind) {
        TileKind.Clock -> if(s.shape == LoudShape.Square) p.yellow else p.paper
        TileKind.Weather,TileKind.Notifications -> p.cyan
        TileKind.Note -> p.pink
        TileKind.Dev,TileKind.Focus -> p.yellow
        else -> p.paper
    }
    BrutalSurface(modifier.heightIn(min = (size.rows * 72).dp).semantics { contentDescription = "${kind.title} tile, sample data, $size" }, Color(fill)) {
        if (state != TileState.Content) {
            Eyebrow(kind.title)
            Text(when(state) {
                TileState.Loading -> "Loading…"
                TileState.Empty -> "Nothing here yet."
                TileState.Error -> "Saved data · Offline"
                TileState.Permission -> "Access needed"
                else -> ""
            }, fontSize = 14.sp)
            if (state == TileState.Permission) TextButton(onClick = { onSampleAction("Permission setup will be wired after design review.") }) { Text("Set up") }
            return@BrutalSurface
        }
        when(kind) {
            TileKind.Clock -> {
                if (!compact) Tag("LIVE / HAMILTON")
                Text("08:53", fontFamily = s.heading, fontWeight = FontWeight.Bold, fontSize = if(wide && !compact) 76.sp else 40.sp)
                Text(if(wide) "TUESDAY, 13 OCTOBER" else "TUE, 13 OCT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if(size.rows > 1) { Spacer(Modifier.height(12.dp)); Small("MAKE SOME NOISE.        ALARM 07:00") }
            }
            TileKind.Weather -> {
                Eyebrow("Weather")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("18°", fontSize = if(compact) 28.sp else 44.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.WbSunny, "Mostly sunny")
                }
                Small(if(compact) "Hamilton" else "Mostly sunny\nH 20° · L 11°")
                if(wide) { Spacer(Modifier.height(12.dp)); Small("10 AM 18°    1 PM 20°    4 PM 19°\nUpdated 08:30 · Sample forecast") }
            }
            TileKind.Note -> {
                Eyebrow("Note")
                Text("Make something worth opening.", fontSize = if(compact) 14.sp else 18.sp, fontWeight = FontWeight.Medium)
                if(!compact) { Spacer(Modifier.height(12.dp)); Small("One good thing today.") }
            }
            TileKind.Music -> {
                var playing by remember { mutableStateOf(true) }
                Eyebrow("Now playing / Spotify")
                if(!compact) Heading("Everything In Its Right Place") else Small("Everything In Its Right Place")
                Small("Radiohead · Kid A")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if(wide) Control(Icons.Default.SkipPrevious,"Previous track") { onSampleAction("Previous track · preview") }
                    Control(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(playing) "Pause" else "Play") { playing = !playing }
                    if(wide) Control(Icons.Default.SkipNext,"Next track") { onSampleAction("Next track · preview") }
                }
                if(!compact) { Spacer(Modifier.height(14.dp)); LinearProgressIndicator(progress = { .37f }, modifier = Modifier.fillMaxWidth(), color = Color(p.ink), trackColor = Color(p.ink).copy(alpha = .15f)); Small("1:32 / 4:11") }
            }
            TileKind.Calendar -> {
                Eyebrow("Calendar / Oct 2026")
                if(wide && size.rows != 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("M" to 12,"T" to 13,"W" to 14,"T" to 15,"F" to 16,"S" to 17,"S" to 18).forEach { (day,num) ->
                            Column(Modifier.background(if(num == 13) Color(p.yellow) else Color.Transparent).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Small(day); Text("$num", fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Heading("10:30")
                Small("Design catch-up\n30 min · Teams meeting")
                if(size.rows == 3) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) { listOf("M","T","W","T","F","S","S").forEach { day -> Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { Small(day) } } }
                    (listOf<Int?>(null,null,null)+(1..31).map { it }).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) { (week+List(7-week.size){null}).forEach { day ->
                            Box(Modifier.weight(1f).heightIn(min=34.dp).background(if(day == 13) Color(p.yellow) else Color.Transparent),contentAlignment=Alignment.Center) { Text(day?.toString() ?: "",fontSize=13.sp) }
                        } }
                    }
                }
            }
            TileKind.Notifications -> {
                Eyebrow("Teams / 2 notifications")
                if(!compact) { Heading("Project chat"); Small("“Ready when you are.”") }
                else Small("Project chat")
            }
            TileKind.App -> {
                Text("GH", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Small("GitHub")
            }
            TileKind.Battery -> { Eyebrow("Battery"); Heading("87%"); if(!tiny) Small("Not charging") }
            TileKind.Focus -> {
                var running by remember { mutableStateOf(false) }
                Eyebrow("Focus"); Heading("25:00")
                TextButton(onClick = { running = !running }) { Text(if(running) "Pause preview" else "Start preview") }
            }
            TileKind.Habit -> {
                var checked by remember { mutableStateOf(false) }
                Eyebrow("One good thing")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { checked = it }); Small("Go outside") }
            }
            TileKind.Web -> {
                Eyebrow("Web")
                OutlinedButton(onClick = { onSampleAction("Chrome search · sample action") }) { Icon(Icons.Default.Search, null); Text("Search web") }
                if(!compact) Small("GitHub · Vercel · MDN\nPinned sites and launcher history only")
            }
            TileKind.Dev,TileKind.Frequent -> {
                Eyebrow(kind.title)
                val names = if(kind == TileKind.Dev) listOf("Claude","ChatGPT","GitHub","Vercel") else listOf("Chrome","Teams","Spotify","Camera")
                names.chunked(if(wide) 4 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { name ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedButton(onClick = { onSampleAction("$name would open on your phone.") }, contentPadding = PaddingValues(4.dp), modifier = Modifier.heightIn(min=48.dp)) { Text(name.take(2), fontWeight=FontWeight.Bold) }
                            Text(name, fontSize=10.sp)
                        }
                    } }
                }
            }
        }
    }
}
