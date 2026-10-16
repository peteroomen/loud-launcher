@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.peteroomen.loud.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.loud.*
import io.github.peteroomen.loud.data.AppEntry
import io.github.peteroomen.loud.data.AppShortcut
import io.github.peteroomen.loud.model.*
import io.github.peteroomen.loud.system.*
import io.github.peteroomen.loud.ui.theme.*
import io.github.peteroomen.loud.widget.WidgetProviderCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@Composable fun AppMenu(app: AppEntry,vm: LauncherViewModel,dismiss: ()->Unit) {
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var name by remember(app.id) {mutableStateOf(settings.appPreferences.displayName(app.id,app.label))}
    val shortcuts by produceState(emptyList<AppShortcut>(),app.id) {value=runCatching {vm.core.apps.shortcuts(app)}.getOrDefault(emptyList())}
    AlertDialog(onDismissRequest=dismiss,title={Text(settings.appPreferences.displayName(app.id,app.label))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it},label={Text("Display name")},singleLine=true)
        TextButton({scope.launch {runCatching {vm.core.settings.renameApp(app.id,name.takeIf {it.isNotBlank() && it!=app.label})}.onFailure {vm.report("Name could not save")};dismiss()}}) {Text("Save name")}
        TextButton({vm.add(HomeTile(kind=HomeTileKind.App,columns=1,config=mapOf("appId" to app.id)));dismiss()}) {Text("Pin to LIVE")}
        TextButton({scope.launch {runCatching {vm.core.settings.hideApp(app.id,app.id !in settings.appPreferences.hiddenIds)}.onFailure {vm.report("Visibility could not save")};dismiss()}}) {Text(if(app.id in settings.appPreferences.hiddenIds) "Show in drawer" else "Hide from drawer")}
        Text("Hidden apps appear when a search has at least two characters.",fontSize=12.sp)
        TextButton({vm.core.apps.openAppInfo(app).onFailure {vm.report("App info unavailable")};dismiss()}) {Text("App info")}
        TextButton({vm.core.apps.uninstall(app).onFailure {vm.report("Uninstall unavailable")};dismiss()}) {Text("Uninstall")}
        if(shortcuts.isNotEmpty()) Text("SHORTCUTS",fontFamily=MonoFont,fontSize=11.sp)
        shortcuts.forEach {shortcut->TextButton({vm.core.apps.launchShortcut(app,shortcut.id).onFailure {vm.report("Shortcut unavailable")};dismiss()}) {Text(shortcut.label)}}
    }},confirmButton={TextButton(dismiss) {Text("Close")}})
}

@Composable fun AddTileDialog(vm: LauncherViewModel,dismiss: ()->Unit,configure: (HomeTile)->Unit) {
    var widgets by remember {mutableStateOf(false)};val scope=rememberCoroutineScope()
    if(widgets) {WidgetPicker(vm,dismiss);return}
    AlertDialog(onDismissRequest=dismiss,title={Text("Add to LIVE")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        HomeTileKind.entries.forEach {kind->TextButton({
            if(kind==HomeTileKind.Widget) widgets=true else {
                val width=if(kind==HomeTileKind.App || kind==HomeTileKind.Battery) 1 else if(kind in listOf(HomeTileKind.Weather,HomeTileKind.Note,HomeTileKind.Habit,HomeTileKind.Focus)) 2 else 4
                val tile=HomeTile(kind=kind,columns=width,rows=if(kind in listOf(HomeTileKind.Calendar,HomeTileKind.Music)) 2 else 1)
                scope.launch {runCatching {vm.core.layout.add(tile)}.onSuccess {configure(tile)}.onFailure {vm.report(it.message ?: "Tile could not add")}}
            }
        },Modifier.fillMaxWidth()) {Text(kind.name,modifier=Modifier.fillMaxWidth())}}
    }},confirmButton={TextButton(dismiss) {Text("Close")}})
}

@Composable private fun WidgetPicker(vm: LauncherViewModel,dismiss: ()->Unit) {
    val host=LocalWidgetHost.current
    val catalog by produceState<WidgetProviderCatalog?>(null,host) {value=withContext(Dispatchers.IO) {host?.installedProviders()}}
    AlertDialog(onDismissRequest=dismiss,title={Text("Android widgets")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        if(catalog==null) Text("Finding widget providers…")
        catalog?.message?.let {Text(it,fontSize=14.sp)}
        catalog?.providers?.forEach {provider->TextButton({host?.beginAdd(provider,maxOf(280,provider.minWidthDp),maxOf(180,provider.minHeightDp));dismiss()},Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth()) {Text(provider.label,fontWeight=FontWeight.Bold);Text("${provider.appLabel} · ${provider.profileLabel}",fontSize=12.sp);Text("Minimum ${provider.minWidthDp} × ${provider.minHeightDp} dp",fontSize=11.sp)}}}
        TextButton({host?.cancelPending();vm.report("Unfinished widget addition cancelled");dismiss()},enabled=host?.hasUnfinishedSetup()==true) {Text("Cancel unfinished addition")}
    }},confirmButton={TextButton(dismiss) {Text("Close")}})
}

