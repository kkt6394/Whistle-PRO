import SwiftUI

/// 앱 아이콘과 같은 그림: 검정 바탕, 레드카드(뒤, -12°)와 라임 카드(앞, +10°). 결제 화면 머리에 쓴다.
public struct BrandMark: View {
    public init() {}

    public var body: some View {
        GeometryReader { proxy in
            let s = min(proxy.size.width, proxy.size.height) / 100
            ZStack {
                Color.black
                card(x: 14, y: 12, angle: -12, color: Palette.danger.dark.color, scale: s)
                card(x: 38, y: 22, angle: 10, color: Palette.accent.dark.color, scale: s)
            }
            .frame(width: s * 100, height: s * 100)
            .clipShape(RoundedRectangle(cornerRadius: s * 22.5, style: .continuous))
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
