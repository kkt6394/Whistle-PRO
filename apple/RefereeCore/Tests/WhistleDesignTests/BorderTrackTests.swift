import CoreGraphics
import SwiftUI
import Testing
@testable import WhistleDesign

struct BorderTrackTests {
    // 가로 100, 세로 140, 모서리 20: 직선 위·아래 60, 좌우 100, 모서리 4개 = 2π·20
    let track = BorderTrack(rect: CGRect(x: 10, y: 5, width: 100, height: 140), cornerRadius: 20)

    func near(_ a: CGPoint, _ b: CGPoint, _ tolerance: CGFloat = 0.01) -> Bool {
        abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance
    }

    @Test func lengthIsStraightsPlusCorners() {
        #expect(abs(track.length - (2 * 60 + 2 * 100 + 2 * .pi * 20)) < 0.001)
    }

    @Test func startsAndEndsAtTopCenter() {
        #expect(near(track.point(at: 0), CGPoint(x: 60, y: 5)))
        #expect(near(track.point(at: 1), CGPoint(x: 60, y: 5)))
    }

    /// 화면 좌표(아래로 y 증가)에서 시계방향: 위쪽 가운데 → 오른쪽 → 아래 → 왼쪽
    @Test func goesClockwise() {
        let quarter = 1.0 / 8
        let early = track.point(at: 0.01)
        #expect(early.x > 60 && abs(early.y - 5) < 0.001, "위쪽 변을 따라 오른쪽으로")
        // 위 가운데에서 30(위 직선 반) + 10π(모서리)만큼 가면 오른쪽 변 시작
        let rightTop = track.point(at: Double((30 + 10 * .pi) / track.length))
        #expect(near(rightTop, CGPoint(x: 110, y: 25)))
        #expect(track.point(at: 0.5).y > 140, "반 바퀴면 아래쪽 변")
        #expect(track.point(at: 1 - quarter).x < 60, "끝 무렵은 왼쪽 위")
    }

    @Test func cornerPointLiesOnArc() {
        // 오른쪽 위 모서리 가운데: 위 직선 반(30) + 모서리 반(5π)
        let p = track.point(at: Double((30 + 5 * .pi) / track.length))
        let center = CGPoint(x: 90, y: 25)
        #expect(abs(hypot(p.x - center.x, p.y - center.y) - 20) < 0.001)
        #expect(p.x > center.x && p.y < center.y)
    }

    @Test func fractionIsClamped() {
        #expect(near(track.point(at: -1), track.point(at: 0)))
        #expect(near(track.point(at: 2), track.point(at: 1)))
    }

    /// 그리는 경로를 길이 비율로 자른 끝점이 point(at:)와 같아야 채움이 맞다
    @Test(arguments: [0.1, 0.25, 0.4, 0.5, 0.75, 0.9])
    func trimmedPathEndsAtPoint(fraction: Double) throws {
        let end = try #require(track.path.trimmedPath(from: 0, to: fraction).currentPoint)
        #expect(near(end, track.point(at: fraction), 1), "\(fraction): \(end) vs \(track.point(at: fraction))")
    }

    @Test func radiusIsClampedToHalfShortSide() {
        #expect(BorderTrack(rect: CGRect(x: 0, y: 0, width: 40, height: 100), cornerRadius: 50).cornerRadius == 20)
        #expect(BorderTrack(rect: .zero, cornerRadius: 10).cornerRadius == 0)
        #expect(BorderTrack(rect: CGRect(x: 0, y: 0, width: 40, height: 40), cornerRadius: -3).cornerRadius == 0)
    }

    @Test func squareCornersStillWork() {
        let square = BorderTrack(rect: CGRect(x: 0, y: 0, width: 100, height: 100), cornerRadius: 0)
        #expect(abs(square.length - 400) < 0.001)
        #expect(near(square.point(at: 0.125), CGPoint(x: 100, y: 0)))
        #expect(near(square.point(at: 0.375), CGPoint(x: 100, y: 100)))
    }
}
