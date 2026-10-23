package com.aidenstudio.whistlepro.session.design

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteTests {
    @Test fun hexChannels() {
        val c = RGB.hex(0xC6FF3D)
        assertTrue(abs(c.red - 198.0 / 255) < 1e-9)
        assertEquals(1.0, c.green)
        assertTrue(abs(c.blue - 61.0 / 255) < 1e-9)
        assertEquals(0xC6FF3D, c.hex)
    }

    @Test fun blackOnWhiteIs21To1() {
        assertTrue(abs(RGB.contrast(RGB.hex(0x000000), RGB.hex(0xFFFFFF)) - 21) < 0.01)
        assertTrue(abs(RGB.contrast(RGB.hex(0xFFFFFF), RGB.hex(0x000000)) - 21) < 0.01)
    }

    /** 스펙 §3.1: 의미 색 글자는 배경·카드 위에서 4.5:1 이상 */
    @Test fun foregroundsReadableOnDarkBackgrounds() {
        for (swatch in Palette.foregrounds) {
            assertTrue(RGB.contrast(swatch.dark, Palette.background.dark) >= 4.5, "${swatch.dark}")
            assertTrue(RGB.contrast(swatch.dark, Palette.surface.dark) >= 4.5, "${swatch.dark}")
        }
    }

    @Test fun foregroundsReadableOnLightBackgrounds() {
        for (swatch in Palette.foregrounds) {
            assertTrue(RGB.contrast(swatch.light, Palette.background.light) >= 4.5, "${swatch.light}")
            assertTrue(RGB.contrast(swatch.light, Palette.surface.light) >= 4.5, "${swatch.light}")
        }
    }

    @Test fun textOnAccentButtonReadable() {
        assertTrue(RGB.contrast(Palette.onAccent.dark, Palette.accent.dark) >= 4.5)
        assertTrue(RGB.contrast(Palette.onAccent.light, Palette.accent.light) >= 4.5)
    }

    @Test fun mixInterpolatesAndClamps() {
        val black = RGB.hex(0x000000)
        val white = RGB.hex(0xFFFFFF)
        assertEquals(RGB(0.5, 0.5, 0.5), RGB.mix(black, white, 0.5))
        assertEquals(white, RGB.mix(black, white, 2.0))
        assertEquals(black, RGB.mix(black, white, -1.0))
    }

    /** 보고서 흰 종이: 흰색·노랑 견본은 테두리가 필요하고, 빨강·파랑·검정·남색은 필요 없다 */
    @Test fun paperOutlineForLightTeamColors() {
        for (hex in listOf(0xFFFFFF, 0xFFCC00)) assertTrue(PaperMark.needsOutline(RGB.hex(hex)), "%06X".format(hex))
        for (hex in listOf(0xFF3B30, 0x007AFF, 0x000000, 0x1C2B5A)) assertTrue(!PaperMark.needsOutline(RGB.hex(hex)), "%06X".format(hex))
    }
}
