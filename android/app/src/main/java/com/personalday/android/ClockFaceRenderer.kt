package com.personalday.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.graphics.text.TextRunShaper
import android.os.Build
import android.util.Log
import android.util.LruCache
import com.personalday.core.DesignSettings
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Dynamic counterpart of the approved Android Canvas probe; no pre-rendered clock assets. */
object ClockFaceRenderer {
    private const val TAG = "ClockTypography"
    private data class FaceKey(val font: ClockTypography.FontFace, val weight: Int)
    private data class MaskKey(val text: String, val style: ClockTypography.TextStyle)
    data class FontAudit(
        val font: String,
        val requestedWeight: Int,
        val actualTypefaceWeight: Int,
        val resourceSha256: String,
        val actualBufferSha256: Set<String>,
        val actualWeights: Set<Int>,
        val axes: Set<String>,
        val verified: Boolean,
        val note: String,
    )
    private data class LoadedFace(val typeface: Typeface, val audit: FontAudit)
    private val faces = mutableMapOf<FaceKey, LoadedFace>()
    private val masks = object : LruCache<MaskKey, Bitmap>(ClockTypography.MASK_CACHE_BYTES) {
        override fun sizeOf(key: MaskKey, value: Bitmap) = value.allocationByteCount
        // Do not recycle on eviction: a draw can still hold a reference to the mask.
    }
    private sealed class Middle {
        data class Text(val value: String) : Middle()
        data class Progress(val units: Int) : Middle()
    }
    private data class Group(val time: String, val middle: Middle, val bottom: String)

