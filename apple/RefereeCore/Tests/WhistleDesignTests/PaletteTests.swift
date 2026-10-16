import Testing
@testable import WhistleDesign

struct PaletteTests {
    @Test func hexChannels() {
        let c = RGB(hex: 0xC6FF3D)
        #expect(abs(c.red - 198.0 / 255) < 1e-9)
        #expect(c.green == 1)
        #expect(abs(c.blue - 61.0 / 255) < 1e-9)
    }

    @Test func blackOnWhiteIs21To1() {
        #expect(abs(RGB.contrast(RGB(hex: 0x000000), RGB(hex: 0xFFFFFF)) - 21) < 0.01)
        #expect(abs(RGB.contrast(RGB(hex: 0xFFFFFF), RGB(hex: 0x000000)) - 21) < 0.01)
    }

    /// 스펙 §3.1: 의미 색 글자는 배경·카드 위에서 4.5:1 이상
    @Test func foregroundsReadableOnDarkBackgrounds() {
        for swatch in Palette.foregrounds {
            #expect(RGB.contrast(swatch.dark, Palette.background.dark) >= 4.5, "\(swatch.dark)")
            #expect(RGB.contrast(swatch.dark, Palette.surface.dark) >= 4.5, "\(swatch.dark)")
        }
    }

    @Test func foregroundsReadableOnLightBackgrounds() {
        for swatch in Palette.foregrounds {
            #expect(RGB.contrast(swatch.light, Palette.background.light) >= 4.5, "\(swatch.light)")
            #expect(RGB.contrast(swatch.light, Palette.surface.light) >= 4.5, "\(swatch.light)")
        }
    }

    /// 보조 버튼 바탕(surfaceRaised) 위 글자·아이콘: 버튼 글자는 굵은 큰 글자라 3:1 기준 (UI 리뷰: 이 바탕이 검사에서 빠져 있었다)
    @Test func foregroundsReadableOnSecondaryButtons() {
        for swatch in Palette.foregrounds {
            #expect(RGB.contrast(swatch.dark, Palette.surfaceRaised.dark) >= 3, "\(swatch.dark)")
            #expect(RGB.contrast(swatch.light, Palette.surfaceRaised.light) >= 3, "\(swatch.light)")
        }
    }

    @Test func textOnAccentButtonReadable() {
        #expect(RGB.contrast(Palette.onAccent.dark, Palette.accent.dark) >= 4.5)
        #expect(RGB.contrast(Palette.onAccent.light, Palette.accent.light) >= 4.5)
    }

    @Test func mixInterpolatesAndClamps() {
        let black = RGB(hex: 0x000000)
        let white = RGB(hex: 0xFFFFFF)
        #expect(RGB.mix(black, white, 0.5) == RGB(red: 0.5, green: 0.5, blue: 0.5))
        #expect(RGB.mix(black, white, 2) == white)
        #expect(RGB.mix(black, white, -1) == black)
    }
}
