import CoreGraphics

/// 워치 메인 화면 배치 (스펙 §4.1·§4.2). 링, 링 안 다섯 줄, 링 아래 버튼이 서로 겹치지 않는 칸을 받는다.
/// 각 줄의 폭은 그 줄 높이 범위에서 가장 좁은 링 안쪽 현(chord)이라 네 모서리가 모두 링 안에 든다.
public struct MainLayout: Equatable, Sendable {
    public var ringCenter: CGPoint
    /// 링 선 가운데까지의 반지름
    public var ringRadius: CGFloat
    public var lineWidth: CGFloat
    /// 위에서부터 상단 줄, 큰 시계, 추가시간, 점수, 상태 줄
    public var rows: [CGRect]
    public var buttons: CGRect
    public var clockFontSize: CGFloat

    /// Always-On(reduced)에서도 보이는 줄인지. 시계·추가시간·점수는 남기고 상단 줄·상태 줄은 숨긴다.
    /// 추가시간 중에는 큰 시계가 규정 시간에서 멈춰 있으므로 +M:SS 줄이 있어야 한다.
    public static func showsRow(_ index: Int, reduced: Bool) -> Bool {
        !reduced || (1...3).contains(index)
    }

    /// 링 안쪽 지름에 대한 각 줄 높이 비율
    static let rowShares: [CGFloat] = [0.12, 0.28, 0.11, 0.13, 0.12]

    public init(size: CGSize) {
        let width = max(0, size.width)
        let height = max(0, size.height)
        lineWidth = min(max(width * 0.037, 5), 7.5)
        let buttonHeight = min(max(height * 0.135, 24), 34)
        let top = height * 0.02
        let gap = height * 0.03
        let bottom = height * 0.035
        let diameter = max(0, min(width * 0.94, height - top - gap - buttonHeight - bottom))
        ringCenter = CGPoint(x: width / 2, y: top + diameter / 2)
        ringRadius = max(0, diameter / 2 - lineWidth / 2)
        let inner = max(0, ringRadius - lineWidth / 2 - width * 0.02)

        let total = Self.rowShares.reduce(0, +) * inner * 2
        var y = ringCenter.y - total / 2
        var rows: [CGRect] = []
        for share in Self.rowShares {
            let rowHeight = share * inner * 2
            let far = max(abs(y - ringCenter.y), abs(y + rowHeight - ringCenter.y))
            let half = max(0, inner * inner - far * far).squareRoot()
            rows.append(CGRect(x: ringCenter.x - half, y: y, width: half * 2, height: rowHeight))
            y += rowHeight
        }
        self.rows = rows
        clockFontSize = rows[1].height * 0.95

        let inset = width * 0.14
        buttons = CGRect(x: inset, y: top + diameter + gap, width: max(0, width - inset * 2), height: buttonHeight)
    }
}
