import Testing
@testable import RefereeCore

struct ClockTests {
    @Test func readsElapsedRemainingAndAddedTime() throws {
        let s = play([cmd(.startPeriod, at: 100)])
        let c = try #require(s.clock(at: 700.9))
        #expect(c.elapsed == 600)
        #expect(c.matchSecond == 600)
        #expect(c.remaining == 2100)
        #expect(c.addedTime == 0)

        let late = try #require(s.clock(at: 100 + 2833))
        #expect(late.remaining == 0)
        #expect(late.addedTime == 133)
    }

    @Test func secondHalfMatchSecondStartsAtFirstHalfLength() throws {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2800), cmd(.startPeriod, at: 3700)])
        let c = try #require(s.clock(at: 3760))
        #expect(c.period == PeriodRef(.regular, 2))
        #expect(c.elapsed == 60)
        #expect(c.matchSecond == 2760)
    }

    @Test func clockFreezesAfterPeriodEnds() throws {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2800)])
        let c = try #require(s.clock(at: 3000))
        #expect(c.elapsed == 2800)
        #expect(c.addedTime == 100)
    }

    @Test func nowBeforeStartClampsToZero() throws {
        let s = play([cmd(.startPeriod, at: 100)])
        let c = try #require(s.clock(at: 50))
        #expect(c.elapsed == 0)
        #expect(c.remaining == 2700)
    }

    @Test func noClockBeforeKickoff() {
        #expect(play([]).clock(at: 0) == nil)
    }

    @Test func stoppageAccumulatesAndClosesAtPeriodEnd() throws {
        let running = play([cmd(.startPeriod, at: 0), cmd(.toggleStoppage, at: 100), cmd(.toggleStoppage, at: 160),
                            cmd(.toggleStoppage, at: 300)])
        let c = try #require(running.clock(at: 330))
        #expect(c.stoppageTotal == 90)
        #expect(c.stoppageRunning)

        let ended = play([cmd(.startPeriod, at: 0), cmd(.toggleStoppage, at: 100), cmd(.toggleStoppage, at: 160),
                          cmd(.toggleStoppage, at: 300), cmd(.endPeriod, at: 400)])
        #expect(ended.stoppageTotals(at: 1000) == [160])
        #expect(ended.clock(at: 1000)?.stoppageRunning == false)
    }

    @Test func stoppageToggleOutsidePeriodIsIgnored() {
        let s = play([cmd(.toggleStoppage, at: 0, id: "t")])
        #expect(s.ignoredCommandIDs == ["t"])
    }

    @Test func adjustPeriodLengthBeforeStart() {
        let s = play([cmd(.adjustPeriodLength(1800), at: 0), cmd(.startPeriod, at: 0)])
        #expect(s.clock(at: 0)?.remaining == 1800)
    }

    @Test func adjustPeriodLengthDuringPeriodAffectsCurrentAndLater() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.adjustPeriodLength(1500), at: 60), cmd(.endPeriod, at: 1500),
                      cmd(.startPeriod, at: 2400)])
        #expect(s.periods[0].nominalLength == 1500)
        #expect(s.periods[1].nominalLength == 1500)
        #expect(s.periods[1].offset == 1500)
    }

    @Test func adjustDuringPreExtraTimeChangesExtraLength() {
        let s = play(RuleSet(extraTime: true), twoHalves() + [cmd(.adjustPeriodLength(600), at: 6400), cmd(.startPeriod, at: 6500)])
        #expect(s.periods[2].nominalLength == 600)
        #expect(s.regularPeriodLength == 2700)
    }

    @Test func adjustPeriodLengthRejectsOutOfRange() {
        let s = play([cmd(.adjustPeriodLength(30), at: 0, id: "a")])
        #expect(s.ignoredCommandIDs == ["a"])
    }

    @Test func intervalCountsDown() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700)])
        #expect(s.intervalRemaining(at: 2800) == 800)
        #expect(s.intervalRemaining(at: 3700) == 0)
        #expect(play([cmd(.startPeriod, at: 0)]).intervalRemaining(at: 10) == nil)
    }
}
