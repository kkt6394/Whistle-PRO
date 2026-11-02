import SwiftUI

/// 앱 아이콘과 같은 그림: 카드 차콜 바탕(`Palette.surface`, 2026-11-03 아이콘과 같은 값), 레드카드(뒤, -12°)와 라임 카드(앞, +10°).
/// 결제 화면 머리에 쓴다. `background: false`면 바탕·둥근 네모 없이 카드 두 장만 — 경기 목록 머리글의 워드마크 옆 마크(2026-11-03)
public struct BrandMark: View {
    public var background: Bool

    public init(background: Bool = true) {
        self.background = background
    }

    public var body: some View {
        GeometryReader { proxy in
            let s = min(proxy.size.width, proxy.size.height) / 100
            ZStack {
                if background { Palette.surface.dark.color }
                card(x: 14, y: 12, angle: -12, color: Palette.danger.dark.color, scale: s)
                card(x: 38, y: 22, angle: 10, color: Palette.accent.dark.color, scale: s)
            }
            .frame(width: s * 100, height: s * 100)
            .clipShape(RoundedRectangle(cornerRadius: background ? s * 22.5 : 0, style: .continuous))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    /// 44×64, 모서리 7 카드를 (x, y)에 놓고 가운데를 축으로 돌린다
    private func card(x: CGFloat, y: CGFloat, angle: Double, color: Color, scale s: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 7 * s, style: .continuous)
            .fill(color)
            .frame(width: 44 * s, height: 64 * s)
            .rotationEffect(.degrees(angle))
            .position(x: (x + 22) * s, y: (y + 32) * s)
    }
}
