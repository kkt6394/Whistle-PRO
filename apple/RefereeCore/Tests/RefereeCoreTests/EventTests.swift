import Testing
@testable import RefereeCore

struct EventTests {
    @Test func goalsAndOwnGoalsCount() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100), goal(.away, at: 200),
                      cmd(.recordEvent(.goal(team: .away, number: 4, ownGoal: true)), at: 300)])
        #expect(s.score == TeamPair(home: 2, away: 1))
        #expect(s.resolvedEvents.map(\.kind) == [.goal, .goal, .ownGoal])
    }

    @Test func eventStoresPeriodAndMatchSecond() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2760), cmd(.startPeriod, at: 3660), goal(.home, at: 3700)])
        #expect(s.events[0].time == EventTime(period: PeriodRef(.regular, 2), duringPlay: true, matchSecond: 2740))
    }

    @Test func goalOutsidePlayIsRejectedButCardsAreAllowedInInterval() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700),
                      goal(.home, at: 2800, id: "g"), card(.home, 5, .yellow, at: 2800, id: "c")])
        #expect(s.ignoredCommandIDs == ["g"])
        #expect(s.events.count == 1)
        #expect(s.events[0].time == EventTime(period: PeriodRef(.regular, 1), duringPlay: false, matchSecond: 2700))
    }

    @Test func eventsBeforeKickoffAreRejected() {
        let s = play([card(.home, 5, .yellow, at: 0, id: "c")])
        #expect(s.ignoredCommandIDs == ["c"])
    }

    @Test func secondYellowForSamePlayerIsSendingOff() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, 4, .yellow, at: 100), card(.away, 4, .yellow, at: 150),
                      card(.home, 4, .yellow, at: 200)])
        #expect(s.resolvedEvents.map(\.kind) == [.yellow, .yellow, .secondYellow])
        #expect(s.sentOffCounts == TeamPair(home: 1, away: 0))
    }

    @Test func secondYellowRuleCanBeDisabled() {
        let s = play(RuleSet(secondYellowSendOff: false),
                     [cmd(.startPeriod, at: 0), card(.home, 4, .yellow, at: 100), card(.home, 4, .yellow, at: 200)])
        #expect(s.resolvedEvents.map(\.kind) == [.yellow, .yellow])
        #expect(s.sentOffCounts == TeamPair(home: 0, away: 0))
    }

    @Test func yellowWithoutNumberNeverAccumulates() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, nil, .yellow, at: 100), card(.home, nil, .yellow, at: 200)])
        #expect(s.resolvedEvents.map(\.kind) == [.yellow, .yellow])
    }

    @Test func editingNumberReResolvesCards() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, 4, .yellow, at: 100), card(.home, 5, .yellow, at: 200, id: "y2"),
                      cmd(.editEvent(id: "y2", .card(team: .home, number: 4, kind: .yellow)), at: 300)])
        #expect(s.resolvedEvents.map(\.kind) == [.yellow, .secondYellow])
        #expect(s.events[1].time.matchSecond == 200)
    }

    @Test func editCannotChangeEventType() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, id: "g"),
                      cmd(.editEvent(id: "g", .card(team: .home, number: 9, kind: .red)), at: 200, id: "e")])
        #expect(s.ignoredCommandIDs == ["e"])
        #expect(s.events[0].payload == .goal(team: .home, number: nil, ownGoal: false))
    }

    @Test func deleteRemovesEventAndUnknownIdIsIgnored() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, id: "g"), cmd(.deleteEvent(id: "g"), at: 150),
                      cmd(.deleteEvent(id: "nope"), at: 160, id: "d")])
        #expect(s.events.isEmpty)
        #expect(s.ignoredCommandIDs == ["d"])
    }

    @Test func sinBinCardRequiresSinBinRule() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, 4, .sinBin, at: 100, id: "s")])
        #expect(s.ignoredCommandIDs == ["s"])
    }

    @Test func invalidShirtNumberIsRejected() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, number: 100, id: "g")])
        #expect(s.ignoredCommandIDs == ["g"])
    }

    @Test func substitutionLimitWarnsWithoutBlocking() {
        let s = play(RuleSet(substitutionLimit: 1), [cmd(.startPeriod, at: 0), sub(.home, at: 100), sub(.home, at: 200), sub(.away, at: 300)])
        #expect(s.events.count == 3)
        #expect(s.resolvedEvents.map(\.warnings) == [[], [.substitutionLimitExceeded], []])
        #expect(s.substitutionCounts == TeamPair(home: 2, away: 1))
    }

    @Test func returnSubstitutionsDisableLimit() {
        let s = play(RuleSet(substitutionLimit: 1, returnSubstitutions: true),
                     [cmd(.startPeriod, at: 0), sub(.home, at: 100), sub(.home, at: 200)])
        #expect(s.resolvedEvents.allSatisfy { $0.warnings.isEmpty })
    }

    @Test func extraTimeAddsSubstitutionAllowance() {
        let rules = RuleSet(extraTime: true, substitutionLimit: 1, extraTimeExtraSubs: 1)
        let s = play(rules, [cmd(.startPeriod, at: 0), sub(.home, at: 100), cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600),
                             cmd(.endPeriod, at: 6300), sub(.home, at: 6400), sub(.home, at: 6500)])
        #expect(s.phase == .preExtraTime)
        #expect(s.resolvedEvents.map(\.warnings) == [[], [], [.substitutionLimitExceeded]])
    }

    @Test func belowMinimumPlayersWarns() {
        let rules = RuleSet(playersPerTeam: 7, minimumPlayers: 6)
        let s = play(rules, [cmd(.startPeriod, at: 0), card(.home, 1, .red, at: 100), card(.home, 2, .red, at: 200)])
        #expect(s.playersOnField(at: 300) == TeamPair(home: 5, away: 7))
        #expect(s.matchWarnings(at: 300) == [.belowMinimumPlayers(.home)])
        #expect(s.matchWarnings(at: 300).map(\.code) == ["belowMinimumPlayers:home"])
        #expect(play(RuleSet(playersPerTeam: 7, minimumPlayers: nil),
                     [cmd(.startPeriod, at: 0), card(.home, 1, .red, at: 100)]).matchWarnings(at: 300).isEmpty)
    }
}
