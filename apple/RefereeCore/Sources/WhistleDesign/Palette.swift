import SwiftUI
#if os(iOS)
import UIKit
#endif

/// sRGB 색 (각 채널 0...1). 대비는 WCAG 2.x 상대 휘도 공식으로 계산한다.
public struct RGB: Equatable, Sendable {
    public var red: Double
    public var green: Double
    public var blue: Double

    public init(red: Double, green: Double, blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    /// 0xRRGGBB
    public init(hex: UInt32) {
        red = Double((hex >> 16) & 0xFF) / 255
        green = Double((hex >> 8) & 0xFF) / 255
        blue = Double(hex & 0xFF) / 255
    }

    public var luminance: Double {
        func linear(_ c: Double) -> Double {
            c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /// 대비 비율 (1...21)
    public static func contrast(_ a: RGB, _ b: RGB) -> Double {
        let high = max(a.luminance, b.luminance)
        let low = min(a.luminance, b.luminance)
        return (high + 0.05) / (low + 0.05)
    }

    /// a에서 b 쪽으로 t(0...1, 범위 밖은 잘라냄)만큼 섞는다
    public static func mix(_ a: RGB, _ b: RGB, _ t: Double) -> RGB {
        let t = min(max(t, 0), 1)
        return RGB(
            red: a.red + (b.red - a.red) * t,
            green: a.green + (b.green - a.green) * t,
            blue: a.blue + (b.blue - a.blue) * t)
    }

    public var color: Color {
        Color(.sRGB, red: red, green: green, blue: blue)
    }
}

/// 다크(워치, 폰 다크 모드)와 라이트(폰 라이트 모드) 값 한 쌍
public struct Swatch: Equatable, Sendable {
    public var dark: RGB
    public var light: RGB

    public init(dark: UInt32, light: UInt32) {
        self.dark = RGB(hex: dark)
        self.light = RGB(hex: light)
    }

    /// iOS는 라이트·다크에 따라 바뀌는 색, 워치(항상 다크)와 macOS는 다크 값
    public var color: Color {
        #if os(iOS)
        let dark = self.dark
        let light = self.light
        return Color(uiColor: UIColor { traits in
            let c = traits.userInterfaceStyle == .light ? light : dark
            return UIColor(red: c.red, green: c.green, blue: c.blue, alpha: 1)
        })
        #else
        return dark.color
        #endif
    }
}

/// 스펙 §3.1 색 토큰. 앱의 모든 색은 여기서 온다 (팀 색 제외).
public enum Palette {
    public static let accent = Swatch(dark: 0xC6FF3D, light: 0x4C7A00)
    /// accent 바탕 위 글자
    public static let onAccent = Swatch(dark: 0x000000, light: 0xFFFFFF)
    public static let added = Swatch(dark: 0xFF9F0A, light: 0xC93400)
    public static let rest = Swatch(dark: 0x64D2FF, light: 0x0071A4)
    public static let danger = Swatch(dark: 0xFF453A, light: 0xD70015)
    public static let caution = Swatch(dark: 0xFFD60A, light: 0x7A6000)
    public static let background = Swatch(dark: 0x000000, light: 0xF2F2F7)
    public static let surface = Swatch(dark: 0x1C1C1E, light: 0xFFFFFF)
    public static let surfaceRaised = Swatch(dark: 0x2C2C2E, light: 0xE5E5EA)
    public static let textPrimary = Swatch(dark: 0xFFFFFF, light: 0x000000)
    public static let textSecondary = Swatch(dark: 0x8E8E93, light: 0x6C6C70)

    /// 배경·카드 위에 글자·아이콘으로 쓰는 색 (대비 검사 대상)
    public static let foregrounds: [Swatch] = [accent, added, rest, danger, caution, textPrimary, textSecondary]
}

/// 화면에서 쓰는 SwiftUI 색
public enum Theme {
    public static let accent = Palette.accent.color
    public static let onAccent = Palette.onAccent.color
    public static let added = Palette.added.color
    public static let rest = Palette.rest.color
    public static let danger = Palette.danger.color
    public static let caution = Palette.caution.color
    public static let background = Palette.background.color
    public static let surface = Palette.surface.color
    public static let surfaceRaised = Palette.surfaceRaised.color
    public static let textPrimary = Palette.textPrimary.color
    public static let textSecondary = Palette.textSecondary.color
}
