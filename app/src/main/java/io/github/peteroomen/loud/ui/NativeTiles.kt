@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.peteroomen.loud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.peteroomen.loud.*
import io.github.peteroomen.loud.data.AppEntry
import io.github.peteroomen.loud.model.*
import io.github.peteroomen.loud.system.*
import io.github.peteroomen.loud.ui.theme.*
import io.github.peteroomen.loud.widget.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

val LocalWidgetHost=staticCompositionLocalOf<WidgetHostController?> {null}

@Composable fun NativeTile(tile: HomeTile,vm: LauncherViewModel,configure: ()->Unit) {
    val style=LocalLoud.current;val p=style.palette;val context=LocalContext.current
    val apps by vm.core.apps.apps.collectAsStateWithLifecycle()
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle()
    val fill=tile.color ?: when(tile.kind) {HomeTileKind.Clock,HomeTileKind.Dev,HomeTileKind.Focus->p.yellow;HomeTileKind.Weather,HomeTileKind.Notifications->p.cyan;HomeTileKind.Note->p.pink;else->p.paper}
    BrutalSurface(Modifier.fillMaxWidth().heightIn(min=(tile.rows*80).dp),Color(fill)) {
        if(tile.kind!=HomeTileKind.Clock) Text(tile.config["title"]?.takeIf {it.isNotBlank()} ?: tile.kind.name.uppercase(),fontFamily=MonoFont,fontSize=11.sp,modifier=Modifier.padding(bottom=8.dp))
        when(tile.kind) {
            HomeTileKind.Clock -> {
                val clock by vm.system.clock.collectAsStateWithLifecycle();val now=clock.now.atZone(clock.zone)
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontFamily=style.heading,fontSize=if(tile.columns>=3) 62.sp else 36.sp,fontWeight=FontWeight.Bold)
                Text(now.format(DateTimeFormatter.ofPattern(if(tile.columns>=3) "EEEE, d MMMM" else "EEE d MMM")),fontSize=14.sp,fontWeight=FontWeight.Bold)
                if(tile.rows>1) {clock.nextAlarm?.let {Text("Alarm ${it.atZone(clock.zone).format(DateTimeFormatter.ofPattern("EEE HH:mm"))}",fontSize=13.sp)};TextButton({if(!SystemActions.openAlarms(context)) vm.report("No clock app available")}) {Text("Alarms")}}
            }
            HomeTileKind.Weather -> {
                val weather by vm.system.weather.collectAsStateWithLifecycle()
                Text(weather.place.name,fontWeight=FontWeight.Bold,fontSize=16.sp)
                weather.reading?.let {reading -> Text("${reading.temperatureC.roundToInt()}°",fontSize=44.sp,fontWeight=FontWeight.Bold);Text(weatherDescription(reading.weatherCode),fontSize=14.sp);weather.lastUpdated?.let {Text("Updated ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))}",fontSize=11.sp)};if(weather.offline) Text("Saved forecast · Offline",fontSize=12.sp);weather.error?.let {Text(it,fontSize=12.sp)}} ?: Text(if(weather.loading) "Loading forecast…" else weather.error ?: "Choose your place to get a forecast",fontSize=14.sp)
                FlowRow {TextButton(configure) {Text("Place")};TextButton({vm.system.refreshWeather(true)}) {Text("Refresh")}}
            }
            HomeTileKind.Calendar -> {
                val calendar by vm.system.calendar.collectAsStateWithLifecycle()
                if(!calendar.permissionGranted) SetupState("Show your synced calendars","Allow calendar") {vm.navigate(LauncherPage.Permissions)} else {
                    Text(calendar.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontWeight=FontWeight.Bold,fontSize=18.sp)
                    FlowRow {IconButton({vm.system.setCalendarMonth(calendar.month.minusMonths(1))}) {Icon(Icons.Default.ChevronLeft,"Previous month")};TextButton({vm.system.setCalendarMonth(YearMonth.now())}) {Text("Today")};IconButton({vm.system.setCalendarMonth(calendar.month.plusMonths(1))}) {Icon(Icons.Default.ChevronRight,"Next month")}}
                    if(tile.rows>=3 && tile.columns>=3) CalendarMonth(calendar.month)
                    if(calendar.loading) Text("Loading events…",fontSize=14.sp)
                    calendar.error?.let {Text(it,fontSize=14.sp)}
                    val now=Instant.now();val agenda=calendar.events.filter {calendar.month!=YearMonth.now() || if(it.allDay) it.end.atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now()) else it.end.isAfter(now)}
                    if(agenda.isEmpty() && !calendar.loading && calendar.error==null) Text("No upcoming events in this month",fontSize=14.sp)
                    agenda.take(if(tile.rows>=3) 5 else 2).forEach {event -> TextButton({if(!SystemActions.openCalendarEvent(context,event)) vm.report("No calendar app available")},contentPadding=PaddingValues(vertical=4.dp)) {Column(Modifier.fillMaxWidth()) {Text(event.title.ifBlank {"Untitled event"},fontWeight=FontWeight.Bold);Text(if(event.allDay) "All day · ${event.start.atZone(ZoneOffset.UTC).toLocalDate()}" else event.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")),fontSize=12.sp)}}}
                    TextButton({if(!SystemActions.openCalendar(context)) vm.report("No calendar app available")}) {Text("Open calendar")}
                }
            }
            HomeTileKind.Music -> {
                val media by vm.system.media.collectAsStateWithLifecycle();val permissions by vm.system.permissions.collectAsStateWithLifecycle();val notifications by vm.system.notifications.collectAsStateWithLifecycle()
                if(!permissions.notificationAccess) SetupState("Music controls need notification access","Set up") {SystemActions.openNotificationSettings(context)}
                else if(!notifications.connected) Text("Connecting to notification access…",fontSize=14.sp)
                else if(media==null) Text("No active player. Start music or a podcast in your app.",fontSize=14.sp)
                else media?.let {track ->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {if(tile.rows>1 && tile.columns>=3) track.art?.let {Image(it.asImageBitmap(),null,Modifier.size(56.dp))};Column(Modifier.weight(1f)) {Text(track.title,fontSize=18.sp,fontWeight=FontWeight.Bold);track.artist?.let {Text(it,fontSize=14.sp)}}}
                    FlowRow {if(tile.columns>=3) IconButton({vm.system.skipPrevious()}) {Icon(Icons.Default.SkipPrevious,"Previous track")};IconButton({vm.system.togglePlayPause()}) {Icon(if(track.playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(track.playing) "Pause" else "Play")};if(tile.columns>=3) IconButton({vm.system.skipNext()}) {Icon(Icons.Default.SkipNext,"Next track")};IconButton({if(!vm.system.openPlayer() && !SystemActions.openApp(context,track.packageName)) vm.report("Player unavailable")}) {Icon(Icons.Default.OpenInNew,"Open player")}}
                    if(tile.rows>1 && track.durationMs>0) {val tick=foregroundSecond(track.playing);val position=remember(tick,track) {track.positionNow()};LinearProgressIndicator(progress={(position.toFloat()/track.durationMs).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("${duration(position)} / ${duration(track.durationMs)}",fontFamily=MonoFont,fontSize=11.sp)}
                }
            }
            HomeTileKind.Frequent -> {
                val history by vm.core.layout.history.collectAsStateWithLifecycle();val usage by vm.system.usage.collectAsStateWithLifecycle();val layout by vm.core.layout.tiles.collectAsStateWithLifecycle()
                val suggested=AppSearch.suggestions(apps.map {SearchApp(it.id,it.label,it.packageName,it.installedAt)},settings.appPreferences,history,layout.mapNotNull {it.config["appId"]},limit=8)
                val deviceApps=if(usage.permissionGranted) usage.entries.mapNotNull {entry->apps.firstOrNull {it.packageName==entry.packageName && it.id !in settings.appPreferences.hiddenIds}}.distinctBy {it.id} else emptyList()
                val frequent=if(deviceApps.isNotEmpty()) deviceApps else suggested.mapNotNull {hit->apps.firstOrNull {it.id==hit.id}}
                if(frequent.isEmpty()) Text("Apps you open here will appear here",fontSize=14.sp) else AppTileButtons(frequent.take(if(tile.rows>1) 8 else 4),vm,tile.columns)
                Text(if(deviceApps.isNotEmpty()) "Device use" else "Opened with Loud",fontSize=11.sp)
            }
            HomeTileKind.App -> {
                val app=apps.firstOrNull {it.id==tile.config["appId"]}
                if(app==null) SetupState("App unavailable","Choose app",configure) else TextButton({vm.launch(app)},contentPadding=PaddingValues(0.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {AppIcon(app,vm,Modifier.size(48.dp));Text(settings.appPreferences.displayName(app.id,app.label),fontSize=13.sp,fontWeight=FontWeight.Bold)}}
            }
            HomeTileKind.Notifications -> {
                val notifications by vm.system.notifications.collectAsStateWithLifecycle();val permissions by vm.system.permissions.collectAsStateWithLifecycle()
                if(!permissions.notificationAccess) SetupState("Notification access is off","Set up") {SystemActions.openNotificationSettings(context)}
                else if(!notifications.connected) {Text("Connecting to notification access…",fontSize=14.sp);TextButton({vm.system.refresh()}) {Text("Retry")}}
                else {Text("${notifications.total}",fontSize=36.sp,fontWeight=FontWeight.Bold);Text("Posted notifications",fontSize=13.sp);if(tile.rows>1) notifications.counts.entries.sortedByDescending {it.value}.take(5).forEach {(pkg,count)->Text("${apps.firstOrNull {it.packageName==pkg}?.label ?: pkg}: $count",fontSize=13.sp)};TextButton({if(!SystemActions.expandNotifications(context)) vm.report("Set up Power Bubble to open notification shade")}) {Text("Open shade")}}
            }
            HomeTileKind.Note -> {val note=tile.config["text"].orEmpty();if(note.isBlank()) SetupState("A space for your note","Write a note",configure) else {Text(note,fontSize=18.sp);TextButton(configure) {Text("Edit")}}}
            HomeTileKind.Web -> {
                val url=tile.config["url"].orEmpty();val history by vm.core.layout.webHistory.collectAsStateWithLifecycle()
                if(url.isBlank()) SetupState("Pin a website","Choose URL",configure) else TextButton({vm.core.openWebsite(url,tile.config["title"].orEmpty()).onFailure {vm.report("No browser available")}}) {Text(tile.config["title"]?.takeIf {it.isNotBlank()} ?: uriLabel(url),fontWeight=FontWeight.Bold)}
                if(tile.rows>1) {if(history.isEmpty()) Text("No websites opened from Loud yet",fontSize=12.sp);history.take(4).forEach {visit -> TextButton({vm.core.openWebsite(visit.url,visit.title).onFailure {vm.report("No browser available")}}) {Text(visit.title.ifBlank {uriLabel(visit.url)},fontSize=13.sp)}}}
                TextButton({vm.navigate(LauncherPage.Search)}) {Icon(Icons.Default.Search,null);Text("Search web")}
            }
            HomeTileKind.Dev -> {
                val services=listOf(DevService("Claude","com.anthropic.claude","https://claude.ai"),DevService("ChatGPT","com.openai.chatgpt","https://chatgpt.com"),DevService("GitHub","com.github.android","https://github.com"),DevService("Vercel",null,"https://vercel.com"))
                services.chunked(if(tile.columns>=3) 2 else 1).forEach {row -> Row(Modifier.fillMaxWidth()) {row.forEach {service -> TextButton({val app=apps.firstOrNull {it.packageName==service.pkg};if(app!=null) vm.launch(app) else vm.core.openWebsite(service.url,service.name).onFailure {vm.report("No browser available")}},Modifier.weight(1f)) {Text(service.name,fontSize=14.sp)}}} }
                val custom=tile.config["urls"].orEmpty().lines().filter {it.isNotBlank() && it !in services.map {s->s.url}}
                if(tile.rows>1) custom.take(3).forEach {url -> TextButton({vm.core.openWebsite(url).onFailure {vm.report("No browser available")}}) {Text(uriLabel(url),fontSize=13.sp)}}
            }
            HomeTileKind.Battery -> {val battery by vm.system.battery.collectAsStateWithLifecycle();Text(battery.levelPercent?.let {"$it%"} ?: "Unknown",fontSize=if(tile.columns>1) 32.sp else 24.sp,fontWeight=FontWeight.Bold);if(battery.levelPercent!=null) Text(if(battery.charging) "Charging" else "On battery",fontSize=12.sp)}
            HomeTileKind.Focus -> FocusTile(tile,vm)
            HomeTileKind.Habit -> {val text=tile.config["text"].orEmpty();if(text.isBlank()) SetupState("Choose a habit to track","Set habit",configure) else {Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(tile.config["checked"]=="true",{checked->vm.save(tile.copy(config=tile.config+("checked" to checked.toString())))});Text(text,modifier=Modifier.weight(1f),fontSize=16.sp)};Text("Saved on this device",fontSize=11.sp)}}
            HomeTileKind.Widget -> {val id=tile.config["widgetId"]?.toIntOrNull();val host=LocalWidgetHost.current;if(id==null || host==null) SetupState("Widget unavailable","Choose another widget",configure) else HostedWidget(host,id,Modifier.fillMaxWidth().height(maxOf(tile.rows*94,tile.config["minHeightDp"]?.toIntOrNull() ?: 1).dp))}
        }
    }
}
private data class DevService(val name: String,val pkg: String?,val url: String)
@Composable private fun SetupState(message: String,label: String,action: ()->Unit) {Text(message,fontSize=14.sp);TextButton(action) {Text(label)}}
@Composable private fun AppTileButtons(apps: List<AppEntry>,vm: LauncherViewModel,columns: Int) {
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle()
    apps.chunked(if(columns>=3) 4 else if(columns>=2) 2 else 1).forEach {row->Row(Modifier.fillMaxWidth()) {row.forEach {app->TextButton({vm.launch(app)},Modifier.weight(1f),contentPadding=PaddingValues(2.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally) {AppIcon(app,vm,Modifier.size(36.dp));Text(settings.appPreferences.displayName(app.id,app.label),fontSize=11.sp,maxLines=2)}}}}}
}
@Composable private fun CalendarMonth(month: YearMonth) {
    val today=LocalDate.now();Row(Modifier.fillMaxWidth()) {listOf("M","T","W","T","F","S","S").forEach {Text(it,modifier=Modifier.weight(1f),fontSize=12.sp)}}
    val days=List<Int?>(month.atDay(1).dayOfWeek.value-1){null}+(1..month.lengthOfMonth()).map {it}
    days.chunked(7).forEach {week->Row(Modifier.fillMaxWidth()) {(week+List(7-week.size){null}).forEach {day->Text(day?.toString() ?: "",modifier=Modifier.weight(1f).padding(vertical=6.dp),fontSize=14.sp,fontWeight=if(day==today.dayOfMonth && month==YearMonth.from(today)) FontWeight.Bold else FontWeight.Normal)}}}
}
/** No ticking work remains active while the launcher is stopped. */
@Composable private fun foregroundSecond(active: Boolean): Long {
    val owner=LocalLifecycleOwner.current
    val tick by produceState(System.currentTimeMillis(),owner,active) {value=System.currentTimeMillis();if(active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {while(isActive) {value=System.currentTimeMillis();delay(1000)}}}
    return tick
}
@Composable private fun FocusTile(tile: HomeTile,vm: LauncherViewModel) {
    val minutes=tile.config["minutes"]?.toIntOrNull()?.coerceIn(1,180) ?: 25
    val deadline=tile.config["deadline"]?.toLongOrNull()?.takeIf {it>0}
    val tick=foregroundSecond(deadline!=null)
    val remaining=tile.config["remainingSeconds"]?.toLongOrNull() ?: minutes*60L
    val seconds=if(deadline!=null) ((deadline-tick+999)/1000).coerceAtLeast(0) else remaining
    Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",fontSize=32.sp,fontWeight=FontWeight.Bold)
    FlowRow {TextButton({val config=if(deadline==null) tile.config+mapOf("deadline" to (System.currentTimeMillis()+seconds*1000).toString()) else (tile.config-"deadline")+("remainingSeconds" to seconds.toString());vm.save(tile.copy(config=config))}) {Text(if(deadline==null) "Start" else "Pause")};TextButton({vm.save(tile.copy(config=tile.config-"deadline"-"remainingSeconds"))}) {Text("Reset")}}
    if(deadline!=null && seconds==0L) Text("Session complete",fontSize=13.sp)
}
private fun duration(ms: Long)="${ms/60000}:${((ms/1000)%60).toString().padStart(2,'0')}"
private fun uriLabel(url: String)=runCatching {java.net.URI(url).host?.removePrefix("www.") ?: url}.getOrDefault(url)
private fun weatherDescription(code: Int)=when(code) {0->"Clear sky";1,2->"Partly cloudy";3->"Overcast";45,48->"Fog";51,53,55->"Drizzle";61,63,65->"Rain";66,67->"Freezing rain";71,73,75,77->"Snow";80,81,82->"Rain showers";85,86->"Snow showers";95,96,99->"Thunderstorm";else->"Weather code $code"}
