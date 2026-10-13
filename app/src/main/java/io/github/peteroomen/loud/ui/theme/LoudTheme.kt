package io.github.peteroomen.loud.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.loud.R

val DisplayFont = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))
val BodyFont = FontFamily(Font(R.font.space_grotesk))
val MonoFont = FontFamily(Font(R.font.space_mono))
data class LoudStyle(val palette: LoudPalette, val shape: LoudShape) {
    val corners get() = RoundedCornerShape(if (shape == LoudShape.Round) 19.dp else 0.dp)
    val stroke get() = if (shape == LoudShape.Round) 2.dp else 3.dp
    val shadow get() = if (shape == LoudShape.Round) 4.dp else 5.dp
    val heading get() = if (shape == LoudShape.Round) BodyFont else DisplayFont
}
val LocalLoudHaptics = staticCompositionLocalOf { true }
val LocalLoudDecorations = staticCompositionLocalOf { true }
val LocalLoud = staticCompositionLocalOf { LoudStyle(LoudPalettes.Taxi, LoudShape.Square) }
@Composable
fun LoudTheme(palette: LoudPalette = LoudPalettes.Taxi, shape: LoudShape = LoudShape.Square, haptics: Boolean = true, decorations: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLoud provides LoudStyle(palette, shape), LocalLoudHaptics provides haptics, LocalLoudDecorations provides decorations) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(palette.ink), onPrimary = Color(palette.paper),
                background = Color(palette.canvas), onBackground = Color(palette.onCanvas),
                surface = Color(palette.paper), onSurface = Color(palette.ink),
                secondaryContainer = Color(palette.yellow), onSecondaryContainer = Color(palette.ink),
            ),
            typography = Typography(
                bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp),
                labelLarge = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            ), content = content,
        )
    }
}
