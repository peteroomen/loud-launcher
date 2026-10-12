package io.github.peteroomen.loud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.peteroomen.loud.ui.theme.*

data class TileExample(val kind:TileKind,val size:TileSize,val palette:LoudPalette,val shape:LoudShape) {
    override fun toString()="${shape.title} / ${palette.name} / ${kind.title} / $size"
}
class TileExamples:PreviewParameterProvider<TileExample> {
    override val values=sequence {
        for(shape in LoudShape.entries) for(palette in listOf(LoudPalettes.Taxi,LoudPalettes.Night))
            for(kind in TileKind.entries) for(size in kind.sizes) yield(TileExample(kind,size,palette,shape))
    }
}
@Preview(name="Every tile / supported size / two themes / two shapes",showBackground=true,widthDp=392)
@Composable
fun TilePreviews(@PreviewParameter(TileExamples::class) example:TileExample) {
    LoudTheme(example.palette,example.shape) {
        Box(Modifier.background(Color(example.palette.canvas)).padding(16.dp)) {
            LoudTile(example.kind,example.size,Modifier.width((example.size.columns*86).dp))
        }
    }
}
data class ScreenExample(val screen:PreviewScreen,val palette:LoudPalette,val shape:LoudShape) {
    override fun toString()="${shape.title} / ${palette.name} / ${screen.title}"
}
class ScreenExamples:PreviewParameterProvider<ScreenExample> {
    override val values=sequence {
        for(shape in LoudShape.entries) for(palette in listOf(LoudPalettes.Taxi,LoudPalettes.Night))
            for(screen in PreviewScreen.entries) yield(ScreenExample(screen,palette,shape))
    }
}
@Preview(name="Screen designs",widthDp=392,showBackground=true)
@Composable
fun ScreenPreviews(@PreviewParameter(ScreenExamples::class) example:ScreenExample) {
    LoudTheme(example.palette,example.shape) {
        CompositionLocalProvider(LocalContentColor provides Color(example.palette.onCanvas)) {
            Column(Modifier.background(Color(example.palette.canvas)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { ScreenContent(example.screen) }
        }
    }
}
@Preview(name="Round / 1.3 font scale",widthDp=360,fontScale=1.3f,showBackground=true)
@Preview(name="Round / compact phone",widthDp=320,showBackground=true)
@Composable
fun RoundAccessibilityPreview() {
    LoudTheme(shape=LoudShape.Round) { Column(Modifier.background(Color(LoudPalettes.Taxi.canvas)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { HomeDesign() } }
}
@Preview(name="Square / 1.3 font scale",widthDp=360,fontScale=1.3f,showBackground=true)
@Composable
fun SquareAccessibilityPreview() {
    LoudTheme { Column(Modifier.background(Color(LoudPalettes.Taxi.canvas)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { HomeDesign() } }
}
