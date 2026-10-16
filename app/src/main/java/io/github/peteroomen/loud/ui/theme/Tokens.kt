package io.github.peteroomen.loud.ui.theme

/** Opaque RGB tokens. Every fill uses onTile; canvas uses onCanvas. */
data class LoudPalette(
    val name: String, val canvas: Long, val onCanvas: Long, val ink: Long,
    val yellow: Long, val cyan: Long, val pink: Long, val paper: Long, val line: Long,
) {
    val textPairs get() = listOf(canvas to onCanvas, yellow to ink, cyan to ink, pink to ink, paper to ink)
    /** Dark canvas with light text: dark system bars and Material dark scheme. */
    val dark get() = onCanvas != ink
}

object LoudPalettes {
    val Taxi = LoudPalette("Taxi", 0xFFF5F0E5, 0xFF171714, 0xFF171714, 0xFFFFE600, 0xFF00CBE5, 0xFFFF6395, 0xFFFFFDF6, 0xFF171714)
    val Riso = LoudPalette("Riso", 0xFFFFF5EC, 0xFF171714, 0xFF171714, 0xFFFF88B3, 0xFF8FBCFF, 0xFFFFBB8A, 0xFFFFFAF6, 0xFF171714)
    val Acid = LoudPalette("Acid", 0xFFF0F1DF, 0xFF171714, 0xFF171714, 0xFFD1F555, 0xFFBBA1FF, 0xFFFFA768, 0xFFFCFFEF, 0xFF171714)
    val Night = LoudPalette("Night Brutal", 0xFF202023, 0xFFF0EDE2, 0xFF171714, 0xFFE0FC67, 0xFF74DFED, 0xFFF3A4CE, 0xFFE8E5DC, 0xFFF0EDE2)
    val Candy = LoudPalette("Candy", 0xFFFFF2E8, 0xFF171714, 0xFF171714, 0xFFF8DE8B, 0xFFB3DFE1, 0xFFF5B8C8, 0xFFFFFAF3, 0xFF171714)
    val PopGarden = LoudPalette("Pop Garden", 0xFFFF9ACB, 0xFF171714, 0xFF171714, 0xFF4BE37A, 0xFF9FD8FF, 0xFFFFE45C, 0xFFFFF7FB, 0xFF171714)
    val BabyBlue = LoudPalette("Baby Blue", 0xFFD6EEFF, 0xFF171714, 0xFF171714, 0xFF8FCBFF, 0xFFFFB3D9, 0xFFB9F5A0, 0xFFF7FCFF, 0xFF171714)
    val Rave = LoudPalette("Rave", 0xFF0E3B2C, 0xFFF4F1E6, 0xFF171714, 0xFFFF6FB5, 0xFFB8F25C, 0xFF9AD6FF, 0xFFF4F1E6, 0xFFF4F1E6)
    val all = listOf(Taxi, Riso, Acid, Night, Candy, PopGarden, BabyBlue, Rave)
}
enum class LoudShape(val title: String) { Square("Square Punch"), Round("Round Paper") }
/** Scales outline and hard-shadow weight independently of shape and palette. */
enum class LoudIntensity(val title: String, val stroke: Float, val shadow: Float) { Calm("Calm", .75f, .6f), Standard("Standard", 1f, 1f), Loud("Loud", 1.5f, 1.6f) }
