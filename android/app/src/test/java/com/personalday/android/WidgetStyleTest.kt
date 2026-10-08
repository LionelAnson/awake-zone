package com.personalday.android

import com.personalday.core.InkMode
import org.junit.Assert.*
import org.junit.Test

class WidgetStyleTest {
    @Test fun defaultsTo18PercentGray() { assertEquals(0x2ed8d8d8, WidgetStyle.veil(18)) }
    @Test fun opacityLimitsDoNotAlterRgb() {
        assertEquals(0x00d8d8d8, WidgetStyle.veil(0)); assertEquals(0xffd8d8d8.toInt(), WidgetStyle.veil(100))
    }
    @Test fun wallpaperHintAndManualOverride() {
        assertEquals(WidgetStyle.ink(InkMode.BLACK, null), WidgetStyle.ink(InkMode.AUTO, true))
        assertEquals(WidgetStyle.ink(InkMode.WHITE, null), WidgetStyle.ink(InkMode.AUTO, false))
        assertEquals(WidgetStyle.ink(InkMode.WHITE, null), WidgetStyle.ink(InkMode.AUTO, null))
        assertEquals(WidgetStyle.ink(InkMode.BLACK, true), WidgetStyle.ink(InkMode.BLACK, false))
    }
    @Test fun customRgbIsIndependentOfBackgroundOpacity() {
        assertEquals(0x00123456, WidgetStyle.veil(0, 0x123456))
        assertEquals(0x80123456.toInt(), WidgetStyle.veil(50, 0x123456))
        assertEquals(0xffabcdef.toInt(), WidgetStyle.ink(InkMode.CUSTOM, false, 0xABCDEF))
    }
    @Test fun validatesSixDigitColors() {
        assertEquals(0xABCDEF, WidgetStyle.parseRgb(" #abcdef "))
        assertEquals(0x123456, WidgetStyle.parseRgb("123456"))
        for (bad in listOf("", "#fff", "#AA112233", "ZZ0000", "#12345", "##123456")) assertNull(WidgetStyle.parseRgb(bad))
        assertEquals("#001234", WidgetStyle.hex(0x1234))
    }
}
