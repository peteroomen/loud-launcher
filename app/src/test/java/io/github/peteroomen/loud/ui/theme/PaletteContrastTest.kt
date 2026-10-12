package io.github.peteroomen.loud.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteContrastTest {
    private fun luminance(rgb:Long):Double {
        val channels=listOf(16,8,0).map { shift ->
            val c=((rgb shr shift) and 255).toDouble()/255
            if(c<=0.04045)c/12.92 else ((c+0.055)/1.055).pow(2.4)
        }
        return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722
    }
    @Test fun everyTextFillPairMeetsAAIncludingPerTileOverrides() {
        LoudPalettes.all.forEach { palette -> palette.textPairs.forEach { (background,foreground) ->
            val a=luminance(background);val b=luminance(foreground)
            val contrast=(maxOf(a,b)+.05)/(minOf(a,b)+.05)
            assertTrue("${palette.name}: $contrast < 4.5",contrast>=4.5)
        } }
    }
}
