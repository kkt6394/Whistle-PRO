import SwiftUI

/// 앱 아이콘과 같은 그림: 잔디 바탕(2026-11-08 아이콘 — 왼쪽 위 밝은 녹색 → 오른쪽 아래 짙은 녹색, 세로 줄무늬), 레드카드(뒤, -12°)와 라임 카드(앞, +10°).
/// 바탕 색은 `scripts/make_phone_icon.py`가 만든 AppIcon.png 모서리에서 뽑은 값.
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
                if background { Self.grass }
                card(x: 14, y: 12, angle: -12, color: Palette.danger.dark.color, scale: s)
                card(x: 38, y: 22, angle: 10, color: Palette.accent.dark.color, scale: s)
            }
            .frame(width: s * 100, height: s * 100)
            .clipShape(RoundedRectangle(cornerRadius: background ? s * 22.5 : 0, style: .continuous))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    /// 잔디: 대각선 그라데이션 + 일곱 줄 세로 줄무늬(번갈아 조금 밝게)
    private static var grass: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0x3F / 255, green: 0x95 / 255, blue: 0x57 / 255),
                                    Color(red: 0x18 / 255, green: 0x56 / 255, blue: 0x2E / 255)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { i in
                    Color.white.opacity(i.isMultiple(of: 2) ? 0.05 : 0)
                }
            }
        }
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
