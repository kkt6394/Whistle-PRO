import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct EventPresentationTests {
    @Test func rowsAreNewestFirstWithFootballMinutes() {
        let s = makeState([(.startPeriod, 0),
                           (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 0),
                           (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 1379),
                           (.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 14)), 2760)])
        let rows = s.eventRows
        #expect(rows.map(\.kind) == [.substitution, .yellow, .goal])
        #expect(rows.map(\.minute) == [.added(base: 45, extra: 2), .regular(23), .regular(1)])
        #expect(rows.map(\.detail) == ["#7 → #14", "#4", "#9"])
    }

    @Test func secondHalfMinutesContinue() {
        let s = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600),
                           (.recordEvent(.goal(team: .away, number: nil, ownGoal: false)), 4200)])
        #expect(s.eventRows.first?.minute == .regular(56))
        #expect(s.eventRows.first?.detail == "")
    }

    @Test func eventsOutsidePlayAndShootout() {
        let s = makeState(RuleSet(penalties: true), [
            (.startPeriod, 0), (.endPeriod, 2700),
            (.recordEvent(.card(team: .home, number: nil, kind: .yellow)), 2800),
            (.startPeriod, 3600), (.endPeriod, 6300),
            (.setShootoutFirstTeam(.away), 6400),
            (.recordEvent(.penaltyKick(team: .away, number: 10, scored: false)), 6410),
        ])
        let rows = s.eventRows
        #expect(rows.map(\.minute) == [.shootout, .afterPeriod("1H")])
        #expect(rows[0].scored == false)
        #expect(rows[0].detail == "#10")
    }

    @Test func feedbackForSecondYellowAndWarnings() {
        let commands = [
            Command(id: "s", timestamp: 0, kind: .startPeriod),
            Command(id: "y1", timestamp: 10, kind: .recordEvent(.card(team: .home, number: 4, kind: .yellow))),
            Command(id: "y2", timestamp: 20, kind: .recordEvent(.card(team: .home, number: 4, kind: .yellow))),
            Command(id: "r", timestamp: 21, kind: .recordEvent(.card(team: .home, number: 4, kind: .red))),
        ]
        let s = MatchEngine.reduce(rules: .fifaStandard, commands: commands)
        #expect(s.feedback(forEvent: "y1") == .none)
        #expect(s.feedback(forEvent: "y2") == .secondYellow(eventID: "y2"))
        #expect(s.feedback(forEvent: "r") == .warning(.playerAlreadySentOff, eventID: "r"))
        #expect(s.feedback(forEvent: "missing") == .none)
    }

    @Test func barredPlayerReturnedWarningIsShownButOtherSinBinWarningsStayHidden() {
        let rules = RuleSet(returnSubstitutions: true, sinBin: true, sinBinLength: 600)
        let commands = [
            Command(id: "s", timestamp: 0, kind: .startPeriod),
            Command(id: "b1", timestamp: 100, kind: .recordEvent(.card(team: .home, number: 7, kind: .sinBin))),
            Command(id: "b2", timestamp: 800, kind: .recordEvent(.card(team: .home, number: 7, kind: .sinBin))),
            Command(id: "out", timestamp: 1500, kind: .recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 12))),
            Command(id: "back", timestamp: 1600, kind: .recordEvent(.substitution(team: .home, numberOut: 12, numberIn: 7))),
            // 출전 금지 선수의 또 다른 일시 퇴장 → overlappingSinBin (숨김)
            Command(id: "ov", timestamp: 1650, kind: .recordEvent(.card(team: .home, number: 7, kind: .sinBin))),
            // 교체로 나간 선수의 일시 퇴장 → playerOffPitch (숨김)
            Command(id: "p1", timestamp: 1700, kind: .recordEvent(.substitution(team: .away, numberOut: 5, numberIn: 12))),
            Command(id: "off", timestamp: 1710, kind: .recordEvent(.card(team: .away, number: 5, kind: .sinBin))),
        ]
        let s = MatchEngine.reduce(rules: rules, commands: commands)
        #expect(s.feedback(forEvent: "back") == .warning(.barredPlayerReturned, eventID: "back"))
        #expect(s.eventRows.first { $0.id == "back" }?.warnings == [.barredPlayerReturned])
        #expect(s.feedback(forEvent: "out") == .none)
        #expect(s.resolvedEvents.first { $0.event.id == "ov" }?.warnings == [.overlappingSinBin])
        #expect(s.resolvedEvents.first { $0.event.id == "off" }?.warnings == [.playerOffPitch])
        for id in ["ov", "off"] {
            #expect(s.feedback(forEvent: id) == .none)
            #expect(s.eventRows.first { $0.id == id }?.warnings == [])
        }
    }

    @Test func sinBinBadgesAndShortHandedTeams() {
        let rules = RuleSet(playersPerTeam: 7, minimumPlayers: 6, sinBin: true)
        let s = makeState(rules, [(.startPeriod, 0),
                                  (.recordEvent(.card(team: .away, number: 8, kind: .sinBin)), 100),
                                  (.recordEvent(.card(team: .away, number: 5, kind: .red)), 120)])
        let d = MatchDisplay(state: s, now: 160, clockDisplay: .elapsed)
        #expect(d.sinBins == [SinBinBadge(team: .away, number: 8, remaining: "9:00", seconds: 540)])
        #expect(d.shortHandedTeams == [.away])
    }

    @Test func shootoutDisplay() {
        let rules = RuleSet(penalties: true)
        let halves: [(CommandKind, Double)] = [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)]
        let waiting = MatchDisplay(state: makeState(rules, halves), now: 6400, clockDisplay: .elapsed)
        #expect(waiting.screen == .penalties)
        #expect(waiting.shootout == ShootoutDisplay(score: TeamPair(home: 0, away: 0), next: nil, needsFirstTeam: true, results: TeamPair(home: [], away: []), kicksPerTeam: 5))

        let kicked = MatchDisplay(
            state: makeState(rules, halves + [(.setShootoutFirstTeam(.home), 6400),
                                              (.recordEvent(.penaltyKick(team: .home, number: nil, scored: true)), 6410)]),
            now: 6420, clockDisplay: .elapsed)
        #expect(kicked.shootout == ShootoutDisplay(score: TeamPair(home: 1, away: 0), next: .away, needsFirstTeam: false, results: TeamPair(home: [true], away: []), kicksPerTeam: 5))
        #expect(MatchDisplay(state: makeState(halves), now: 6400, clockDisplay: .elapsed).shootout == nil)
    }

    @Test func preExtraTimeShowsWaitingTime() {
        let s = makeState(RuleSet(extraTime: true), [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        let d = MatchDisplay(state: s, now: 6390, clockDisplay: .elapsed)
        #expect(d.screen == .preExtraTime)
        #expect(d.intervalClock == "+1:30")
        #expect(d.intervalKind == .preExtraTime)
        #expect(d.intervalTone == .waiting)
        #expect(d.nextPeriodLabel == "ET1")
    }
}

@MainActor
struct LastCommandTests {
    @Test func lastCommandIDTracksAcceptedCommandsOnly() {
        let s = MatchSession.start(setup: MatchSetup(), store: MemoryStore(), now: { 0 })
        #expect(s.lastCommandID == nil)
        s.send(.startPeriod)
        let id = s.lastCommandID
        #expect(id != nil)
        #expect(id == s.record.commands.last?.id)
        s.send(.startPeriod)
        #expect(s.lastCommandID == id)
    }

    /// M7b 리뷰: ＋ 버튼 음성 안내가 1초마다 모든 행을 만들었다 → 마지막 행만
    @Test func lastRowMatchesFirstOfAllRows() {
        let s = makeState([(.startPeriod, 0),
                           (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 60),
                           (.recordEvent(.substitution(team: .away, numberOut: 7, numberIn: 14)), 120)])
        let setup = MatchSetup()
        #expect(s.lastEventRow(setup: setup) == s.eventRows(setup: setup).first)
        #expect(makeState([]).lastEventRow(setup: setup) == nil)
    }
}