    /** Both sizes and the in-app preview use this exact drawing path.
     * The RemoteViews caller caps output for Binder; diagnostics can request the full
     * 1080 x 470 reference canvas. [percent] shares a snapshot with [progressBasisPoints].
     */
    fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        regularTime: String,
        personalTime: String,
        calendarPeriod: String,
        calendarDate: String,
        percent: String,
        progressBasisPoints: Int,
        textColor: Int,
        design: DesignSettings = DesignSettings(),
        backgroundRgb: Int = 0xD8D8D8,
        backgroundOpacity: Int = 0,
    ): Bitmap {
        require(widthPx > 0 && heightPx > 0)
        design.validate()
        require(backgroundOpacity in 0..100 && backgroundRgb in 0..0xFFFFFF)
        val width = widthPx
        val height = heightPx
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val canvas = Canvas(result)
        val fit = ClockTypography.fit(width, height)
        canvas.translate(fit.offsetX, fit.offsetY)
        canvas.scale(fit.scale, fit.scale)
        if (backgroundOpacity > 0) {
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundRgb or 0xff000000.toInt()
                alpha = (backgroundOpacity * 255 / 100f).roundToInt()
            }
            canvas.drawRoundRect(0f, 0f, ClockTypography.DESIGN_WIDTH, ClockTypography.DESIGN_HEIGHT,
                16f, 16f, background)
        }
        val rgb = textColor or 0xff000000.toInt()
        drawGroup(context, canvas, ClockTypography.LEFT,
            Group(regularTime, Middle.Text(calendarPeriod), calendarDate), rgb, design)
        drawGroup(context, canvas, ClockTypography.RIGHT,
            Group(personalTime, Middle.Progress(progressBasisPoints.coerceIn(0, 10000)), percent), rgb, design)
        return result
    }

    @Synchronized
    fun fontDiagnostics(context: Context): List<FontAudit> = listOf(
        ClockTypography.TIME, ClockTypography.CHINESE, ClockTypography.DATE,
    ).map { loadFace(context, it).audit }

    private fun drawGroup(context: Context, canvas: Canvas, origin: ClockTypography.Point, group: Group, color: Int,
                          design: DesignSettings) {
        drawText(context, canvas, group.time,
            ClockTypography.text(ClockTypography.TIME, design.timeScale, design.timeOpacity), origin.x, origin.y, color)
        when (val middle = group.middle) {
            is Middle.Text -> drawText(context, canvas, middle.value,
                ClockTypography.text(ClockTypography.CHINESE, design.middleScale, design.middleOpacity),
                origin.x + ClockTypography.SECOND_OFFSET.x, origin.y + ClockTypography.SECOND_OFFSET.y, color)
            is Middle.Progress -> drawProgress(canvas, origin, middle.units, color, ClockTypography.progress(design))
        }
        drawText(context, canvas, group.bottom,
            ClockTypography.text(if (group.middle is Middle.Progress) ClockTypography.PERCENTAGE else ClockTypography.DATE,
                design.bottomScale, design.bottomOpacity),
            origin.x + ClockTypography.THIRD_OFFSET.x, origin.y + ClockTypography.THIRD_OFFSET.y, color)
    }

    private fun drawText(context: Context, canvas: Canvas, text: String, style: ClockTypography.TextStyle,
                         x: Float, y: Float, color: Int) {
        if (text.isEmpty() || style.opacity == 0f) return
        // Opacity changes only composition. Reuse the same expensive glyph raster.
        val key = MaskKey(text, style.copy(opacity = 1f))
        val mask = masks.get(key) ?: rasterize(context, text, style).also { masks.put(key, it) }
        val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            alpha = (style.opacity * 255).roundToInt()
        }
        canvas.drawBitmap(mask, x, y, tint)
    }

    private fun drawProgress(canvas: Canvas, origin: ClockTypography.Point, units: Int, color: Int,
                             style: ClockTypography.ProgressStyle) {
        if (style.opacity == 0f) return
        // Draw once into a transparent strip, replacing alpha at overlapping ticks/fill.
        // Apply the selected row opacity once; crossings cannot become more opaque.
        val strip = Bitmap.createBitmap(style.width.toInt() + 1,
            ceil(style.tickBottom - style.tickTop).toInt(), Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val c = Canvas(strip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val y = -style.tickTop
        val filled = if (units >= 10000) style.width else floor(style.width * units / 10000f)
        // Non-overlapping fill/track also avoids double antialias coverage at
        // fractional top/bottom coordinates when the user selects a thin strip.
        p.alpha = (style.trackRelativeOpacity * 255).roundToInt()
        c.drawRect(filled, y, style.width, y + style.height, p)
        p.alpha = 255
        c.drawRect(0f, y, filled, y + style.height, p)
        p.alpha = (style.tickRelativeOpacity * 255).roundToInt()
        style.ticks.forEach { fraction ->
            val x = (style.width * fraction).roundToInt().toFloat()
            c.drawRect(x, 0f, x + style.tickWidth, strip.height.toFloat(), p)
        }
        canvas.drawBitmap(strip, origin.x + style.offset.x, origin.y + style.offset.y + style.tickTop,
            Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (style.opacity * 255).roundToInt() })
        strip.recycle()
    }

    @Synchronized
    private fun loadFace(context: Context, style: ClockTypography.TextStyle): LoadedFace {
        val key = FaceKey(style.font, style.weight)
        faces[key]?.let { return it }
        val resource = when (style.font) {
            ClockTypography.FontFace.ROBOTO -> R.font.clock_roboto
            ClockTypography.FontFace.YAHEI_LIGHT -> R.font.clock_yahei_light
        }
        val face = if (Build.FINGERPRINT == "robolectric") {
            // Resource shadows are only a structure-test path; auditFont labels it unverified.
            Typeface.create(context.resources.getFont(resource), style.weight, false)
        } else {
            // A compiled res/font entry is not an AssetManager assets/ path. Load its
            // resource bytes explicitly, then preserve the selected variation in the family.
            val builder = Font.Builder(context.resources, resource)
                .setTtcIndex(0).setWeight(style.weight).setSlant(FontStyle.FONT_SLANT_UPRIGHT)
            if (style.font == ClockTypography.FontFace.ROBOTO) {
                builder.setFontVariationSettings("'wght' ${style.weight}, 'wdth' 100, 'ital' 0")
            }
            val font = builder.build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                .setStyle(FontStyle(style.weight, FontStyle.FONT_SLANT_UPRIGHT)).build()
        }
        val resourceHash = context.resources.openRawResource(resource).use { sha(it.readBytes()) }
        val audit = auditFont(face, style, resourceHash)
        Log.i(TAG, "font-audit font=${audit.font} requested=${audit.requestedWeight} actual=${audit.actualTypefaceWeight} " +
            "verified=${audit.verified} sha=${audit.resourceSha256} actualSha=${audit.actualBufferSha256} " +
            "weights=${audit.actualWeights} axes=${audit.axes} note=${audit.note}")
        return LoadedFace(face, audit).also { faces[key] = it }
    }

    private fun auditFont(face: Typeface, style: ClockTypography.TextStyle, resourceHash: String): FontAudit {
        if (Build.FINGERPRINT == "robolectric") {
            return FontAudit(style.font.name, style.weight, face.weight, resourceHash,
                emptySet(), emptySet(), emptySet(), false, "Host tests do not certify native font selection")
        }
        val sample = if (style.font == ClockTypography.FontFace.YAHEI_LIGHT) "周一二三四五六日上下午" else "0123456789:/.%-"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = style.sizePx }
        val glyphs = TextRunShaper.shapeTextRun(sample, 0, sample.length, 0, sample.length, 0f, 0f, false, paint)
        val hashes = linkedSetOf<String>()
        val weights = linkedSetOf<Int>()
        val axes = linkedSetOf<String>()
        val seen = hashSetOf<Int>()
        check(glyphs.glyphCount() > 0) { "Bundled font produced no glyphs: ${style.font}" }
        for (i in 0 until glyphs.glyphCount()) {
            check(glyphs.getGlyphId(i) != 0) { "Missing glyph in ${style.font}" }
            val font = glyphs.getFont(i)
            check(font.ttcIndex == 0) { "Unexpected font collection face: ${font.ttcIndex}" }
            if (seen.add(font.sourceIdentifier)) {
                hashes += sha(font.buffer)
                weights += font.style.weight
                font.axes?.forEach { axes += "${it.tag}=${it.styleValue}" }
            }
        }
        check(hashes == setOf(resourceHash)) { "Font fallback detected for ${style.font}: expected $resourceHash, actual $hashes" }
        check(weights == setOf(style.weight)) { "Unexpected native font weight for ${style.font}: $weights" }
        return FontAudit(style.font.name, style.weight, face.weight, resourceHash, hashes, weights, axes, true,
            "Bundled resource matches every shaped sample glyph; face 0")
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha(buffer: ByteBuffer): String {
        val copy = buffer.asReadOnlyBuffer().apply { position(0) }
        return MessageDigest.getInstance("SHA-256").apply { update(copy) }.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun rasterize(context: Context, text: String, style: ClockTypography.TextStyle): Bitmap {
        val ss = ClockTypography.SUPERSAMPLING
        val face = loadFace(context, style).typeface
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face; textSize = style.sizePx; color = Color.WHITE
            hinting = Paint.HINTING_OFF; isFakeBoldText = false
        }
        val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
        val advance = chars.sumOf { (paint.measureText(it) + style.trackingPx).toDouble() }.toFloat() - style.trackingPx
        val metrics = paint.fontMetrics
        val pad = 32
        val width = ceil(advance.coerceAtLeast(1f) * ss).toInt() + pad * 2
        val height = ceil((metrics.bottom - metrics.top) * style.scaleY * ss).toInt() + pad * 2
        val high = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val c = Canvas(high)
        c.translate(pad.toFloat(), pad.toFloat()); c.scale(ss.toFloat(), ss * style.scaleY)
        var x = 0f
        chars.forEach { char -> c.drawText(char, x, -metrics.top, paint); x += paint.measureText(char) + style.trackingPx }
        var pixels = IntArray(width * height)
        high.getPixels(pixels, 0, width, 0, 0, width, height)
        if (style.trimYPx > 0) pixels = erode(pixels, width, height, style.trimYPx * ss, vertical = true)
        if (style.trimXPx > 0) pixels = erode(pixels, width, height, style.trimXPx * ss, vertical = false)
        high.setPixels(pixels, 0, width, 0, 0, width, height)
        val bounds = alphaBounds(pixels, width, height)
        if (bounds == null) {
            high.recycle()
            // LEGACY graphics has no actual raster. Keep structure tests possible without
            // mislabelling their empty placeholder as rendered-font evidence.
            check(Build.FINGERPRINT == "robolectric") { "Empty native glyph raster: $text" }
            Log.w(TAG, "No glyph raster in host graphics mode; font visuals remain unverified")
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        }
        val left = bounds[0] / ss * ss
        val top = bounds[1] / ss * ss
        val cropWidth = (bounds[2] + ss - 1) / ss * ss - left
        val cropHeight = (bounds[3] + ss - 1) / ss * ss - top
        val crop = Bitmap.createBitmap(high, left, top, cropWidth, cropHeight)
        val small = Bitmap.createScaledBitmap(crop, cropWidth / ss, cropHeight / ss, true)
        val smallPixels = IntArray(small.width * small.height)
        small.getPixels(smallPixels, 0, small.width, 0, 0, small.width, small.height)
        val final = alphaBounds(smallPixels, small.width, small.height)
        check(final != null) { "Downsample removed glyphs: $text" }
        val result = Bitmap.createBitmap(small, final[0], final[1], final[2] - final[0], final[3] - final[1])
        result.density = Bitmap.DENSITY_NONE
        if (result !== small) small.recycle()
        if (crop !== small && crop !== result) crop.recycle()
        if (high !== crop && high !== small && high !== result) high.recycle()
        return result
    }

    private fun alphaBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) if (pixels[y * width + x] ushr 24 > 0) {
            if (x < left) left = x; if (x > right) right = x
            if (y < top) top = y; if (y > bottom) bottom = y
        }
        return if (right < left) null else intArrayOf(left, top, right + 1, bottom + 1)
    }

    private fun erode(source: IntArray, width: Int, height: Int, radius: Float, vertical: Boolean): IntArray {
        val result = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var alpha = source[y * width + x] ushr 24
            // Most of the glyph layer is transparent. A min filter cannot turn a
            // zero alpha into a nonzero value, so avoid all sampling in that case.
            if (alpha == 0) continue
            for (delta in 1..floor(radius).toInt()) {
                alpha = min(alpha, sampleAlpha(source, width, height, x, y, -delta.toFloat(), vertical))
                if (alpha == 0) break
                alpha = min(alpha, sampleAlpha(source, width, height, x, y, delta.toFloat(), vertical))
                if (alpha == 0) break
            }
            if (alpha == 0) continue
            alpha = min(alpha, sampleAlpha(source, width, height, x, y, -radius, vertical))
            if (alpha == 0) continue
            alpha = min(alpha, sampleAlpha(source, width, height, x, y, radius, vertical))
            if (alpha == 0) continue
            result[y * width + x] = (alpha shl 24) or 0x00ffffff
        }
        return result
    }

    private fun sampleAlpha(source: IntArray, width: Int, height: Int, x: Int, y: Int, offset: Float, vertical: Boolean): Int {
        val coordinate = (if (vertical) y else x) + offset
        val first = floor(coordinate).toInt()
        val fraction = coordinate - first
        val limit = if (vertical) height else width
        fun at(index: Int): Int = if (index !in 0 until limit) 0
            else source[if (vertical) index * width + x else y * width + index] ushr 24
        return (at(first) * (1 - fraction) + at(first + 1) * fraction).roundToInt()
    }
}
