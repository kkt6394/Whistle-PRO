import CoreGraphics
import SwiftUI

/// 화면 모양을 따르는 진행 테두리의 경로 (B1 스펙 §2). 위쪽 가운데에서 시작해 시계방향으로 한 바퀴 돈다.
/// 위치는 경로 길이 비율(0...1)로 쓴다. 좌표는 화면 좌표(아래로 y 증가)다.
public struct BorderTrack: Equatable, Sendable {
    /// 선 가운데가 지나는 사각형
    public var rect: CGRect
    public var cornerRadius: CGFloat

    public init(rect: CGRect, cornerRadius: CGFloat) {
        self.rect = rect
        self.cornerRadius = min(max(0, cornerRadius), max(0, min(rect.width, rect.height) / 2))
    }

    private var straightX: CGFloat { max(0, rect.width - 2 * cornerRadius) }
    private var straightY: CGFloat { max(0, rect.height - 2 * cornerRadius) }
    private var arc: CGFloat { .pi / 2 * cornerRadius }

    public var length: CGFloat { 2 * straightX + 2 * straightY + 4 * arc }

    /// 모서리 원의 중심: 오른쪽 위, 오른쪽 아래, 왼쪽 아래, 왼쪽 위
    private var centers: [CGPoint] {
        let r = cornerRadius
        return [
            CGPoint(x: rect.maxX - r, y: rect.minY + r), CGPoint(x: rect.maxX - r, y: rect.maxY - r),
            CGPoint(x: rect.minX + r, y: rect.maxY - r), CGPoint(x: rect.minX + r, y: rect.minY + r),
        ]
    }

    /// 경로 위 위치(0...1)의 점. 0과 1은 위쪽 가운데.
    public func point(at fraction: Double) -> CGPoint {
        let r = cornerRadius
        let c = centers
        var d = CGFloat(min(max(fraction, 0), 1)) * length
        let segments: [(CGFloat, (CGFloat) -> CGPoint)] = [
            (straightX / 2, { CGPoint(x: rect.midX + $0, y: rect.minY) }),
            (arc, { corner(c[0], from: -90, travelled: $0) }),
            (straightY, { CGPoint(x: rect.maxX, y: rect.minY + r + $0) }),
            (arc, { corner(c[1], from: 0, travelled: $0) }),
            (straightX, { CGPoint(x: rect.maxX - r - $0, y: rect.maxY) }),
            (arc, { corner(c[2], from: 90, travelled: $0) }),
            (straightY, { CGPoint(x: rect.minX, y: rect.maxY - r - $0) }),
            (arc, { corner(c[3], from: 180, travelled: $0) }),
            (straightX / 2, { CGPoint(x: rect.minX + r + $0, y: rect.minY) }),
        ]
        for (segment, at) in segments {
            if d <= segment { return at(d) }
            d -= segment
        }
        return CGPoint(x: rect.midX, y: rect.minY)
    }

    /// 모서리 원 위에서 시작 각도(도, 0° = 오른쪽, 화면 좌표라 각도가 커지면 시계방향)부터 호 길이만큼 간 점
    private func corner(_ center: CGPoint, from degrees: CGFloat, travelled: CGFloat) -> CGPoint {
        guard cornerRadius > 0 else { return center }
        let angle = degrees * .pi / 180 + travelled / cornerRadius
        return CGPoint(x: center.x + cornerRadius * cos(angle), y: center.y + cornerRadius * sin(angle))
    }

    /// 한 바퀴 전체 경로. 구간은 `trimmedPath(from:to:)`로 자른다 (길이 비율 기준).
    public var path: Path {
        let r = cornerRadius
        let c = centers
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        p.addArc(center: c[0], radius: r, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        p.addArc(center: c[1], radius: r, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        p.addArc(center: c[2], radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r))
        p.addArc(center: c[3], radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
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
