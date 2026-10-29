import Foundation
import Testing
@testable import RefereeSession

private let metersPerDegree = 111_320.0

private func meters(_ a: GeoPoint, _ b: GeoPoint) -> Double {
    let north = (b.latitude - a.latitude) * metersPerDegree
    let east = (b.longitude - a.longitude) * metersPerDegree * cos((a.latitude + b.latitude) / 2 * .pi / 180)
    return (north * north + east * east).squareRoot()
}

private let center = GeoPoint(latitude: 37.5, longitude: 127.0)
private func basePitch() -> Pitch { Pitch(center: center, length: 105, width: 68, bearing: 30) }

struct PitchTests {
    @Test func rectangleCornersGivePitch() throws {
        let pitch = try #require(Pitch.fromCorners(basePitch().corners))
        #expect(abs(pitch.length - 105) <= 0.5)
        #expect(abs(pitch.width - 68) <= 0.5)
        #expect(abs(pitch.bearing - 30) <= 0.5)
        #expect(meters(center, pitch.center) < 0.5)
    }

    @Test func cornersInAnyOrderGiveSamePitch() throws {
        let c = basePitch().corners
        for order in [[0, 2, 1, 3], [3, 2, 1, 0]] {
            let pitch = try #require(Pitch.fromCorners(order.map { c[$0] }))
            #expect(abs(pitch.length - 105) <= 0.5)
            #expect(abs(pitch.width - 68) <= 0.5)
            #expect(meters(center, pitch.center) < 0.5)
        }
    }

    @Test func firstCornerSideIsGoalLineA() throws {
        let c = basePitch().corners
        for first in 0..<4 {
            let order = (0..<4).map { c[(first + $0) % 4] }
            let pitch = try #require(Pitch.fromCorners(order))
            #expect(abs(pitch.local(order[0]).y) <= 0.5)
        }
    }

    @Test func skewedQuadIsSquared() throws {
        var c = basePitch().corners
        c[2] = GeoPoint(latitude: c[2].latitude + 3.0 / metersPerDegree, longitude: c[2].longitude)
        let pitch = try #require(Pitch.fromCorners(c))
        #expect(abs(pitch.length - 105) <= 2)
        #expect(abs(pitch.width - 68) <= 2)
    }

    @Test func degenerateCornersGiveNull() {
        let c = basePitch().corners
        #expect(Pitch.fromCorners(Array(c.prefix(3))) == nil)
        let line = (0..<4).map { GeoPoint(latitude: 37.5 + Double($0) * 0.0001, longitude: 127.0) }
        #expect(Pitch.fromCorners(line) == nil)
    }

    @Test func localAndGeoRoundTrip() {
        let pitch = basePitch()
        let p = GeoPoint(latitude: 37.5004, longitude: 127.0003)
        let back = pitch.geo(pitch.local(p))
        #expect(meters(p, back) < 0.01)
        let expected: [(Double, Double)] = [(0, 0), (68, 0), (68, 105), (0, 105)]
        for (corner, e) in zip(pitch.corners, expected) {
            let l = pitch.local(corner)
            #expect(abs(l.x - e.0) <= 0.5)
            #expect(abs(l.y - e.1) <= 0.5)
        }
    }

    @Test func localCoordinatesOutsidePitch() {
        let pitch = basePitch()
        let p = pitch.geo(PitchPoint(x: -5, y: 50))
        #expect(abs(pitch.local(p).x - -5) <= 0.1)
        #expect(pitch.contains(p, margin: 8))
        #expect(!pitch.contains(p, margin: 2))
    }

    @Test func standardPitchKeepsMarkings() {
        #expect(abs(PitchMarkings.of(length: 105, width: 68).scale - 1) < 1e-9)
        #expect(abs(PitchMarkings.of(length: 90, width: 60).scale - 1) < 1e-9)
    }

    @Test func smallPitchScalesMarkings() {
        let m = PitchMarkings.of(length: 40, width: 20)
        #expect(abs(m.scale - 0.397) <= 0.01)
        #expect(abs(m.penaltyAreaWidth - 16.0) <= 0.1)
    }

    /// 경기장 라인 (경기장 좌표): 하프라인, 센터서클, 양 끝(짧은 변)마다 페널티·골 에어리어와 골대
    @Test func markingLinesPutGoalsOnShortSides() {
        let lines = PitchMarkings.lines(length: 105, width: 68)
        #expect(lines.count == 8)
        // 하프라인은 가로로 가운데
        #expect(lines[0] == [PitchPoint(x: 0, y: 52.5), PitchPoint(x: 68, y: 52.5)])
        // 센터서클은 닫힌 원
        #expect(lines[1].first == lines[1].last)
        #expect(lines[1].allSatisfy { abs(hypot($0.x - 34, $0.y - 52.5) - 9.15) < 1e-6 })
        // 페널티 에어리어는 양 골라인(y 0, 105)에서 16.5 m 안쪽으로, 폭 40.32 m
        let near = lines[2], far = lines[5]
        #expect(abs(near[0].y) < 1e-9 && abs(near[1].y - 16.5) < 1e-9)
        #expect(abs(far[0].y - 105) < 1e-9 && abs(far[1].y - 88.5) < 1e-9)
        #expect(abs(near[3].x - near[0].x - 40.32) < 1e-9)
        // 골대는 골라인 바깥
        #expect(lines[4].contains { $0.y < 0 })
        #expect(lines[7].contains { $0.y > 105 })
    }
}

/// 경기장 안을 1 m 간격으로 채운 점들.
private func filled(_ pitch: Pitch) -> [GeoPoint] {
    (0...Int(pitch.width)).flatMap { x in
        (0...Int(pitch.length)).map { y in pitch.geo(PitchPoint(x: Double(x), y: Double(y))) }
    }
}

struct PitchEstimateTests {
    private let truth = Pitch(center: center, length: 100, width: 60, bearing: 30)

    private func expectFits(_ pitch: Pitch, tolerance: Double) {
        #expect(abs(pitch.length - 100) <= tolerance)
        #expect(abs(pitch.width - 60) <= tolerance)
        let diff = abs(pitch.bearing - 30)
        #expect(diff <= 2 || abs(diff - 180) <= 2)
    }

    @Test func estimateFitsCoveredRectangle() throws {
        expectFits(try #require(PitchEstimate.of(filled(truth))), tolerance: 3)
    }

    @Test func estimateIgnoresOutliers() throws {
        let far = (0..<3).map { GeoPoint(latitude: 37.5 + (500.0 + Double($0)) / metersPerDegree, longitude: 127.0) }
        expectFits(try #require(PitchEstimate.of(filled(truth) + far)), tolerance: 3)
    }

    @Test func thinStripIsWidenedTo30() throws {
        let pitch = try #require(PitchEstimate.of(filled(Pitch(center: center, length: 100, width: 4, bearing: 30))))
        #expect(abs(pitch.width - 30) <= 0.5)
        #expect(abs(pitch.length - 100) <= 3)
    }

    @Test func tooFewPointsGiveNoEstimate() {
        #expect(PitchEstimate.of([]) == nil)
        #expect(PitchEstimate.of(Array(filled(truth).prefix(9))) == nil)
    }
}
