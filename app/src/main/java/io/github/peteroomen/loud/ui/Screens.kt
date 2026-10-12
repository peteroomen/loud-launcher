package io.github.peteroomen.loud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.loud.ui.theme.*

enum class PreviewScreen(val title: String) { Home("Live"), Apps("Apps"), Search("Search"), Settings("Appearance"), Edit("Edit layout"), Gallery("Tile gallery"), Permissions("Permissions") }

@Composable
fun LoudPreviewApp() {
    var paletteName by rememberSaveable { mutableStateOf("Taxi") }
    var shapeName by rememberSaveable { mutableStateOf("Square") }
    var pageName by rememberSaveable { mutableStateOf("Home") }
    var message by rememberSaveable { mutableStateOf("") }
    val palette = LoudPalettes.all.first { it.name == paletteName }
    val shape = LoudShape.valueOf(shapeName)
    val page = PreviewScreen.valueOf(pageName)
    LoudTheme(palette,shape) {
        Surface(color=Color(palette.canvas),contentColor=Color(palette.onCanvas)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    LoudShape.entries.forEach { item -> BrutalButton(item.title, Modifier.weight(1f), item == shape) { shapeName = item.name } }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("LOUD✳",fontFamily=DisplayFont,fontSize=32.sp,fontWeight=FontWeight.Bold)
                    Text("SAMPLE DATA",fontFamily=MonoFont,fontSize=10.sp)
                    IconButton(onClick={pageName=PreviewScreen.Settings.name}) { Icon(Icons.Default.Tune,"Appearance") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    ScreenContent(page, onNavigate={pageName=it.name}, onPalette={paletteName=it.name}, onAction={message=it})
                    if(message.isNotEmpty()) { Text(message,fontSize=13.sp); TextButton(onClick={message=""}) { Text("Dismiss") } }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(PreviewScreen.Home,PreviewScreen.Apps,PreviewScreen.Search).forEach { item ->
                        BrutalButton(item.title.uppercase(),Modifier.weight(1f),page==item) { pageName=item.name }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenContent(screen: PreviewScreen, onNavigate: (PreviewScreen)->Unit = {}, onPalette: (LoudPalette)->Unit = {}, onAction: (String)->Unit = {}) {
    when(screen) {
        PreviewScreen.Home -> HomeDesign(onAction)
        PreviewScreen.Apps,PreviewScreen.Search -> AppsDesign(screen == PreviewScreen.Search,onAction)
        PreviewScreen.Settings -> SettingsDesign(onPalette,onNavigate)
        PreviewScreen.Edit -> EditDesign(onAction)
        PreviewScreen.Gallery -> GalleryDesign(onAction)
        PreviewScreen.Permissions -> PermissionsDesign(onAction)
    }
}

@Composable
fun HomeDesign(onAction:(String)->Unit = {}) {
    LoudTile(TileKind.Clock,TileSize(4,2),Modifier.fillMaxWidth(),onSampleAction=onAction)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        LoudTile(TileKind.Weather,TileSize(2,2),Modifier.weight(1f),onSampleAction=onAction)
        LoudTile(TileKind.Note,TileSize(2,2),Modifier.weight(1f),onSampleAction=onAction)
    }
    LoudTile(TileKind.Music,TileSize(4,2),Modifier.fillMaxWidth(),onSampleAction=onAction)
    LoudTile(TileKind.Calendar,TileSize(4,2),Modifier.fillMaxWidth(),onSampleAction=onAction)
    LoudTile(TileKind.Notifications,TileSize(4,1),Modifier.fillMaxWidth(),onSampleAction=onAction)
    LoudTile(TileKind.Dev,TileSize(4,1),Modifier.fillMaxWidth(),onSampleAction=onAction)
}

private val sampleApps = listOf("Calendar","Camera","ChatGPT","Chrome","Claude","GitHub","Maps","Settings","Spotify","Teams","WhatsApp","YouTube")
@Composable
fun AppsDesign(search: Boolean = false,onAction:(String)->Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val p=LocalLoud.current.palette
    Text(if(search) "FIND SOMETHING" else "APPS",fontFamily=LocalLoud.current.heading,fontSize=32.sp,fontWeight=FontWeight.Bold)
    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search sample apps")},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
    val visible=sampleApps.filter { it.contains(query,true) }
    Text("${visible.size} ITEMS",fontFamily=MonoFont,fontSize=11.sp)
    if(visible.isEmpty()) {
        Text("No matching apps")
        BrutalButton("Search web for “$query”") { onAction("Chrome web search will be connected after design approval.") }
    }
    visible.groupBy { it.first() }.forEach { (letter,apps) ->
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text("$letter",fontFamily=DisplayFont,fontSize=25.sp); HorizontalDivider(color=Color(p.onCanvas)) }
        apps.forEach { app ->
            val index=sampleApps.indexOf(app)
            BrutalSurface(Modifier.fillMaxWidth(),Color(listOf(p.paper,p.yellow,p.pink,p.cyan)[index%4]),onClick={onAction("$app would open on your phone. This gallery uses sample apps.")}) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(42.dp).background(Color(p.paper)),contentAlignment=Alignment.Center) { Text(app.take(2),fontWeight=FontWeight.Bold) }
                    Text(if(LocalLoud.current.shape == LoudShape.Square) app.uppercase() else app,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsDesign(onPalette:(LoudPalette)->Unit = {},onNavigate:(PreviewScreen)->Unit = {}) {
    val s=LocalLoud.current
    Text("MAKE IT YOURS",fontFamily=s.heading,fontSize=30.sp,fontWeight=FontWeight.Bold)
    Text("Colour palette",fontWeight=FontWeight.Bold)
    LoudPalettes.all.forEach { p ->
        BrutalSurface(Modifier.fillMaxWidth(),Color(p.paper),onClick={onPalette(p)}) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(p.name,color=Color(p.ink),modifier=Modifier.weight(1f))
                listOf(p.yellow,p.cyan,p.pink).forEach { fill -> Box(Modifier.size(24.dp).background(Color(fill))) }
                if(p==s.palette) Icon(Icons.Default.Check,"Selected",tint=Color(p.ink))
            }
        }
    }
    Text("Canvas follows the theme colour. Wallpaper is an optional override in the wired launcher.",fontSize=13.sp)
    BrutalSurface(Modifier.fillMaxWidth()) {
        Text("POWER BUBBLE / NEUBRUTAL",fontFamily=MonoFont,fontSize=11.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            BrutalButton("⏻",selected=true) { }
            Text("Matching appearance\nPower Bubble integration retained",fontSize=12.sp)
        }
    }
    listOf(PreviewScreen.Edit,PreviewScreen.Gallery,PreviewScreen.Permissions).forEach { page -> BrutalButton(page.title,Modifier.fillMaxWidth()) { onNavigate(page) } }
}

@Composable
fun EditDesign(onAction:(String)->Unit = {}) {
    var kind by remember { mutableStateOf(TileKind.Music) }
    var sizeIndex by remember { mutableIntStateOf(0) }
    var colourIndex by remember { mutableIntStateOf(0) }
    val p=LocalLoud.current.palette
    Text("EDIT TILE",fontFamily=LocalLoud.current.heading,fontSize=32.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        BrutalButton("Previous",Modifier.weight(1f)) { kind=TileKind.entries[(kind.ordinal+TileKind.entries.size-1)%TileKind.entries.size];sizeIndex=0 }
        BrutalButton("Next",Modifier.weight(1f)) { kind=TileKind.entries[(kind.ordinal+1)%TileKind.entries.size];sizeIndex=0 }
    }
    val size=kind.sizes[sizeIndex%kind.sizes.size]
    Text("${kind.title} / $size",fontFamily=MonoFont,fontSize=13.sp)
    LoudTile(kind,size,Modifier.fillMaxWidth(size.columns/4f),fillOverride=listOf(null,p.yellow,p.cyan,p.pink,p.paper)[colourIndex],onSampleAction=onAction)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        BrutalButton("Resize",Modifier.weight(1f)) { sizeIndex=(sizeIndex+1)%kind.sizes.size }
        BrutalButton("Recolour",Modifier.weight(1f)) { colourIndex=(colourIndex+1)%5 }
    }
    Text("Tile configuration preview. Dragging and saved layouts arrive in the editable-canvas milestone.",fontSize=13.sp)
}

@Composable
fun GalleryDesign(onAction:(String)->Unit = {}) {
    var state by remember { mutableStateOf(TileState.Content) }
    Text("TILE GALLERY",fontFamily=LocalLoud.current.heading,fontSize=32.sp)
    BrutalButton("State: ${state.name}",Modifier.fillMaxWidth()) { state=TileState.entries[(state.ordinal+1)%TileState.entries.size] }
    TileKind.entries.forEach { kind ->
        Text(kind.title,fontWeight=FontWeight.Bold)
        kind.sizes.forEach { size ->
            Text(size.toString(),fontFamily=MonoFont,fontSize=11.sp)
            LoudTile(kind,size,Modifier.fillMaxWidth(size.columns/4f),state,onSampleAction=onAction)
        }
    }
}

@Composable
fun PermissionsDesign(onAction:(String)->Unit = {}) {
    Text("ACCESS, EXPLAINED",fontFamily=LocalLoud.current.heading,fontSize=30.sp)
    listOf("Calendar" to "Your synced events, shown locally.","Notification access" to "Music controls and active notifications.","Usage access" to "App frequency beyond this launcher.","Location (optional)" to "Local weather. A manual city works too.","Power Bubble" to "Lock and notification-shade gestures.").forEach { (title,copy) ->
        BrutalSurface(Modifier.fillMaxWidth()) {
            Text(title,fontWeight=FontWeight.Bold)
            Text(copy,fontSize=13.sp)
            TextButton(onClick={onAction("$title setup is disabled in the sample-data design preview.")}) { Text("Set up") }
        }
    }
}
