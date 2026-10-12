import SwiftUI

/// 캡슐 버튼 (스펙 §3.3). primary는 accent 바탕, secondary는 surfaceRaised 바탕.
public struct PillButtonStyle: ButtonStyle {
    public enum Kind: Sendable {
        case primary, secondary
    }

    public var kind: Kind
    public var height: CGFloat

    public init(_ kind: Kind, height: CGFloat = 36) {
        self.kind = kind
        self.height = height
    }

    public func makeBody(configuration: Configuration) -> some View {
        PillBody(configuration: configuration, kind: kind, height: height)
    }
}

private struct PillBody: View {
    let configuration: ButtonStyleConfiguration
    let kind: PillButtonStyle.Kind
    let height: CGFloat
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        configuration.label
            .font(.system(size: min(height * 0.42, 17), weight: .heavy, design: .rounded))
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .foregroundStyle(kind == .primary ? Theme.onAccent : Theme.textPrimary)
            .padding(.horizontal, height * 0.3)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Capsule().fill(kind == .primary ? Theme.accent : Theme.surfaceRaised))
            .contentShape(Capsule())
            .opacity(isEnabled ? (configuration.isPressed ? 0.7 : 1) : 0.4)
    }
}

extension View {
    /// surface 바탕의 둥근 카드 행 (스펙 §3.3)
    public func rowCard(cornerRadius: CGFloat = 10) -> some View {
        padding(.horizontal, 10)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(Theme.surface))
    }

    /// MainLayout이 정한 칸에 한 줄로 넣는다. 넘치면 글자를 줄이고, 그래도 넘치면 잘린다.
    /// 칸 밖으로는 절대 그려지지 않는다 (스펙 §4.2 겹침 금지).
    public func placed(in rect: CGRect) -> some View {
        lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(width: rect.width, height: rect.height)
            .clipped()
            .position(x: rect.midX, y: rect.midY)
    }
}
