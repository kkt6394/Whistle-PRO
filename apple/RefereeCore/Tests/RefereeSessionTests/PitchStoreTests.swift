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
private func basePitch(_ bearing: Double = 30) -> Pitch { Pitch(center: center, length: 105, width: 68, bearing: bearing) }

/// p에서 북쪽으로 north m 옮긴 점
private func north(_ p: GeoPoint, _ north: Double) -> GeoPoint {
    GeoPoint(latitude: p.latitude + north / metersPerDegree, longitude: p.longitude)
}

/// 경기장 한가운데 inside개 + 멀리(1 km) outside개
private func track(_ pitch: Pitch, inside: Int, outside: Int) -> [GeoPoint] {
    Array(repeating: pitch.center, count: inside) + Array(repeating: north(pitch.center, 1000), count: outside)
}

private let anyPoint = [GeoPoint(latitude: 0, longitude: 0)]
private let a0 = UUID(uuidString: "AAAAAAAA-0000-0000-0000-000000000001")!
private let b0 = UUID(uuidString: "BBBBBBBB-0000-0000-0000-000000000002")!

private let fixedPitchesJSON = """
{"pitches":[{"id":"11111111-2222-3333-4444-555555555555","corners":[{"latitude":37.5,"longitude":127.0},{"latitude":37.5,"longitude":127.001},{"latitude":37.501,"longitude":127.001},{"latitude":37.501,"longitude":127.0}],"createdAt":1793000000.0}],
 "links":["AAAAAAAA-0000-0000-0000-000000000001",{"pitch":"11111111-2222-3333-4444-555555555555"},"BBBBBBBB-0000-0000-0000-000000000002",{}]}
"""

struct PitchStoreTests {
    let a = UUID()
    let b = UUID()
    let c = UUID()

    private func expectSame(_ expected: Pitch, _ actual: Pitch?, tolerance: Double = 0.5) throws {
        let p = try #require(actual)
        #expect(abs(expected.length - p.length) <= tolerance)
        #expect(abs(expected.width - p.width) <= tolerance)
        #expect(meters(expected.center, p.center) < tolerance)
    }

