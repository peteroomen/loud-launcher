package io.github.peteroomen.loud.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
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
data class LoudStyle(val palette: LoudPalette, val shape: LoudShape, val intensity: LoudIntensity = LoudIntensity.Standard) {
    val corners get() = RoundedCornerShape(if (shape == LoudShape.Round) 19.dp else 0.dp)
    val stroke get() = (if (shape == LoudShape.Round) 2.dp else 3.dp) * intensity.stroke
    val shadow get() = (if (shape == LoudShape.Round) 4.dp else 5.dp) * intensity.shadow
    val tagStroke get() = 2.dp * intensity.stroke
    val heading get() = if (shape == LoudShape.Round) BodyFont else DisplayFont
}
val LocalLoudHaptics = staticCompositionLocalOf { true }
val LocalLoudDecorations = staticCompositionLocalOf { true }
val LocalLoud = staticCompositionLocalOf { LoudStyle(LoudPalettes.Taxi, LoudShape.Square) }
@Composable
fun LoudTheme(palette: LoudPalette = LoudPalettes.Taxi, shape: LoudShape = LoudShape.Square, haptics: Boolean = true, decorations: Boolean = true, intensity: LoudIntensity = LoudIntensity.Standard, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLoud provides LoudStyle(palette, shape, intensity), LocalLoudHaptics provides haptics, LocalLoudDecorations provides decorations, LocalContentColor provides Color(palette.onCanvas)) {
        MaterialTheme(
            colorScheme = (if(palette == LoudPalettes.Night) darkColorScheme() else lightColorScheme()).copy(
                primary = Color(palette.onCanvas), onPrimary = Color(palette.canvas),
                background = Color(palette.canvas), onBackground = Color(palette.onCanvas),
                surface = Color(if(palette == LoudPalettes.Night) palette.canvas else palette.paper), onSurface = Color(palette.onCanvas),
                onSurfaceVariant = Color(palette.onCanvas), outline = Color(palette.onCanvas),
                secondaryContainer = Color(palette.yellow), onSecondaryContainer = Color(palette.ink),
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(if(shape == LoudShape.Round) 6.dp else 0.dp),
                small = RoundedCornerShape(if(shape == LoudShape.Round) 12.dp else 0.dp),
                medium = RoundedCornerShape(if(shape == LoudShape.Round) 19.dp else 0.dp),
                large = RoundedCornerShape(if(shape == LoudShape.Round) 24.dp else 0.dp),
                extraLarge = RoundedCornerShape(if(shape == LoudShape.Round) 28.dp else 0.dp),
            ),
            typography = Typography(
                bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp),
                labelLarge = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            ), content = content,
        )
    }
}
