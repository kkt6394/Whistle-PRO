import Testing
@testable import RefereeCore

struct PhaseTransitionTests {
    @Test func regularMatchRunsTwoHalves() {
        #expect(play([cmd(.startPeriod, at: 0)]).phase == .period(PeriodRef(.regular, 1)))

        let firstHalfDone = [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2760)]
        #expect(play(firstHalfDone).phase == .interval(after: PeriodRef(.regular, 1)))

        let secondHalf = play(firstHalfDone + [cmd(.startPeriod, at: 3660)])
        #expect(secondHalf.phase == .period(PeriodRef(.regular, 2)))
        #expect(secondHalf.periods[1].offset == 2700)

        #expect(play(firstHalfDone + [cmd(.startPeriod, at: 3660), cmd(.endPeriod, at: 6400)]).phase == .finished(.completed))
    }

    @Test func quartersHaveFourPeriodsAndBreakLengths() {
        let rules = RuleSet(periodCount: 4, periodLength: 1500, halfTimeLength: 600, quarterBreakLength: 120)
        var commands: [Command] = []
        var t = 0.0
        for _ in 1...3 {
            commands += [cmd(.startPeriod, at: t), cmd(.endPeriod, at: t + 1500)]
            t += 2000
        }
        let s = play(rules, commands)
        #expect(s.phase == .interval(after: PeriodRef(.regular, 3)))
        #expect(s.intervalLength(after: PeriodRef(.regular, 1)) == 120)
        #expect(s.intervalLength(after: PeriodRef(.regular, 2)) == 600)
        #expect(s.intervalLength(after: PeriodRef(.regular, 3)) == 120)

        let done = play(rules, commands + [cmd(.startPeriod, at: t), cmd(.endPeriod, at: t + 1500)])
        #expect(done.phase == .finished(.completed))
        #expect(done.periods.map { $0.ref.label(periodCount: 4) } == ["Q1", "Q2", "Q3", "Q4"])
        #expect(done.periods.map(\.offset) == [0, 1500, 3000, 4500])
    }

    @Test func tiedMatchGoesThroughExtraTimeToPenalties() {
        let rules = RuleSet(extraTime: true, penalties: true)
        #expect(play(rules, twoHalves()).phase == .preExtraTime)

        let et1 = twoHalves() + [cmd(.startPeriod, at: 6600), cmd(.endPeriod, at: 7500)]
        let s = play(rules, et1)
        #expect(s.phase == .interval(after: PeriodRef(.extra, 1)))
        #expect(s.periods[2].offset == 5400)
        #expect(s.periods[2].nominalLength == 900)
        #expect(s.intervalLength(after: PeriodRef(.extra, 1)) == 60)

        let done = play(rules, et1 + [cmd(.startPeriod, at: 7560), cmd(.endPeriod, at: 8460)])
        #expect(done.phase == .penalties)
        #expect(done.periods.map { $0.ref.label(periodCount: 2) } == ["1H", "2H", "ET1", "ET2"])
    }

    @Test func tiedWithPenaltiesOnlySkipsExtraTime() {
        #expect(play(RuleSet(penalties: true), twoHalves()).phase == .penalties)
    }

    @Test func tiedWithoutTiebreakersFinishes() {
        #expect(play(twoHalves()).phase == .finished(.completed))
    }

    @Test func invalidCommandIsIgnoredAndRecorded() {
        let s = play([cmd(.endPeriod, at: 0, id: "bad"), cmd(.startPeriod, at: 1), cmd(.startPeriod, at: 2, id: "dup")])
        #expect(s.ignoredCommandIDs == ["bad", "dup"])
        #expect(s.periods.count == 1)
    }

    @Test func kickoffAlternatesEachPeriod() {
        #expect(play([cmd(.setKickoffTeam(.home), at: 0)]).kickoffTeam == .home)

        let afterFirstHalf = [cmd(.setKickoffTeam(.home), at: 0), cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700)]
        #expect(play(afterFirstHalf).kickoffTeam == .away)
    }

    @Test func kickoffCannotChangeAfterStart() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.setKickoffTeam(.home), at: 10, id: "late")])
        #expect(s.ignoredCommandIDs == ["late"])
        #expect(s.kickoffTeam == nil)
    }

    @Test func extraTimeKickoffIsSetSeparately() {
        let rules = RuleSet(extraTime: true)
        let toExtra = [cmd(.setKickoffTeam(.home), at: 0)] + twoHalves() + [cmd(.setKickoffTeam(.away), at: 6400)]
        let s = play(rules, toExtra)
        #expect(s.kickoffTeam == .away)
        #expect(s.regularKickoff == .home)

        let et2 = play(rules, toExtra + [cmd(.startPeriod, at: 6600), cmd(.endPeriod, at: 7500)])
        #expect(et2.kickoffTeam == .home)
    }

    @Test func abandonClosesRunningPeriod() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.abandonMatch(reason: "폭우"), at: 1000)])
        #expect(s.phase == .finished(.abandoned(reason: "폭우")))
        #expect(s.periods[0].endedAt == 1000)

        let again = play([cmd(.startPeriod, at: 0), cmd(.abandonMatch(reason: "폭우"), at: 1000),
                          cmd(.abandonMatch(reason: "x"), at: 1100, id: "late")])
        #expect(again.ignoredCommandIDs == ["late"])
    }

    @Test func undoRemovesLastCommand() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), cmd(.undo, at: 2701)])
        #expect(s.phase == .period(PeriodRef(.regular, 1)))
        #expect(s.periods[0].endedAt == nil)
    }

    @Test func undoOnEmptyLogIsHarmless() {
        let s = play([cmd(.undo, at: 0), cmd(.undo, at: 1), cmd(.startPeriod, at: 2)])
        #expect(s.phase == .period(PeriodRef(.regular, 1)))
        #expect(s.ignoredCommandIDs.isEmpty)
    }

    @Test func commandsWithSameTimestampApplyInLogOrder() {
        let s = play([cmd(.setKickoffTeam(.away), at: 0), cmd(.startPeriod, at: 0)])
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.kickoffTeam == .away)
    }
}
