@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.peteroomen.loud.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peteroomen.loud.*
import io.github.peteroomen.loud.data.AppEntry
import io.github.peteroomen.loud.model.*
import io.github.peteroomen.loud.system.*
import io.github.peteroomen.loud.ui.theme.*
import io.github.peteroomen.loud.widget.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

@Composable
fun LoudLauncherApp(vm: LauncherViewModel,widgets: WidgetHostController) {
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val generation by vm.homeGeneration.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val keyboard=LocalSoftwareKeyboardController.current
    val palette=LoudPalettes.all.firstOrNull {it.name==settings.palette} ?: LoudPalettes.Taxi
    val shape=if(settings.shape==AppearanceShape.Round) LoudShape.Round else LoudShape.Square
    val intensity=LoudIntensity.valueOf(settings.intensity.name)
    val snackbar=remember {SnackbarHostState()}
    suspend fun reconcileWidgets() {widgets.reconcileWidgets(vm.core.layout.persistedTiles().mapNotNull {it.config["widgetId"]?.toIntOrNull()}.toSet())}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {vm.system.refresh()}
    val wallpaper=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}.onSuccess {vm.settings {it.copy(wallpaperUri=uri.toString())}}.onFailure {vm.report("Wallpaper permission could not be kept. Choose another image.")}
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri -> if(uri!=null) scope.launch {
        runCatching {val json=vm.core.exportBackup();withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(json.toByteArray(Charsets.UTF_8))} ?: error("File could not open")}}.onSuccess {vm.report("Layout exported")}.onFailure {vm.report(it.message ?: "Export failed")}
    } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) scope.launch {
        runCatching {withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {input ->
            val bytes=ByteArrayOutputStream();val chunk=ByteArray(8192);var count=input.read(chunk)
            while(count!=-1) {require(bytes.size()+count<=2*1024*1024) {"Backup exceeds 2 MB"};bytes.write(chunk,0,count);count=input.read(chunk)}
            bytes.toString("UTF-8")
        } ?: error("File could not open")}}.mapCatching {vm.core.importBackup(it).getOrThrow()}.onSuccess {reconcileWidgets();vm.report("Layout imported. Choose wallpaper again if needed.")}.onFailure {vm.report(it.message ?: "Import failed")}
    } }
    LaunchedEffect(message) {message?.let {snackbar.showSnackbar(it);vm.message.value=null}}
    LaunchedEffect(generation) {keyboard?.hide()}
    SideEffect {(context as? Activity)?.window?.let {WindowCompat.getInsetsController(it,it.decorView).apply {isAppearanceLightStatusBars=!palette.dark;isAppearanceLightNavigationBars=!palette.dark}}}
    BackHandler(page!=LauncherPage.Live || edit) {vm.home()}
    LoudTheme(palette,shape,settings.haptics,settings.decorations,intensity) {
        CompositionLocalProvider(LocalWidgetHost provides widgets) {
            Box(Modifier.fillMaxSize().background(Color(palette.canvas))) {
                WallpaperImage(settings.wallpaperUri)
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).pointerInput(Unit) {
                        var distance=0f;detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance>45.dp.toPx() && !SystemActions.expandNotifications(context)) vm.report("Set up Power Bubble to open notification shade")}) {change,delta -> distance+=delta;if(delta>0) change.consume()}
                    },verticalAlignment=Alignment.CenterVertically) {
                        Text("LOUD✳",fontFamily=DisplayFont,fontWeight=FontWeight.Bold,fontSize=32.sp,color=Color(palette.onCanvas),modifier=Modifier.weight(1f))
                        IconButton({vm.page.value=LauncherPage.Live;vm.edit.value=!edit}) {Icon(Icons.Default.Edit,"Edit layout",tint=Color(palette.onCanvas))}
                        IconButton({vm.navigate(LauncherPage.Settings)}) {Icon(Icons.Default.Tune,"Settings",tint=Color(palette.onCanvas))}
                    }
                    Box(Modifier.weight(1f)) {when(page) {
                        LauncherPage.Live -> LiveCanvas(vm)
                        LauncherPage.Apps,LauncherPage.Search -> AppsCanvas(vm,page==LauncherPage.Search)
                        LauncherPage.Settings -> SettingsCanvas(vm,{wallpaper.launch(arrayOf("image/*"))},{export.launch("loud-layout.json")},{import.launch(arrayOf("application/json","text/plain"))})
                        LauncherPage.Permissions -> PermissionCanvas(vm) {permissions.launch(arrayOf(it))}
                    } }
                    Row(Modifier.fillMaxWidth().padding(12.dp).pointerInput(Unit) {
                        var distance=0f;detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance < -45.dp.toPx()) vm.navigate(LauncherPage.Search)}) {change,delta -> distance+=delta;if(delta<0) change.consume()}
                    },horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        BrutalButton("LIVE",Modifier.weight(1f),page==LauncherPage.Live) {vm.home()}
                        BrutalButton("APPS",Modifier.weight(1f),page==LauncherPage.Apps) {vm.navigate(LauncherPage.Apps)}
                        BrutalButton("SEARCH",Modifier.weight(1f),page==LauncherPage.Search) {vm.navigate(LauncherPage.Search)}
                    }
                }
                SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=80.dp))
            }
        }
    }
}

