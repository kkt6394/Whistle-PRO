import SwiftUI

/// 열린 링 위의 한 구간. from·to는 링 전체(0...1) 기준 위치.
public struct RingArc: Shape {
    public var from: Double
    public var to: Double

    public init(from: Double, to: Double) {
        self.from = from
        self.to = to
    }

    public func path(in rect: CGRect) -> Path {
        var path = Path()
        guard to > from else { return path }
        path.addArc(
            center: CGPoint(x: rect.midX, y: rect.midY),
            radius: min(rect.width, rect.height) / 2,
            startAngle: .degrees(RingGeometry.angle(at: from)),
            endAngle: .degrees(RingGeometry.angle(at: to)),
            clockwise: false)
        return path
    }
}

/// 아래가 열린 진행 링 (스펙 §4.1). 바탕 트랙 위에 진행(tint)과 추가시간(added)을 그린다.
/// 프레임은 선 가운데 지름(ringRadius × 2)으로 준다.
public struct OpenRing: View {
    public var fill: RingFill
    public var tint: Color
    public var lineWidth: CGFloat

    public init(fill: RingFill, tint: Color, lineWidth: CGFloat) {
        self.fill = fill
        self.tint = tint
        self.lineWidth = lineWidth
    }

    public var body: some View {
        let style = StrokeStyle(lineWidth: lineWidth, lineCap: .round)
        ZStack {
            RingArc(from: 0, to: 1).stroke(Theme.surfaceRaised, style: style)
            if fill.main > 0 {
                RingArc(from: 0, to: fill.main).stroke(tint, style: style)
            }
            if let added = fill.added {
                RingArc(from: added.lowerBound, to: added.upperBound).stroke(Theme.added, style: style)
            }
        }
        .accessibilityHidden(true)
    }
}
