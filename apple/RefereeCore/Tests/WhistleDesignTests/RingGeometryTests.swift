import Testing
@testable import WhistleDesign

struct RingGeometryTests {
    let share = RingGeometry.regulationShare

    @Test func kickoffIsEmpty() {
        #expect(RingGeometry.period(elapsed: 0, length: 2700) == RingFill(main: 0, added: nil))
    }

    @Test func halfwayFillsHalfOfRegulationShare() {
        let fill = RingGeometry.period(elapsed: 1350, length: 2700)
        #expect(abs(fill.main - share / 2) < 1e-9)
        #expect(fill.added == nil)
    }

    @Test func regulationEndFillsShareWithoutAddedSegment() {
        let fill = RingGeometry.period(elapsed: 2700, length: 2700)
        #expect(abs(fill.main - share) < 1e-9)
        #expect(fill.added == nil)
    }

    @Test func addedTimeGrowsFromShare() throws {
        // 규정 45분의 10% = 270초. 135초면 남은 구간의 절반
        let fill = RingGeometry.period(elapsed: 2835, length: 2700)
        #expect(abs(fill.main - share) < 1e-9)
        let added = try #require(fill.added)
        #expect(abs(added.lowerBound - share) < 1e-9)
        #expect(abs(added.upperBound - (share + (1 - share) / 2)) < 1e-9)
    }

    @Test func addedTimeCapsAtRingEnd() {
        let fill = RingGeometry.period(elapsed: 2700 + 900, length: 2700)
        #expect(abs((fill.added?.upperBound ?? 0) - 1) < 1e-9)
    }

    @Test func zeroLengthGivesEmptyRing() {
        #expect(RingGeometry.period(elapsed: 100, length: 0) == RingFill(main: 0, added: nil))
        #expect(RingGeometry.period(elapsed: -5, length: 2700) == RingFill(main: 0, added: nil))
    }

    @Test func restShrinksWithRemainingTime() {
        #expect(RingGeometry.rest(remaining: 450, total: 900) == RingFill(main: 0.5, added: nil))
        #expect(RingGeometry.rest(remaining: 900, total: 900) == RingFill(main: 1, added: nil))
        #expect(RingGeometry.rest(remaining: 0, total: 900) == RingFill(main: 0, added: nil))
    }

    @Test func restWithZeroTotalIsEmpty() {
        #expect(RingGeometry.rest(remaining: 0, total: 0) == RingFill(main: 0, added: nil))
        #expect(RingGeometry.rest(remaining: 2000, total: 900) == RingFill(main: 1, added: nil))
    }
}
