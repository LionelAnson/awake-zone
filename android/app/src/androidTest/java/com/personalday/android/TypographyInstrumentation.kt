package com.personalday.android

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.ImageView
import com.personalday.core.DesignSettings
import com.personalday.core.InkMode
import com.personalday.core.Preferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Runs the product's renderer on Android's native graphics stack without a test framework.
 * PNGs and the machine-readable report stay in the target app's private files directory.
 * These checks prove rendering invariants; they do not certify the Xiaomi reference match.
 */
class TypographyInstrumentation : Instrumentation() {
    private val checks = JSONArray()
    private val frames = JSONArray()
    private val fontRuns = JSONArray()
    private val failures = mutableListOf<String>()
    private lateinit var output: File

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        output = File(targetContext.filesDir, "typography-018")
        val report = JSONObject()
            .put("startedAt", Instant.now().toString())
            .put("api", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("renderer", "ClockFaceRenderer.render")
            .put("verificationBoundary", "Native font and bitmap checks; Xiaomi physical-device appearance is not verified.")
        try {
            check(output.isDirectory || output.mkdirs()) { "Cannot create ${output.absolutePath}" }
            runFrames()
            runFonts()
            report.put("unchangedDefaultCheckCount", checks.length())
            runDesignChecks()
        } catch (error: Throwable) {
            failures += "Unhandled diagnostic error: ${error.javaClass.name}: ${error.message}"
            report.put("error", error.stackTraceToString())
        }
        report.put("finishedAt", Instant.now().toString())
            .put("passed", failures.isEmpty())
            .put("checks", checks)
            .put("frames", frames)
            .put("fontRuns", fontRuns)
            .put("failures", JSONArray(failures))
        val reportFile = File(output, "report.json")
        try {
            reportFile.writeText(report.toString(2))
        } catch (error: Throwable) {
            failures += "Cannot persist report: ${error.message}"
        }
        finish(if (failures.isEmpty()) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("report", reportFile.absolutePath)
            putString("outputDirectory", output.absolutePath)
            putInt("checkCount", checks.length())
            putInt("failureCount", failures.size)
            putString("failures", failures.joinToString("\n"))
            putString(REPORT_KEY_STREAMRESULT,
                "\nTypography native diagnostics: ${checks.length()} checks, ${failures.size} failures.\n" +
                    "Report: ${reportFile.absolutePath}\n" + failures.joinToString("\n"))
        })
    }

    private fun runFrames() {
        frame("sample-1080-white", 1080, 470, Color.WHITE, verifyBaseAlpha = true).recycle()
        frame("sample-1080-black", 1080, 470, Color.BLACK, verifyBaseAlpha = true).recycle()
        frame("wide-320x140", 320, 140, Color.WHITE).recycle()
        frame("compact-160x70", 160, 70, Color.WHITE).recycle()
        frame("narrow-tall-160x320", 160, 320, Color.WHITE).recycle()
        frame("wide-short-480x70", 480, 70, Color.BLACK).recycle()

        val empty = frame("progress-zero", 1080, 470, Color.WHITE,
            personal = "00:00", percent = "0.00%", units = 0, verifyBaseAlpha = true)
        val complete = frame("progress-complete", 1080, 470, Color.WHITE,
            regular = "0:00", personal = "24:00", period = "周五上午", date = "10/02",
            percent = "100.00%", units = 10_000, verifyBaseAlpha = true)
        // Probe the bar between quarter ticks, avoiding anti-aliased ends and tick crossings.
        for (x in intArrayOf(645, 745, 845, 945)) {
            val emptyAlpha = maxAlpha(empty, Rect(x, 236, x + 3, 240))
            val completeAlpha = maxAlpha(complete, Rect(x, 236, x + 3, 240))
            record("progress endpoints at x=$x", completeAlpha == 128 && emptyAlpha < completeAlpha,
                "zero=$emptyAlpha; full=$completeAlpha")
        }
        val beforeEnd = frame("progress-before-end", 1080, 470, Color.WHITE,
            personal = "23:59", percent = "99.99%", units = 9999, verifyBaseAlpha = true)
        val finalPixel = Rect(1026, 236, 1027, 240)
        val beforeEndAlpha = maxAlpha(beforeEnd, finalPixel)
        val fullEndAlpha = maxAlpha(complete, finalPixel)
        record("99.99 percent does not fill final bar pixel", beforeEndAlpha < fullEndAlpha && fullEndAlpha == 128,
            "x=1026; 9999=$beforeEndAlpha; 10000=$fullEndAlpha")
        beforeEnd.recycle()
        empty.recycle()
        complete.recycle()

        val calendarRasters = mutableSetOf<Int>()
        for (day in listOf("一", "二", "三", "四", "五", "六", "日")) {
            for (period in listOf("上午", "下午")) {
                val value = "周$day$period"
                val bitmap = render(320, 140, Color.WHITE, period = value)
                val region = mapped(Rect(25, 174, 475, 304), bitmap)
                calendarRasters += pixels(bitmap, region).contentHashCode()
                record("calendar content $value", countAlpha(bitmap, region) > 20,
                    "visiblePixels=${countAlpha(bitmap, region)}; region=${region.flattenToString()}")
                checkFrameBounds("calendar $value", bitmap)
                bitmap.recycle()
            }
        }
        record("calendar strings update native output", calendarRasters.size == 14,
            "distinct middle-row pixel arrays=${calendarRasters.size}; expected=14")
    }

    private fun frame(
        name: String, width: Int, height: Int, color: Int,
        regular: String = "4:49", personal: String = "13:13",
        period: String = "周四下午", date: String = "10/01",
        percent: String = "55.10%", units: Int = 5510, verifyBaseAlpha: Boolean = false,
    ): Bitmap {
        val started = SystemClock.elapsedRealtimeNanos()
        val bitmap = render(width, height, color, regular, personal, period, date, percent, units)
        val renderMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        val png = File(output, "$name.png")
        png.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        val whole = pixels(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
        frames.put(JSONObject()
            .put("name", name).put("path", png.absolutePath)
            .put("width", bitmap.width).put("height", bitmap.height).put("renderMilliseconds", renderMs)
            .put("sha256", sha256(png.readBytes()))
            .put("textColor", "#%08X".format(color))
            .put("regular", regular).put("personal", personal).put("period", period)
            .put("date", date).put("percent", percent).put("progressBasisPoints", units)
            .put("visibleBounds", visibleBounds(bitmap)?.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) })
            .put("maxAlpha", whole.maxOf { Color.alpha(it) })
            .put("transparentPixels", whole.count { Color.alpha(it) == 0 }))
        record("$name dimensions", bitmap.width == width && bitmap.height == height,
            "${bitmap.width}x${bitmap.height}")
        record("$name has transparent background", whole.any { Color.alpha(it) == 0 },
            "transparent pixels=${whole.count { Color.alpha(it) == 0 }}")
        checkFrameBounds(name, bitmap)
        for (column in listOf("left" to Rect(20, 25, 480, 440), "right" to Rect(585, 25, 1040, 440))) {
            val region = mapped(column.second, bitmap)
            record("$name ${column.first} column visible", countAlpha(bitmap, region) > 10,
                "visible pixels=${countAlpha(bitmap, region)}")
        }
        record("$name central gap transparent", countAlpha(bitmap, mapped(Rect(480, 25, 575, 440), bitmap)) == 0,
            "gap contains no panel or decoration")
        record("$name RGB retained", whole.filter { Color.alpha(it) >= 16 }.all {
            Color.red(it) == Color.red(color) && Color.green(it) == Color.green(color) && Color.blue(it) == Color.blue(color)
        }, "visible RGB matches requested black/white")
        if (verifyBaseAlpha) {
            val blankRegions = listOf(Rect(0, 0, 1080, 25), Rect(0, 440, 1080, 470),
                Rect(20, 160, 1040, 174), Rect(20, 304, 1040, 315))
            record("$name blank bands alpha zero", blankRegions.all { countAlpha(bitmap, it) == 0 },
                "No painted background above/below content or between text rows")
            val first = maxAlpha(bitmap, Rect(20, 25, 1040, 174))
            val lower = maxAlpha(bitmap, Rect(20, 174, 1040, 440))
            record("$name first-row opacity", first == 255, "maxAlpha=$first; expected=255")
            record("$name lower-row opacity", lower == 128, "maxAlpha=$lower; expected=128")
            record("$name lower-row ceiling", pixels(bitmap, Rect(20, 174, 1040, 440)).all { Color.alpha(it) <= 128 },
                "Chinese/date/progress/percentage do not exceed 50% alpha")
        }
        return bitmap
    }

    private fun render(
        width: Int, height: Int, color: Int, regular: String = "4:49", personal: String = "13:13",
        period: String = "周四下午", date: String = "10/01", percent: String = "55.10%", units: Int = 5510,
    ): Bitmap = ClockFaceRenderer.render(targetContext, width, height, regular, personal, period, date, percent, units, color)

    /** Uses real Android pixels and passed-in settings only. Nothing here writes preferences,
     * changes a bound widget, or changes the current refresh service. */
    private fun runDesignChecks() {
        val none = DesignSettings(timeOpacity = 0, middleOpacity = 0, bottomOpacity = 0)
        val regions = listOf(Rect(20, 25, 1070, 174), Rect(20, 174, 1070, 315), Rect(20, 315, 1070, 455))
        val fullyHidden = designFrame("design-all-hidden", none)
        record("design zero opacity hides all glyphs and progress", visibleBounds(fullyHidden) == null,
            "all ${fullyHidden.width * fullyHidden.height} pixels are transparent")
        fullyHidden.recycle()

        // Each row is enabled independently. This catches alpha accidentally applied to the
        // whole face, the wrong row, or applied twice at bar/tick intersections.
        for (row in 0..2) {
            var geometry: IntArray? = null
            for (opacity in listOf(50, 100)) {
                val selected = when (row) {
                    0 -> none.copy(timeOpacity = opacity)
                    1 -> none.copy(middleOpacity = opacity)
                    else -> none.copy(bottomOpacity = opacity)
                }
                val image = designFrame("design-row${row + 1}-alpha$opacity", selected)
                val region = regions[row]
                val expectedAlpha = if (opacity == 50) 128 else 255
                val values = pixels(image, region)
                record("design row ${row + 1} opacity $opacity reaches requested alpha",
                    values.maxOf { Color.alpha(it) } == expectedAlpha &&
                        values.all { Color.alpha(it) <= expectedAlpha }, "ceiling=$expectedAlpha")
                record("design row ${row + 1} opacity $opacity leaves other rows blank",
                    regions.filterIndexed { index, _ -> index != row }.all { countAlpha(image, it) == 0 },
                    "Only the selected row is painted")
                record("design row ${row + 1} opacity $opacity controls both columns",
                    countAlpha(image, Rect(region.left, region.top, 540, region.bottom)) > 20 &&
                        countAlpha(image, Rect(580, region.top, region.right, region.bottom)) > 20,
                    "Both requested row regions have native pixels")
                val currentGeometry = values.indices.filter { Color.alpha(values[it]) >= expectedAlpha / 2 }.toIntArray()
                if (geometry == null) geometry = currentGeometry else {
                    // Compositing rounds alpha by one byte; geometry above half coverage remains
                    // the same except for a small number of exact-threshold antialias pixels.
                    val a = geometry.toSet(); val b = currentGeometry.toSet()
                    val mismatch = (a - b).size + (b - a).size
                    record("design row ${row + 1} opacity preserves glyph geometry",
                        mismatch < maxOf(20, a.size / 50), "half-coverage mismatch=$mismatch; pixels=${a.size}")
                }
                image.recycle()
            }
        }
        runBackgroundChecks(none)
        runSizeChecks(none)
        runProgressThicknessChecks(none)
        runPreviewCacheChecks()
    }

    private fun runBackgroundChecks(none: DesignSettings) {
        val color = 0x4A7096
        val firstOnly = none.copy(timeOpacity = 100)
        val transparent = designFrame("design-background-0", firstOnly, color, 0)
        val half = designFrame("design-background-50", firstOnly, color, 50)
        val opaque = designFrame("design-background-100", firstOnly, color, 100)
        val blank = Rect(520, 25, 575, 440)
        record("design transparent background leaves central gap empty", countAlpha(transparent, blank) == 0,
            "0% background leaves the selected gap transparent")
        for ((label, bitmap, alpha) in listOf(Triple("50", half, 128), Triple("100", opaque, 255))) {
            val sample = pixels(bitmap, blank)
            record("design background $label has uniform requested RGB and alpha", sample.all {
                Color.alpha(it) == alpha && kotlin.math.abs(Color.red(it) - 0x4A) <= 1 &&
                    kotlin.math.abs(Color.green(it) - 0x70) <= 1 && kotlin.math.abs(Color.blue(it) - 0x96) <= 1
            }, "Central gap RGBA=#${alpha.toString(16)}4A7096 (RGB rounding tolerance 1); pixels=${sample.size}")
            record("design background $label retains rounded corners", Color.alpha(bitmap.getPixel(0, 0)) == 0 &&
                Color.alpha(bitmap.getPixel(16, 0)) == alpha, "Corner empty; straight top edge has background alpha")
            val originalPixels = pixels(transparent, Rect(0, 0, 1080, 470))
            val composedPixels = pixels(bitmap, Rect(0, 0, 1080, 470))
            val solidGlyphs = originalPixels.indices.filter { Color.alpha(originalPixels[it]) == 255 }
            record("design background $label does not fade opaque text", solidGlyphs.size > 100 && solidGlyphs.all {
                composedPixels[it] == Color.WHITE
            }, "${solidGlyphs.size} solid white time pixels remain white and opaque")
        }
        val hiddenText = designFrame("design-background-50-hidden-text", none, color, 50)
        record("design background remains when text opacity is zero",
            hiddenText.getPixel(600, 100) == half.getPixel(550, 100) && maxAlpha(hiddenText, Rect(20, 20, 1060, 450)) == 128,
            "Background has its own alpha, independent of all text rows")
        transparent.recycle(); half.recycle(); opaque.recycle(); hiddenText.recycle()
    }

    private fun runSizeChecks(none: DesignSettings) {
        val smallDesign = DesignSettings(timeScale = 60, middleScale = 60, bottomScale = 60)
        val largeDesign = DesignSettings(timeScale = 110, middleScale = 110, bottomScale = 110)
        val small = designFrame("design-all-size60", smallDesign, longest = true)
        val large = designFrame("design-all-size110", largeDesign, longest = true)
        val regionPairs = listOf(
            "time-left" to Rect(20, 25, 545, 174), "time-right" to Rect(585, 25, 1070, 174),
            "Chinese" to Rect(20, 174, 545, 315), "date" to Rect(20, 315, 545, 455),
            "percentage" to Rect(585, 315, 1070, 455))
        for ((label, region) in regionPairs) {
            val smallBounds = visibleBoundsIn(small, region)
            val largeBounds = visibleBoundsIn(large, region)
            val smallArea = alphaArea(small, region)
            val largeArea = alphaArea(large, region)
            record("design $label size changes native glyph dimensions", smallBounds != null && largeBounds != null &&
                largeBounds.width() > smallBounds.width() * 1.6 && largeBounds.height() > smallBounds.height() * 1.6,
                "60=${smallBounds?.flattenToString()}; 110=${largeBounds?.flattenToString()}")
            record("design $label size changes covered area", largeArea > smallArea * 2,
                "sum-alpha area: 60=$smallArea; 110=$largeArea")
            record("design $label maximum stays within assigned row and column", largeBounds != null &&
                largeBounds.left > region.left && largeBounds.top > region.top &&
                largeBounds.right < region.right && largeBounds.bottom < region.bottom,
                "visible=${largeBounds?.flattenToString()}; limit=${region.flattenToString()}")
        }
        record("design maximum size preserves column gap", countAlpha(large, Rect(545, 25, 585, 455)) == 0,
            "40 design-pixel gap stays transparent")
        checkFrameBounds("design maximum-size longest values", large)

        // Isolate each row at the upper limit, then check for occupied-pixel overlap.
        // This measures actual glyph extents, rather than assuming text-box heights.
        val rows = listOf(
            largeDesign.copy(middleOpacity = 0, bottomOpacity = 0),
            largeDesign.copy(timeOpacity = 0, bottomOpacity = 0),
            largeDesign.copy(timeOpacity = 0, middleOpacity = 0),
        ).map { d -> designRender(d, longest = true) }
        val masks = rows.map { pixels(it, Rect(0, 0, 1080, 470)) }
        val overlaps = masks[0].indices.count { i -> masks.count { Color.alpha(it[i]) > 0 } > 1 }
        record("design maximum-size rows do not overlap", overlaps == 0, "overlapping native pixels=$overlaps")
        rows.forEach { it.recycle() }
        small.recycle(); large.recycle()

        val calendarHashes = mutableSetOf<Int>()
        for (day in listOf("一", "二", "三", "四", "五", "六", "日")) for (period in listOf("上午", "下午")) {
            val text = "周$day$period"
            val bitmap = ClockFaceRenderer.render(targetContext, 1080, 470, "", "", text, "", "", 0, Color.WHITE,
                none.copy(middleScale = 110, middleOpacity = 100))
            val area = Rect(20, 174, 545, 315)
            val bounds = visibleBoundsIn(bitmap, area)
            calendarHashes += pixels(bitmap, area).contentHashCode()
            record("design calendar maximum $text fits", bounds != null && bounds.width() > 400 && bounds.height() > 100 &&
                bounds.left > area.left && bounds.top > area.top && bounds.right < area.right && bounds.bottom < area.bottom,
                "native glyph bounds=${bounds?.flattenToString()}; limits=${area.flattenToString()}")
            bitmap.recycle()
        }
        record("design all 14 calendar strings remain distinct at maximum", calendarHashes.size == 14,
            "distinct raster hashes=${calendarHashes.size}")
    }

    private fun runProgressThicknessChecks(none: DesignSettings) {
        val thin = designFrame("design-progress-thickness50", none.copy(middleOpacity = 100, progressThickness = 50))
        val thick = designFrame("design-progress-thickness200", none.copy(middleOpacity = 100, progressThickness = 200))
        // x=645 lies inside the filled section and clear of ticks/endpoints.
        val column = Rect(645, 200, 646, 275)
        // The 50% strip has a half-pixel tick offset. Sum coverage rather than
        // counting fully opaque pixels, which would discard valid antialias edges.
        val thinRows = alphaArea(thin, column)
        val thickRows = alphaArea(thick, column)
        record("design progress thickness changes actual filled height",
            kotlin.math.abs(thinRows - 2.0) < .1 && kotlin.math.abs(thickRows - 8.0) < .1,
            "alpha-weighted column pixels: 50%=$thinRows; 200%=$thickRows")
        val thinBounds = visibleBoundsIn(thin, Rect(585, 200, 1040, 275))
        val thickBounds = visibleBoundsIn(thick, Rect(585, 200, 1040, 275))
        record("design progress thickness preserves width and extends ticks", thinBounds != null && thickBounds != null &&
            thinBounds.left == thickBounds.left && thinBounds.right == thickBounds.right &&
            thickBounds.height() > thinBounds.height() * 3,
            "50=${thinBounds?.flattenToString()}; 200=${thickBounds?.flattenToString()}")
        record("design progress thick crossings never exceed row opacity",
            maxAlpha(thick, Rect(585, 200, 1040, 275)) == 255, "100% row alpha remains the composition ceiling")
        thin.recycle(); thick.recycle()
    }

    private fun runPreviewCacheChecks() {
        val now = Instant.parse("2026-10-01T08:49:00Z")
        val base = Preferences(ink = InkMode.WHITE)
        fun preview(p: Preferences): Bitmap {
            var result: Bitmap? = null
            runOnMainSync {
                val view = WidgetRenderer.preview(targetContext, p, now, 320, 140)
                    .apply(targetContext, FrameLayout(targetContext))
                val drawable = view.findViewById<ImageView>(R.id.widget_image).drawable as BitmapDrawable
                result = drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            return checkNotNull(result)
        }
        val original = preview(base)
        val baselinePixels = pixels(original, Rect(0, 0, original.width, original.height))
        val variants = listOf(
            "time opacity" to base.copy(design = base.design.copy(timeOpacity = 0)),
            "middle opacity" to base.copy(design = base.design.copy(middleOpacity = 0)),
            "bottom opacity" to base.copy(design = base.design.copy(bottomOpacity = 0)),
            "time size" to base.copy(design = base.design.copy(timeScale = 60)),
            "middle size" to base.copy(design = base.design.copy(middleScale = 60)),
            "bottom size" to base.copy(design = base.design.copy(bottomScale = 60)),
            "progress thickness" to base.copy(design = base.design.copy(progressThickness = 200)),
            "background opacity" to base.copy(opacity = 50),
            "background color" to base.copy(opacity = 50, backgroundRgb = 0x4070A0),
            "text color" to base.copy(ink = InkMode.CUSTOM, customInkRgb = 0x44AAEE),
        )
        var previous = baselinePixels
        for ((name, preferences) in variants) {
            val bitmap = preview(preferences)
            val actual = pixels(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
            val differences = actual.indices.count { actual[it] != baselinePixels[it] }
            record("preview cache updates $name with unchanged clock content",
                differences > 5 && !actual.contentEquals(previous), "changed pixels from default=$differences")
            when (name) {
                "time opacity" -> record("preview passes first-row opacity to renderer",
                    countAlpha(bitmap, mapped(Rect(20, 25, 1070, 174), bitmap)) == 0,
                    "First row has no visible native pixels")
                "background opacity", "background color" -> record("preview passes $name to renderer",
                    maxAlpha(bitmap, mapped(Rect(535, 25, 575, 440), bitmap)) == 128,
                    "Background gap has requested 50% alpha")
            }
            previous = actual
            bitmap.recycle()
        }
        val restored = preview(base)
        record("preview cache restores exact default output", baselinePixels.contentEquals(
            pixels(restored, Rect(0, 0, restored.width, restored.height))), "Same time and settings restore identical pixel array")
        original.recycle(); restored.recycle()
    }

    private fun designRender(design: DesignSettings, backgroundRgb: Int = 0xD8D8D8,
                             backgroundOpacity: Int = 0, longest: Boolean = false): Bitmap =
        ClockFaceRenderer.render(targetContext, 1080, 470,
            if (longest) "23:59" else "4:49", if (longest) "24:00" else "13:13", "周四下午", "10/01",
            if (longest) "100.00%" else "55.10%", if (longest) 10000 else 5510, Color.WHITE,
            design, backgroundRgb, backgroundOpacity)

    private fun designFrame(name: String, design: DesignSettings, backgroundRgb: Int = 0xD8D8D8,
                            backgroundOpacity: Int = 0, longest: Boolean = false): Bitmap {
        val bitmap = designRender(design, backgroundRgb, backgroundOpacity, longest)
        val png = File(output, "$name.png")
        png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        frames.put(JSONObject().put("name", name).put("path", png.absolutePath)
            .put("sha256", sha256(png.readBytes())).put("design", design.toString())
            .put("backgroundRgb", "#%06X".format(backgroundRgb)).put("backgroundOpacity", backgroundOpacity)
            .put("visibleBounds", visibleBounds(bitmap)?.flattenToString()))
        return bitmap
    }

    private fun alphaArea(bitmap: Bitmap, region: Rect): Double = pixels(bitmap, region).sumOf { Color.alpha(it).toLong() } / 255.0

    private fun visibleBoundsIn(bitmap: Bitmap, rect: Rect): Rect? {
        val region = Rect(rect)
        if (!region.intersect(0, 0, bitmap.width, bitmap.height)) return null
        val sample = pixels(bitmap, region)
        var left = region.width(); var top = region.height(); var right = -1; var bottom = -1
        for (i in sample.indices) if (Color.alpha(sample[i]) != 0) {
            val x = i % region.width(); val y = i / region.width()
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        return if (right < 0) null else Rect(region.left + left, region.top + top, region.left + right + 1, region.top + bottom + 1)
    }

    private fun checkFrameBounds(name: String, bitmap: Bitmap) {
        val bounds = visibleBounds(bitmap)
        record("$name not blank", bounds != null, bounds?.flattenToString() ?: "no visible pixels")
        record("$name outer edges transparent", bounds != null && bounds.left > 0 && bounds.top > 0 &&
            bounds.right < bitmap.width && bounds.bottom < bitmap.height, bounds?.flattenToString() ?: "empty")
    }

    private fun runFonts() {
        // Product loading uses TextRunShaper on these same cached Typefaces and rejects
        // missing glyphs, wrong TTC indices, unexpected font buffers, or wrong weights.
        // No test-made Typeface can silently stand in for the one that rendered the PNGs.
        val audits = ClockFaceRenderer.fontDiagnostics(targetContext)
        record("three native typefaces audited", audits.size == 3, "count=${audits.size}")
        record("all product font roles audited", audits.map { "${it.font}:${it.requestedWeight}" }.toSet() ==
            setOf("ROBOTO:190", "YAHEI_LIGHT:290", "ROBOTO:130"),
            "actual=${audits.map { "${it.font}:${it.requestedWeight}" }}")
        for (audit in audits) {
            val label = "${audit.font}-${audit.requestedWeight}"
            val resource = if (audit.font == ClockTypography.FontFace.ROBOTO.name) R.font.clock_roboto else R.font.clock_yahei_light
            val expectedHash = targetContext.resources.openRawResource(resource).use { sha256(it.readBytes()) }
            val expectedWeight = if (audit.font == ClockTypography.FontFace.YAHEI_LIGHT.name) 290
                else if (audit.requestedWeight == 190) 190 else 130
            val axes = audit.axes.mapNotNull {
                val pair = it.split('=', limit = 2)
                pair.getOrNull(1)?.toFloatOrNull()?.let { value -> pair[0] to value }
            }.toMap()
            fontRuns.put(JSONObject().put("font", audit.font)
                .put("requestedWeight", audit.requestedWeight).put("actualTypefaceWeight", audit.actualTypefaceWeight)
                .put("resource", targetContext.resources.getResourceName(resource))
                .put("resourceSha256", audit.resourceSha256).put("expectedResourceSha256", expectedHash)
                .put("actualFontBufferSha256", JSONArray(audit.actualBufferSha256.toList()))
                .put("actualWeights", JSONArray(audit.actualWeights.toList()))
                .put("axes", JSONArray(audit.axes.toList())).put("verified", audit.verified).put("note", audit.note))
            record("$label native shaping verified", audit.verified, audit.note)
            record("$label uses bundled font", audit.resourceSha256 == expectedHash && audit.actualBufferSha256 == setOf(expectedHash),
                "actual=${audit.actualBufferSha256}; expected=$expectedHash")
            record("$label actual font weight", audit.actualTypefaceWeight == expectedWeight && audit.actualWeights == setOf(expectedWeight),
                "typeface=${audit.actualTypefaceWeight}; shaped=${audit.actualWeights}; expected=$expectedWeight")
            if (audit.font == ClockTypography.FontFace.ROBOTO.name) {
                record("$label variable axes", axes["wght"] == expectedWeight.toFloat() &&
                    (axes["wdth"] ?: 100f) == 100f && (axes["ital"] ?: 0f) == 0f,
                    "actual=${audit.axes}; expected wght=$expectedWeight, wdth=100, ital=0")
            }
        }
    }

    private fun mapped(base: Rect, bitmap: Bitmap): Rect {
        val scale = min(bitmap.width / 1080f, bitmap.height / 470f)
        val x = (bitmap.width - 1080f * scale) / 2f
        val y = (bitmap.height - 470f * scale) / 2f
        return Rect(floor(x + base.left * scale).toInt(), floor(y + base.top * scale).toInt(),
            ceil(x + base.right * scale).toInt(), ceil(y + base.bottom * scale).toInt())
    }

    private fun pixels(bitmap: Bitmap, rect: Rect): IntArray {
        val clipped = Rect(rect)
        if (!clipped.intersect(0, 0, bitmap.width, bitmap.height)) return IntArray(0)
        return IntArray(clipped.width() * clipped.height()).also {
            bitmap.getPixels(it, 0, clipped.width(), clipped.left, clipped.top, clipped.width(), clipped.height())
        }
    }

    private fun maxAlpha(bitmap: Bitmap, rect: Rect) = pixels(bitmap, rect).maxOfOrNull { Color.alpha(it) } ?: 0
    private fun countAlpha(bitmap: Bitmap, rect: Rect) = pixels(bitmap, rect).count { Color.alpha(it) != 0 }
    private fun visibleBounds(bitmap: Bitmap): Rect? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        val pixels = pixels(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
        for (i in pixels.indices) if (Color.alpha(pixels[i]) != 0) {
            val x = i % bitmap.width
            val y = i / bitmap.width
            left = minOf(left, x); top = minOf(top, y)
            right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        return if (right >= 0) Rect(left, top, right + 1, bottom + 1) else null
    }

    private fun record(name: String, passed: Boolean, detail: String) {
        checks.put(JSONObject().put("name", name).put("passed", passed).put("detail", detail))
        if (!passed) failures += "$name: $detail"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
