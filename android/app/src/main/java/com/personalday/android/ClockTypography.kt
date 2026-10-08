package com.personalday.android

import com.personalday.core.DesignSettings
import kotlin.math.min

/** Parameters approved in the isolated native-Canvas typography preview.
 * Coordinates refer to visible glyph edges, not TextView boxes or font ascenders.
 */
object ClockTypography {
    const val DESIGN_WIDTH = 1080f
    const val DESIGN_HEIGHT = 470f
    const val SUPERSAMPLING = 4
    const val MAX_OUTPUT_PIXELS = 480_000
    const val MASK_CACHE_BYTES = 4 * 1024 * 1024

    enum class FontFace { ROBOTO, YAHEI_LIGHT }
    data class Point(val x: Float, val y: Float)
    data class TextStyle(
        val font: FontFace,
        val sizePx: Float,
        val weight: Int,
        val scaleY: Float,
        val trackingPx: Float = 0f,
        val trimXPx: Float = 0f,
        val trimYPx: Float = 0f,
        val opacity: Float,
    )
    data class ProgressStyle(
        val offset: Point,
        val width: Float,
        val height: Float,
        val opacity: Float,
        val trackRelativeOpacity: Float,
        val tickRelativeOpacity: Float,
        val tickTop: Float = -4f,
        val tickBottom: Float = 7f,
        val tickWidth: Float = 1f,
        val ticks: List<Float> = listOf(.25f, .5f, .75f),
    )
    data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float)

    val LEFT = Point(32f, 32f)
    val RIGHT = Point(600f, 32f)
    val SECOND_OFFSET = Point(1f, 146f)
    val THIRD_OFFSET = Point(4f, 287f)

    val TIME = TextStyle(FontFace.ROBOTO, 168.2f, 190, 1.01f, opacity = 1f)
    val CHINESE = TextStyle(FontFace.YAHEI_LIGHT, 108.112f, 290, 1.16f,
        trimXPx = .8f, trimYPx = 1f, opacity = .5f)
    val DATE = TextStyle(FontFace.ROBOTO, 124.12f, 130, 1.14f, trimYPx = .1f, opacity = .5f)
    val PERCENTAGE = TextStyle(FontFace.ROBOTO, 116f, 130, 1.21f, trimYPx = .1f, opacity = .5f)
    val PROGRESS = ProgressStyle(Point(1f, 204f), 426f, 4f, .5f, 52f / 161f, 133f / 161f,
        tickTop = -5f, tickBottom = 9f, tickWidth = 1.5f)

    /** Keep the approved 100% layout unchanged. Scale stroke trimming with the glyph
     * so small sizes retain thin strokes instead of erasing them. The supported range
     * reserves room for long times, all calendar strings, and 100.00% without resizing
     * text as its content changes. */
    fun text(base: TextStyle, sizePercent: Int, opacityPercent: Int): TextStyle {
        val scale = sizePercent / 100f
        return base.copy(sizePx = base.sizePx * scale, trackingPx = base.trackingPx * scale,
            trimXPx = base.trimXPx * scale, trimYPx = base.trimYPx * scale,
            opacity = opacityPercent / 100f)
    }

    fun progress(design: DesignSettings): ProgressStyle {
        val thickness = design.progressThickness / 100f
        return PROGRESS.copy(height = PROGRESS.height * thickness,
            opacity = design.middleOpacity / 100f, tickTop = PROGRESS.tickTop * thickness,
            tickBottom = PROGRESS.tickBottom * thickness, tickWidth = PROGRESS.tickWidth * thickness)
    }

    fun fit(widthPx: Int, heightPx: Int): Fit {
        require(widthPx > 0 && heightPx > 0)
        val scale = min(widthPx / DESIGN_WIDTH, heightPx / DESIGN_HEIGHT)
        return Fit(scale, (widthPx - DESIGN_WIDTH * scale) / 2f,
            (heightPx - DESIGN_HEIGHT * scale) / 2f)
    }
}
