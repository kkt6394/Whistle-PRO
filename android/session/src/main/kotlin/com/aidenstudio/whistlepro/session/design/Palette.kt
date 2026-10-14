package com.aidenstudio.whistlepro.session.design

import kotlin.math.pow

/** sRGB 색 (각 채널 0..1). 대비는 WCAG 2.x 상대 휘도 공식으로 계산한다. Compose Color 변환은 앱에서 한다. */
data class RGB(val red: Double, val green: Double, val blue: Double) {
    val luminance: Double
        get() {
            fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        }

    /** 0xRRGGBB */
    val hex: Int
        get() = (Math.round(red * 255).toInt() shl 16) or (Math.round(green * 255).toInt() shl 8) or Math.round(blue * 255).toInt()

    companion object {
        /** 0xRRGGBB */
        fun hex(value: Int) = RGB(
            ((value shr 16) and 0xFF) / 255.0,
            ((value shr 8) and 0xFF) / 255.0,
            (value and 0xFF) / 255.0,
        )

        /** 대비 비율 (1..21) */
        fun contrast(a: RGB, b: RGB): Double {
            val high = maxOf(a.luminance, b.luminance)
            val low = minOf(a.luminance, b.luminance)
            return (high + 0.05) / (low + 0.05)
        }

        /** a에서 b 쪽으로 t(0..1, 범위 밖은 잘라냄)만큼 섞는다 */
        fun mix(a: RGB, b: RGB, t: Double): RGB {
            val k = t.coerceIn(0.0, 1.0)
            return RGB(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k)
        }
    }
}

/** 다크(워치, 폰 다크 모드)와 라이트(폰 라이트 모드) 값 한 쌍 */
data class Swatch(val dark: RGB, val light: RGB) {
    constructor(dark: Int, light: Int) : this(RGB.hex(dark), RGB.hex(light))
}

/** 스펙 §3.1 색 토큰. 앱의 모든 색은 여기서 온다 (팀 색 제외). Swift Palette와 같은 값. */
object Palette {
    val accent = Swatch(0xC6FF3D, 0x4C7A00)
    /** accent 바탕 위 글자 */
    val onAccent = Swatch(0x000000, 0xFFFFFF)
    val added = Swatch(0xFF9F0A, 0xC93400)
    val rest = Swatch(0x64D2FF, 0x0071A4)
    val danger = Swatch(0xFF453A, 0xD70015)
    val caution = Swatch(0xFFD60A, 0x7A6000)
    val background = Swatch(0x000000, 0xF2F2F7)
    val surface = Swatch(0x1C1C1E, 0xFFFFFF)
    val surfaceRaised = Swatch(0x2C2C2E, 0xE5E5EA)
    val textPrimary = Swatch(0xFFFFFF, 0x000000)
    val textSecondary = Swatch(0x8E8E93, 0x6C6C70)

    /** 배경·카드 위에 글자·아이콘으로 쓰는 색 (대비 검사 대상) */
    val foregrounds = listOf(accent, added, rest, danger, caution, textPrimary, textSecondary)
}
