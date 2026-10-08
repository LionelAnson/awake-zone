package com.personalday.android

import com.personalday.core.InkMode

object WidgetStyle {
    fun ink(mode: InkMode, supportsDarkText: Boolean?, customRgb: Int = 0x101010): Int = when (mode) {
        InkMode.BLACK -> 0xff101010.toInt()
        InkMode.WHITE -> 0xfffafafa.toInt()
        InkMode.AUTO -> if (supportsDarkText == true) 0xff101010.toInt() else 0xfffafafa.toInt()
        InkMode.CUSTOM -> 0xff000000.toInt() or (customRgb and 0xFFFFFF)
    }
    fun veil(opacity: Int, rgb: Int = 0xD8D8D8): Int =
        ((opacity.coerceIn(0, 100) * 255 + 50) / 100 shl 24) or (rgb and 0xFFFFFF)
    fun parseRgb(text: String): Int? = text.trim().removePrefix("#")
        .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toIntOrNull(16)
    fun hex(rgb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", rgb and 0xFFFFFF)
}
