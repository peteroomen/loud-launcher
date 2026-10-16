package io.github.peteroomen.loud.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peteroomen.loud.ui.theme.*

fun Modifier.brutalShadow(shape: Shape, color: Color, offset: Dp) = drawBehind {
    translate(offset.toPx(), offset.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
}

@Composable
fun BrutalSurface(
    modifier: Modifier = Modifier,
    fill: Color = Color(LocalLoud.current.palette.paper),
    onClick: (() -> Unit)? = null,
    haptics: Boolean = LocalLoudHaptics.current,
    motion: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalLoud.current
    val shadow = if (LocalLoudDecorations.current) style.shadow else 0.dp
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val displacement by animateFloatAsState(if (pressed) 1f else 0f, if (motion) tween(90) else snap(), label = "tile press")
    val haptic = LocalHapticFeedback.current
    val clickable = if (onClick == null) Modifier else Modifier.clickable(source, null, role = Role.Button) {
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    CompositionLocalProvider(LocalContentColor provides Color(style.palette.ink)) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary=Color(style.palette.ink), onPrimary=Color(style.palette.paper), surface=Color(style.palette.paper), onSurface=Color(style.palette.ink), onSurfaceVariant=Color(style.palette.ink), outline=Color(style.palette.ink))) {
        Column(
            modifier.padding(end = shadow, bottom = shadow)
                .heightIn(min = if (onClick != null) 48.dp else 0.dp)
                .graphicsLayer { translationX = shadow.toPx() * displacement; translationY = shadow.toPx() * displacement }
                .brutalShadow(style.corners, Color(style.palette.line), shadow * (1f - displacement))
                .clip(style.corners).background(fill).border(style.stroke, Color(style.palette.line), style.corners)
                .then(clickable).padding(14.dp),
            content = content,
        )
        }
    }
}

@Composable
fun Tag(label: String, modifier: Modifier = Modifier) {
    val s = LocalLoud.current
    Text(label, modifier.border(s.tagStroke, Color(s.palette.ink)).background(Color(s.palette.paper)).padding(horizontal = 7.dp, vertical = 4.dp),
        fontFamily = MonoFont, fontSize = 11.sp, color = Color(s.palette.ink))
}

@Composable
fun BrutalButton(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val p = LocalLoud.current.palette
    BrutalSurface(modifier.heightIn(min = 48.dp), fill = Color(if (selected) p.yellow else p.paper), onClick = onClick) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