@Composable private fun WallpaperImage(uri: String?) {
    val context=LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null,uri) {value=if(uri==null) null else withContext(Dispatchers.IO) {runCatching {
        val source=Uri.parse(uri);val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(source)?.use {BitmapFactory.decodeStream(it,null,bounds)}
        val options=BitmapFactory.Options().apply {inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/1600).coerceAtLeast(1)}
        context.contentResolver.openInputStream(source)?.use {BitmapFactory.decodeStream(it,null,options)?.asImageBitmap()}
    }.getOrNull()} }
    bitmap?.let {Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Color(LocalLoud.current.palette.canvas).copy(alpha=.6f)))}
}

@Composable private fun LiveCanvas(vm: LauncherViewModel) {
    val tiles by vm.core.layout.tiles.collectAsStateWithLifecycle()
    val loaded by vm.core.layout.loaded.collectAsStateWithLifecycle()
    val failure by vm.core.initializationError.collectAsStateWithLifecycle()
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val generation by vm.homeGeneration.collectAsStateWithLifecycle()
    val state=rememberLazyGridState()
    var configured by remember {mutableStateOf<HomeTile?>(null)}
    var adding by remember {mutableStateOf(false)}
    val context=LocalContext.current
    LaunchedEffect(generation) {state.scrollToItem(0);configured=null;adding=false}
    BackHandler(configured!=null || adding) {configured=null;adding=false}
    Column {
        if(edit) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {Text("EDIT LAYOUT",fontFamily=MonoFont,modifier=Modifier.weight(1f));TextButton({adding=true}) {Icon(Icons.Default.Add,null);Text("Add")};TextButton({vm.edit.value=false}) {Text("Done")}}
        LazyVerticalGrid(columns=GridCells.Fixed(settings.gridColumns),state=state,contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxSize().pointerInput(edit) {
            detectTapGestures(onDoubleTap={if(!edit && !SystemActions.screenOff(context)) vm.report("Install and set up Power Bubble to lock with double tap")})
        }) {
            failure?.let {item(span={GridItemSpan(maxLineSpan)}) {Text("Saved layout could not load: $it")}}
            if(!loaded && failure==null) item(span={GridItemSpan(maxLineSpan)}) {LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(loaded && tiles.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {BrutalSurface(Modifier.fillMaxWidth()) {Text("Your canvas is empty",fontSize=22.sp);BrutalButton("Add a tile") {adding=true}}}
            items(tiles,key={it.id},span={GridItemSpan(it.columns.coerceIn(1,settings.gridColumns))}) {tile ->
                Column(Modifier.animateItem().combinedClickable(onClick={},onLongClick={vm.edit.value=true;configured=tile})) {NativeTile(tile,vm) {configured=tile};if(edit) TileEditControls(tile,vm) {configured=tile}}
            }
            if(loaded) item(span={GridItemSpan(maxLineSpan)}) {Row(Modifier.fillMaxWidth().heightIn(min=56.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {TextButton({adding=true}) {Icon(Icons.Default.Add,null);Text("Add tile")};IconButton({if(!SystemActions.expandNotifications(context)) vm.report("Set up Power Bubble to open notification shade")}) {Icon(Icons.Default.Notifications,"Open notification shade")}}}
        }
    }
    if(adding) AddTileDialog(vm,{adding=false},{configured=it;adding=false})
    configured?.let {TileConfigDialog(it,vm,{configured=null})}
}

@Composable private fun TileEditControls(tile: HomeTile,vm: LauncherViewModel,configure: ()->Unit) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        Icon(Icons.Default.DragHandle,"Drag to reorder",Modifier.size(48.dp).pointerInput(tile.id) {var travel=0f;detectDragGestures(onDragStart={travel=0f}) {change,drag -> change.consume();travel+=drag.y;if(abs(travel)>64.dp.toPx()) {vm.move(tile,if(travel>0) 1 else -1);travel=0f}}})
        IconButton({vm.move(tile,-1)}) {Icon(Icons.Default.ArrowUpward,"Move tile earlier")}
        IconButton({vm.move(tile,1)}) {Icon(Icons.Default.ArrowDownward,"Move tile later")}
        IconButton(configure) {Icon(Icons.Default.Tune,"Configure tile")}
    }
}

@Composable fun AppIcon(app: AppEntry,vm: LauncherViewModel,modifier: Modifier=Modifier.size(42.dp)) {
    val pixels=with(LocalDensity.current) {48.dp.roundToPx()}
    val generation by vm.core.icons.generation.collectAsStateWithLifecycle()
    val icon by produceState(vm.core.icons.cached(app.id,pixels),app.id,pixels,generation) {value=vm.core.icons.load(app,pixels)}
    if(icon!=null) Image(icon!!.asImageBitmap(),null,modifier) else Box(modifier,contentAlignment=Alignment.Center) {Text(app.label.take(1),fontSize=24.sp,fontWeight=FontWeight.Bold)}
}

private data class DrawerRow(val letter: Char?,val app: AppEntry?)
@Composable private fun AppsCanvas(vm: LauncherViewModel,search: Boolean) {
    val apps by vm.core.apps.apps.collectAsStateWithLifecycle()
    val loading by vm.core.apps.loading.collectAsStateWithLifecycle()
    val error by vm.core.apps.error.collectAsStateWithLifecycle()
    val settings by vm.core.settings.settings.collectAsStateWithLifecycle()
    val history by vm.core.layout.history.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val context=LocalContext.current;val scope=rememberCoroutineScope();val keyboard=LocalSoftwareKeyboardController.current
    val focus=remember {FocusRequester()};val list=rememberLazyListState()
    var menu by remember {mutableStateOf<AppEntry?>(null)}
    var accessibleLetters by remember {mutableStateOf(false)}
    val found=remember(apps,settings.appPreferences,history,query) {AppSearch.rank(apps.map {SearchApp(it.id,it.label,it.packageName,it.installedAt)},query,settings.appPreferences,history).mapNotNull {hit -> apps.firstOrNull {it.id==hit.id}}}
    val sections=remember(found,settings.appPreferences) {found.groupBy {settings.appPreferences.displayName(it.id,it.label).firstOrNull()?.uppercaseChar() ?: '#'}.toSortedMap()}
    val sequence=if(query.isNotBlank()) found.map {DrawerRow(null,it)} else sections.flatMap {(letter,entries)->listOf(DrawerRow(letter,null))+entries.map {DrawerRow(null,it)}}
    LaunchedEffect(search) {if(search) {focus.requestFocus();keyboard?.show()}}
    fun enter() {if(found.isNotEmpty()) vm.launch(found.first()) else if(query.isNotBlank() && !SystemActions.webSearch(context,query)) vm.report("No browser available")}
    fun jump(letter: Char) {val target=sequence.indexOfFirst {it.letter==letter};if(target>=0) scope.launch {list.scrollToItem(target)}}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        OutlinedTextField(query,{vm.query.value=it},Modifier.fillMaxWidth().focusRequester(focus),label={Text(if(search) "Find apps or search the web" else "Search apps")},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton({vm.query.value=""}) {Icon(Icons.Default.Close,"Clear query")}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={enter()}))
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it);TextButton({vm.core.apps.refresh()}) {Text("Retry")}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("${found.size} APPS",fontFamily=MonoFont,fontSize=11.sp,modifier=Modifier.weight(1f));if(query.isBlank()) TextButton({accessibleLetters=true}) {Text("Jump to letter")}}
        if(found.isEmpty()) {Text(if(query.length>=2) "No matching apps" else "No visible matching apps");if(query.isNotBlank()) BrutalButton("Search web for “$query”") {enter()}}
        Row(Modifier.weight(1f)) {
            LazyColumn(state=list,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                items(sequence,key={it.app?.id ?: "section:${it.letter}"}) {row ->
                    if(row.app==null) Row(verticalAlignment=Alignment.CenterVertically) {Text("${row.letter}",fontFamily=DisplayFont,fontSize=28.sp);HorizontalDivider(Modifier.padding(start=12.dp),color=Color(LocalLoud.current.palette.line))}
                    else {val app=row.app;BrutalSurface(Modifier.fillMaxWidth().combinedClickable(onClick={vm.launch(app)},onLongClick={menu=app})) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {AppIcon(app,vm);Text(settings.appPreferences.displayName(app.id,app.label),fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(app.id in settings.appPreferences.hiddenIds) Icon(Icons.Default.VisibilityOff,"Hidden app")}}}
                }
            }
            if(query.isBlank()) Column(Modifier.width(48.dp).fillMaxHeight().semantics {contentDescription="Alphabet scrubber. Drag to jump through letters, or use Jump to letter for buttons."}.pointerInput(sections.keys.toList()) {
                detectDragGestures {change,_ -> change.consume();val letters=sections.keys.toList();if(letters.isNotEmpty()) jump(letters[(change.position.y/size.height*letters.size).toInt().coerceIn(0,letters.lastIndex)])}
            },horizontalAlignment=Alignment.CenterHorizontally) {sections.keys.forEach {letter -> Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {Text("$letter",fontSize=12.sp)}}}
        }
    }
    menu?.let {AppMenu(it,vm,{menu=null})}
    if(accessibleLetters) AlertDialog(onDismissRequest={accessibleLetters=false},title={Text("Jump to letter")},text={FlowRow {sections.keys.forEach {letter -> TextButton({jump(letter);accessibleLetters=false},Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {Text("$letter")}}}},confirmButton={TextButton({accessibleLetters=false}) {Text("Close")}})
}