@Composable fun TileConfigDialog(tile: HomeTile,vm: LauncherViewModel,dismiss: ()->Unit) {
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle();val apps by vm.core.apps.apps.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val host=LocalWidgetHost.current;val p=LocalLoud.current.palette
    val screenWidth=LocalConfiguration.current.screenWidthDp
    val minimumWidth=if(tile.kind==HomeTileKind.Widget) ceil((tile.config["minWidthDp"]?.toIntOrNull() ?: 1).toDouble()/((screenWidth-40).coerceAtLeast(1)/settings.gridColumns)).toInt().coerceIn(1,settings.gridColumns) else if(tile.kind==HomeTileKind.App || tile.kind==HomeTileKind.Battery) 1 else 2
    val minimumRows=if(tile.kind==HomeTileKind.Widget) ceil((tile.config["minHeightDp"]?.toIntOrNull() ?: 1)/94.0).toInt().coerceIn(1,4) else 1
    var columns by remember(tile.id) {mutableIntStateOf(tile.columns.coerceIn(minimumWidth,settings.gridColumns))}
    var rows by remember(tile.id) {mutableIntStateOf(tile.rows.coerceAtLeast(minimumRows))}
    var color by remember(tile.id) {mutableStateOf(tile.color)}
    var title by remember(tile.id) {mutableStateOf(tile.config["title"].orEmpty())}
    var text by remember(tile.id) {mutableStateOf(tile.config[if(tile.kind==HomeTileKind.Dev) "urls" else if(tile.kind==HomeTileKind.Web) "url" else if(tile.kind==HomeTileKind.Focus) "minutes" else "text"].orEmpty())}
    var appId by remember(tile.id) {mutableStateOf(tile.config["appId"].orEmpty())}
    var placeQuery by remember(tile.id) {mutableStateOf("")};var places by remember(tile.id) {mutableStateOf(emptyList<WeatherPlace>())}
    var placeError by remember(tile.id) {mutableStateOf<String?>(null)};var finding by remember(tile.id) {mutableStateOf(false)};var selectedPlace by remember(tile.id) {mutableStateOf<WeatherPlace?>(null)}
    AlertDialog(onDismissRequest=dismiss,title={Text("${tile.kind.name} tile")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Width: $columns columns",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {(minimumWidth..settings.gridColumns).forEach {n->BrutalButton("$n",Modifier.weight(1f),columns==n) {columns=n}}}
        Text("Height: $rows rows",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {(minimumRows..4).forEach {n->BrutalButton("$n",Modifier.weight(1f),rows==n) {rows=n}}}
        if(tile.kind==HomeTileKind.Widget) Text("Android widget minimum: ${tile.config["minWidthDp"] ?: "1"} × ${tile.config["minHeightDp"] ?: "1"} dp. Provider colours are kept.",fontSize=12.sp)
        Text("Tile colour",fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf<Long?>(null,p.yellow,p.cyan,p.pink,p.paper).forEachIndexed {index,fill->Box(Modifier.size(48.dp).background(Color(fill ?: p.canvas)).semantics {contentDescription=listOf("Automatic colour","Palette accent one","Palette accent two","Palette accent three","Paper colour")[index]}.clickable {color=fill},contentAlignment=Alignment.Center) {if(color==fill) Icon(Icons.Default.Check,"Selected colour",tint=Color(if(fill==null) p.onCanvas else p.ink)) else if(index==0) Text("Auto",fontSize=11.sp)}}}
        if(tile.kind in listOf(HomeTileKind.Note,HomeTileKind.Web,HomeTileKind.Dev,HomeTileKind.Habit,HomeTileKind.Widget)) OutlinedTextField(title,{title=it},label={Text("Title (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        when(tile.kind) {
            HomeTileKind.Note,HomeTileKind.Habit -> OutlinedTextField(text,{text=it},label={Text(if(tile.kind==HomeTileKind.Note) "Your note" else "Habit")},modifier=Modifier.fillMaxWidth(),minLines=if(tile.kind==HomeTileKind.Note) 3 else 1)
            HomeTileKind.Web,HomeTileKind.Dev -> OutlinedTextField(text,{text=it},label={Text(if(tile.kind==HomeTileKind.Web) "Website URL" else "Extra website URLs, one per line")},modifier=Modifier.fillMaxWidth(),minLines=if(tile.kind==HomeTileKind.Dev) 3 else 1)
            HomeTileKind.Focus -> OutlinedTextField(text,{text=it.filter(Char::isDigit)},label={Text("Session length, 1–180 minutes")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            HomeTileKind.App -> apps.filter {it.id !in settings.appPreferences.hiddenIds}.sortedBy {settings.appPreferences.displayName(it.id,it.label)}.forEach {app->TextButton({appId=app.id},Modifier.fillMaxWidth()) {if(appId==app.id) Icon(Icons.Default.Check,null);Text(settings.appPreferences.displayName(app.id,app.label))}}
            HomeTileKind.Weather -> {
                val weather by vm.system.weather.collectAsStateWithLifecycle();Text("Current: ${selectedPlace?.name ?: weather.place.name}")
                OutlinedTextField(placeQuery,{placeQuery=it},label={Text("City or town")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                TextButton({scope.launch {finding=true;placeError=null;runCatching {vm.system.searchWeatherPlaces(placeQuery)}.onSuccess {places=it;if(it.isEmpty()) placeError="No matching places"}.onFailure {placeError=it.message ?: "Place search failed"};finding=false}},enabled=placeQuery.isNotBlank() && !finding) {Text(if(finding) "Searching…" else "Find place")}
                placeError?.let {Text(it,fontSize=13.sp)}
                places.forEach {place->TextButton({selectedPlace=place}) {Text("${place.name}, ${place.country}");if(selectedPlace==place) Icon(Icons.Default.Check,null)}}
                Text("Weather place is shared by all weather tiles.",fontSize=12.sp)
                TextButton({vm.navigate(LauncherPage.Permissions);dismiss()}) {Text("Optional coarse location setup")}
            }
            HomeTileKind.Widget -> tile.config["widgetId"]?.toIntOrNull()?.let {id->TextButton({host?.reconfigure(id);dismiss()}) {Text("Provider settings")}}
            else -> Text("Live data updates automatically. Access controls are in Permissions.",fontSize=13.sp)
        }
        TextButton({scope.launch {runCatching {vm.core.layout.remove(tile.id);if(tile.kind==HomeTileKind.Widget) tile.config["widgetId"]?.toIntOrNull()?.let {host?.deleteWidget(it)}}.onFailure {vm.report("Tile could not remove")};dismiss()}}) {Icon(Icons.Default.Delete,null);Text("Remove tile")}
    }},confirmButton={TextButton({
        var config=tile.config;if(title.isNotBlank()) config=config+("title" to title) else config=config-"title"
        when(tile.kind) {
            HomeTileKind.Note,HomeTileKind.Habit -> config=config+("text" to text)
            HomeTileKind.Web,HomeTileKind.Dev -> {val urls=text.lines().filter {it.isNotBlank()};if(urls.any {runCatching {val uri=java.net.URI(it);uri.scheme !in listOf("http","https") || uri.host.isNullOrBlank()}.getOrDefault(true)}) {vm.report("Use complete http:// or https:// website URLs");return@TextButton};config=config+((if(tile.kind==HomeTileKind.Dev) "urls" else "url") to text)}
            HomeTileKind.Focus -> config=(config-"deadline"-"remainingSeconds")+("minutes" to (text.toIntOrNull() ?: 25).coerceIn(1,180).toString())
            HomeTileKind.App -> config=config+("appId" to appId)
            HomeTileKind.Weather -> selectedPlace?.let {vm.system.selectWeatherPlace(it)}
            else -> Unit
        }
        vm.save(tile.copy(columns=columns,rows=rows,color=color,config=config));dismiss()
    }) {Text("Save")}},dismissButton={TextButton(dismiss) {Text("Cancel")}})
}

@Composable fun SettingsCanvas(vm: LauncherViewModel,wallpaper: ()->Unit,export: ()->Unit,import: ()->Unit) {
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle();val scope=rememberCoroutineScope();val host=LocalWidgetHost.current
    var reset by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("MAKE IT YOURS",fontFamily=LocalLoud.current.heading,fontWeight=FontWeight.Bold,fontSize=30.sp)
        Text("Appearance",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {AppearanceShape.entries.forEach {shape->BrutalButton(shape.name,Modifier.weight(1f),settings.shape==shape) {vm.settings {it.copy(shape=shape)}}}}
        Text("Intensity",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {AppearanceIntensity.entries.forEach {level->BrutalButton(level.name,Modifier.weight(1f),settings.intensity==level) {vm.settings {it.copy(intensity=level)}}}}
        LoudPalettes.all.forEach {p->BrutalSurface(Modifier.fillMaxWidth(),Color(p.paper),onClick={vm.settings {it.copy(palette=p.name)}}) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text(p.name,modifier=Modifier.weight(1f));listOf(p.yellow,p.cyan,p.pink).forEach {fill->Box(Modifier.size(24.dp).background(Color(fill)))};if(settings.palette==p.name) Icon(Icons.Default.Check,"Selected palette")}}}
        Text("Grid columns",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {(2..4).forEach {n->BrutalButton("$n",Modifier.weight(1f),settings.gridColumns==n) {vm.settings {it.copy(gridColumns=n)}}}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Haptics",modifier=Modifier.weight(1f));Switch(settings.haptics,{value->vm.settings {it.copy(haptics=value)}})}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Shadow decorations",modifier=Modifier.weight(1f));Switch(settings.decorations,{value->vm.settings {it.copy(decorations=value)}})}
        BrutalButton("Choose wallpaper",Modifier.fillMaxWidth(),onClick=wallpaper)
        if(settings.wallpaperUri!=null) TextButton({vm.settings {it.copy(wallpaperUri=null)}}) {Text("Use theme canvas")}
        Text("Long press a tile to change its size, colour and content.",fontSize=13.sp)
        BrutalButton("Edit layout",Modifier.fillMaxWidth()) {vm.navigate(LauncherPage.Live);vm.edit.value=true}
        BrutalButton("Permissions and Power Bubble",Modifier.fillMaxWidth()) {vm.navigate(LauncherPage.Permissions)}
        BrutalButton("Export layout JSON",Modifier.fillMaxWidth(),onClick=export)
        BrutalButton("Import layout JSON",Modifier.fillMaxWidth(),onClick=import)
        BrutalButton("Reset layout and settings",Modifier.fillMaxWidth()) {reset=true}
        Text("Swipe up from the bottom buttons to search. Swipe down on the header for notifications. Double tap empty canvas to lock with Power Bubble.",fontSize=13.sp)
    }
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Reset Loud?")},text={Text("This clears your saved layout, settings, renamed and hidden apps, and launch history. Your weather place is kept.")},confirmButton={TextButton({scope.launch {runCatching {vm.core.reset();host?.reconcileWidgets(vm.core.layout.persistedTiles().mapNotNull {it.config["widgetId"]?.toIntOrNull()}.toSet())}.onSuccess {vm.home()}.onFailure {vm.report("Reset failed")}};reset=false}) {Text("Reset")}},dismissButton={TextButton({reset=false}) {Text("Cancel")}})
}

@Composable fun PermissionCanvas(vm: LauncherViewModel,request: (String)->Unit) {
    val context=LocalContext.current;val permissions by vm.system.permissions.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("ACCESS, EXPLAINED",fontFamily=LocalLoud.current.heading,fontSize=30.sp,fontWeight=FontWeight.Bold)
        PermissionRow("Home launcher","Choose Loud as your default home app.",false,"Choose default") {if(!SystemActions.start(context,Intent(Settings.ACTION_HOME_SETTINGS))) vm.report("Home settings unavailable")}
        PermissionRow("Calendar","Show events from your device's synced calendars, including Google Calendar.",permissions.calendar) {if(permissions.calendar) SystemActions.openAppSettings(context) else request(Manifest.permission.READ_CALENDAR)}
        PermissionRow("Notification and music access","Count posted notifications and control active media. Counts are not guaranteed unread messages.",permissions.notificationAccess) {if(!SystemActions.openNotificationSettings(context)) vm.report("Notification settings unavailable")}
        PermissionRow("Usage access","Use device app frequency. Loud's own launch history works without this permission.",permissions.usageAccess) {if(!SystemActions.openUsageSettings(context)) vm.report("Usage settings unavailable")}
        PermissionRow("Coarse location (optional)","Choose a city manually or use an available approximate location for weather.",permissions.coarseLocation,if(permissions.coarseLocation) "Use approximate location" else "Allow approximate location") {if(permissions.coarseLocation) {if(!vm.system.useCoarseLocation()) vm.report("No coarse location fix available. Choose a city manually.")} else request(Manifest.permission.ACCESS_COARSE_LOCATION)}
        PermissionRow("Power Bubble","Provides screen lock and notification shade gestures after its own setup.",permissions.powerBubbleAvailable,"Open Power Bubble",enabledDescription="Available") {if(!SystemActions.openApp(context,"io.github.peteroomen.powerbubble")) vm.report("Power Bubble is not installed")}
        TextButton({if(!SystemActions.openAppSettings(context)) vm.report("App settings unavailable")}) {Text("Loud app settings")}
    }
}
@Composable private fun PermissionRow(title: String,copy: String,granted: Boolean,label: String="Set up",enabledDescription: String="Enabled",action: ()->Unit) {BrutalSurface(Modifier.fillMaxWidth()) {Row(verticalAlignment=Alignment.CenterVertically) {Text(title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(granted) Icon(Icons.Default.Check,enabledDescription)};Text(copy,fontSize=13.sp);TextButton(action) {Text(if(granted && label=="Set up") "Manage" else label)}}}