    @Test func savedPitchIsLinkedToMatch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        try expectSame(basePitch(), library.pitch(matchID: a, points: anyPoint))
    }

    @Test func otherMatchOnSamePitchIsMatchedAutomatically() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        try expectSame(basePitch(), library.pitch(matchID: b, points: track(basePitch(), inside: 6, outside: 4)))
        #expect(library.readPitches()?.links[b] == nil)
    }

    @Test func matchMostlyOutsideIsNotMatched() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        #expect(library.pitch(matchID: c, points: track(basePitch(), inside: 4, outside: 6)) == nil)
    }

    @Test func marginOf15MetersCounts() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let pitch = basePitch()
        try library.savePitch(matchID: a, corners: pitch.corners, now: 100)
        // 터치라인(x=0) 밖으로 10 m / 20 m
        func outside(_ distance: Double) -> [GeoPoint] {
            Array(repeating: pitch.geo(PitchPoint(x: -distance, y: 52)), count: 10)
        }
        try expectSame(pitch, library.pitch(matchID: b, points: outside(10)))
        #expect(library.pitch(matchID: b, points: outside(20)) == nil)
    }

    @Test func bestOfSeveralPitchesWins() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let p1 = basePitch(0)
        let p2 = Pitch(center: north(center, 50), length: 105, width: 68, bearing: 0)
        try library.savePitch(matchID: a, corners: p1.corners, now: 100)
        try library.savePitch(matchID: b, corners: p2.corners, now: 200)
        #expect(library.readPitches()?.pitches.count == 2)
        // 둘 다 안 7개, P1만 4개, P2만 9개 → P1 55%, P2 80%
        let points = Array(repeating: center, count: 7) + Array(repeating: north(center, -60), count: 4)
            + Array(repeating: north(center, 100), count: 9)
        try expectSame(p2, library.pitch(matchID: c, points: points))
    }

    @Test func clearedMatchStaysCleared() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        try library.clearPitch(matchID: b)
        #expect(library.pitch(matchID: b, points: track(basePitch(), inside: 10, outside: 0)) == nil)
        try expectSame(basePitch(), library.pitch(matchID: c, points: track(basePitch(), inside: 10, outside: 0)))
    }

    @Test func refittingNearbyUpdatesSharedPitch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        let moved = Pitch(center: north(center, 10), length: 105, width: 68, bearing: 30)
        try library.savePitch(matchID: a, corners: moved.corners, now: 200)
        #expect(library.readPitches()?.pitches.count == 1)
        try expectSame(moved, library.pitch(matchID: b, points: track(moved, inside: 10, outside: 0)))
    }

    @Test func refittingAdjacentSmallPitchCreatesNewPitch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let small = Pitch(center: center, length: 40, width: 20, bearing: 30)
        try library.savePitch(matchID: a, corners: small.corners, now: 100)
        try library.savePitch(matchID: b, corners: small.corners, now: 150)
        #expect(library.readPitches()?.pitches.count == 1)
        // 길이 방향으로 25 m 옮긴 옆 구장(중심 거리는 30 m 이내지만 서로의 중심이 상대 경기장 밖)
        let next = Pitch(center: small.geo(PitchPoint(x: 10, y: 45)), length: 40, width: 20, bearing: 30)
        try library.savePitch(matchID: b, corners: next.corners, now: 200)
        let file = try #require(library.readPitches())
        #expect(file.pitches.count == 2)
        try expectSame(small, library.pitch(matchID: a, points: anyPoint))
        let aCorners = try #require(file.pitches.first { $0.id == file.links[a]?.pitch }).corners
        for (e, x) in zip(small.corners, aCorners) { #expect(meters(e, x) < 0.01) }
        try expectSame(next, library.pitch(matchID: b, points: anyPoint))
    }

    @Test func refittingFarAwayCreatesNewPitch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.savePitch(matchID: a, corners: basePitch().corners, now: 100)
        let moved = Pitch(center: north(center, 40), length: 105, width: 68, bearing: 30)
        try library.savePitch(matchID: a, corners: moved.corners, now: 200)
        #expect(library.readPitches()?.pitches.count == 2)
        try expectSame(moved, library.pitch(matchID: a, points: anyPoint))
        // 연결 없는 경기 B는 원래 구장(중심 가까이 점만 있다)
        try expectSame(basePitch(), library.pitch(matchID: b, points: [basePitch().geo(PitchPoint(x: 34, y: 2))]))
    }

    @Test func corruptFileActsEmptyAndSaveRecovers() throws {
        let dir = tempDirectory()
        let library = MatchLibrary(directory: dir)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("garbage".utf8).write(to: dir.appendingPathComponent(MatchLibrary.pitchesFileName))
        #expect(library.pitch(matchID: a, points: anyPoint) == nil)
        #expect(try library.savePitch(matchID: a, corners: basePitch().corners, now: 100) != nil)
        try expectSame(basePitch(), library.pitch(matchID: a, points: anyPoint))
    }

    @Test func degenerateCornersAreNotSaved() throws {
        let library = MatchLibrary(directory: tempDirectory())
        #expect(try library.savePitch(matchID: a, corners: [center, center, center, center], now: 100) == nil)
        #expect(library.readPitches() == nil)
    }

    @Test func jsonMatchesSwiftShape() throws {
        let file = try JSONDecoder().decode(PitchFile.self, from: Data(fixedPitchesJSON.utf8))
        #expect(file.pitches.count == 1)
        #expect(file.pitches[0].id == UUID(uuidString: "11111111-2222-3333-4444-555555555555"))
        #expect(file.pitches[0].corners[0] == GeoPoint(latitude: 37.5, longitude: 127.0))
        #expect(file.pitches[0].createdAt == 1793000000)
        #expect(file.links[a0] == PitchLink(pitch: file.pitches[0].id))
        #expect(file.links[b0] == PitchLink(pitch: nil))

        // 다시 써도 같은 모양(코틀린 테스트와 같은 항목을 확인)
        let root = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(file)) as? [String: Any])
        let links = try #require(root["links"] as? [Any])
        #expect(links.count == 4)
        // 사전 순서는 정해져 있지 않아 키로 짝을 찾는다
        let a0Index = try #require(links.firstIndex { $0 as? String == a0.uuidString })
        let b0Index = try #require(links.firstIndex { $0 as? String == b0.uuidString })
        #expect((links[a0Index + 1] as? [String: Any])?.keys.sorted() == ["pitch"])
        #expect((links[b0Index + 1] as? [String: Any])?.isEmpty == true)
    }
}
