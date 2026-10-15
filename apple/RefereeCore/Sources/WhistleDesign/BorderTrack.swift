import CoreGraphics
import SwiftUI

/// 화면 모양을 따르는 진행 테두리의 경로 (B1 스펙 §2). 위쪽 가운데에서 시작해 시계방향으로 한 바퀴 돈다.
/// 위치는 경로 길이 비율(0...1)로 쓴다. 좌표는 화면 좌표(아래로 y 증가)다.
public struct BorderTrack: Equatable, Sendable {
    /// 선 가운데가 지나는 사각형
    public var rect: CGRect
    /// 위쪽 두 모서리 반지름 (윗변이 시스템 시각 아래로 내려와 화면 모서리보다 작다)
    public var topRadius: CGFloat
    /// 아래쪽 두 모서리 반지름 (화면 모서리를 따른다)
    public var bottomRadius: CGFloat

    public init(rect: CGRect, topRadius: CGFloat, bottomRadius: CGFloat) {
        self.rect = rect
        let limit = max(0, min(rect.width, rect.height) / 2)
        var top = min(max(0, topRadius), limit)
        var bottom = min(max(0, bottomRadius), limit)
        // 위·아래 모서리가 세로로 겹치지 않게
        if top + bottom > rect.height, top + bottom > 0 {
            let scale = rect.height / (top + bottom)
            top *= scale
            bottom *= scale
        }
        self.topRadius = top
        self.bottomRadius = bottom
    }

    public init(rect: CGRect, cornerRadius: CGFloat) {
        self.init(rect: rect, topRadius: cornerRadius, bottomRadius: cornerRadius)
    }

    private var topStraight: CGFloat { max(0, rect.width - 2 * topRadius) }
    private var bottomStraight: CGFloat { max(0, rect.width - 2 * bottomRadius) }
    private var sideStraight: CGFloat { max(0, rect.height - topRadius - bottomRadius) }
    private var topArc: CGFloat { .pi / 2 * topRadius }
    private var bottomArc: CGFloat { .pi / 2 * bottomRadius }

    public var length: CGFloat { topStraight + bottomStraight + 2 * sideStraight + 2 * topArc + 2 * bottomArc }

    /// 모서리 원의 중심: 오른쪽 위, 오른쪽 아래, 왼쪽 아래, 왼쪽 위
    private var centers: [CGPoint] {
        let t = topRadius, b = bottomRadius
        return [
            CGPoint(x: rect.maxX - t, y: rect.minY + t), CGPoint(x: rect.maxX - b, y: rect.maxY - b),
            CGPoint(x: rect.minX + b, y: rect.maxY - b), CGPoint(x: rect.minX + t, y: rect.minY + t),
        ]
    }

    /// 경로 위 위치(0...1)의 점. 0과 1은 위쪽 가운데.
    public func point(at fraction: Double) -> CGPoint {
        let t = topRadius, b = bottomRadius
        let c = centers
        var d = CGFloat(min(max(fraction, 0), 1)) * length
        let segments: [(CGFloat, (CGFloat) -> CGPoint)] = [
            (topStraight / 2, { CGPoint(x: rect.midX + $0, y: rect.minY) }),
            (topArc, { corner(c[0], t, from: -90, travelled: $0) }),
            (sideStraight, { CGPoint(x: rect.maxX, y: rect.minY + t + $0) }),
            (bottomArc, { corner(c[1], b, from: 0, travelled: $0) }),
            (bottomStraight, { CGPoint(x: rect.maxX - b - $0, y: rect.maxY) }),
            (bottomArc, { corner(c[2], b, from: 90, travelled: $0) }),
            (sideStraight, { CGPoint(x: rect.minX, y: rect.maxY - b - $0) }),
            (topArc, { corner(c[3], t, from: 180, travelled: $0) }),
            (topStraight / 2, { CGPoint(x: rect.minX + t + $0, y: rect.minY) }),
        ]
        for (segment, at) in segments {
            if d <= segment { return at(d) }
            d -= segment
        }
        return CGPoint(x: rect.midX, y: rect.minY)
    }

    /// 모서리 원 위에서 시작 각도(도, 0° = 오른쪽, 화면 좌표라 각도가 커지면 시계방향)부터 호 길이만큼 간 점
    private func corner(_ center: CGPoint, _ radius: CGFloat, from degrees: CGFloat, travelled: CGFloat) -> CGPoint {
        guard radius > 0 else { return center }
        let angle = degrees * .pi / 180 + travelled / radius
        return CGPoint(x: center.x + radius * cos(angle), y: center.y + radius * sin(angle))
    }

    /// 한 바퀴 전체 경로. 구간은 `trimmedPath(from:to:)`로 자른다 (길이 비율 기준).
    public var path: Path {
        let t = topRadius, b = bottomRadius
        let c = centers
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - t, y: rect.minY))
        p.addArc(center: c[0], radius: t, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - b))
        p.addArc(center: c[1], radius: b, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: rect.minX + b, y: rect.maxY))
        p.addArc(center: c[2], radius: b, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + t))
        p.addArc(center: c[3], radius: t, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        p.addLine(to: CGPoint(x: rect.midX, y: rect.minY))
        return p
    }
}

/// 테두리 위의 한 구간 (from·to는 경로 전체 0...1 기준)
struct BorderSegment: Shape {
    var track: BorderTrack
    var from: Double
    var to: Double

    func path(in rect: CGRect) -> Path {
        guard to > from else { return Path() }
        return track.path.trimmedPath(from: from, to: to)
    }
}

/// 화면 모양 진행 테두리 (B1 스펙 §2). 바탕 트랙 위에 진행(tint)과 추가시간(added)을 그린다.
/// 경로가 화면 좌표라 화면 전체 크기 프레임(왼쪽 위 원점) 안에 둔다.
public struct BorderProgress: View {
    public var track: BorderTrack
    public var fill: RingFill
    public var tint: Color
    public var lineWidth: CGFloat

    public init(track: BorderTrack, fill: RingFill, tint: Color, lineWidth: CGFloat) {
        self.track = track
        self.fill = fill
        self.tint = tint
        self.lineWidth = lineWidth
    }

    public var body: some View {
        let style = StrokeStyle(lineWidth: lineWidth, lineCap: .round)
        ZStack {
            BorderSegment(track: track, from: 0, to: 1).stroke(Theme.surfaceRaised, style: style)
            if fill.main > 0 {
                BorderSegment(track: track, from: 0, to: fill.main).stroke(tint, style: style)
            }
            if let added = fill.added {
                BorderSegment(track: track, from: added.lowerBound, to: added.upperBound).stroke(Theme.added, style: style)
            }
        }
        .accessibilityHidden(true)
    }
}
