import Foundation
import Testing
@testable import RefereeCore

private let pkRules = RuleSet(penalties: true, sinBin: true)

private func kick(_ team: Team, _ scored: Bool, at t: Double, id: String) -> Command {
    cmd(.recordEvent(.penaltyKick(team: team, number: nil, scored: scored)), at: t, id: id)
}

/// home O O O, away X X X → 3:0 결정 (마지막 킥 id "last")
private func decidedShootout() -> [Command] {
    twoHalves() + [
        cmd(.setShootoutFirstTeam(.home), at: 6400),
        kick(.home, true, at: 6410, id: "h1"), kick(.away, false, at: 6420, id: "a1"),
        kick(.home, true, at: 6430, id: "h2"), kick(.away, false, at: 6440, id: "a2"),
        kick(.home, true, at: 6450, id: "h3"), kick(.away, false, at: 6460, id: "last"),
    ]
}

struct BacklogEngineTests {
    @Test func sinBinsEndWhenShootoutStarts() {
        let s = play(pkRules, [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600),
                               card(.home, 5, .sinBin, at: 6200), cmd(.endPeriod, at: 6300)])
        #expect(s.phase == .penalties)
        #expect(s.activeSinBins(at: 6300).isEmpty)
        #expect(s.playersOnField(at: 6300) == TeamPair(home: 11, away: 11))
    }

    @Test func sinBinsEndWhenMatchFinishes() {
        let s = play(RuleSet(sinBin: true), [cmd(.startPeriod, at: 0), card(.home, 5, .sinBin, at: 2600), cmd(.endPeriod, at: 2700),
                                             cmd(.startPeriod, at: 3600), cmd(.abandonMatch(reason: "x"), at: 3610)])
        #expect(s.activeSinBins(at: 3610).isEmpty)
    }

    @Test func shootoutLabelIsPK() {
        #expect(play(pkRules, twoHalves()).currentPeriodLabel == "PK")
        #expect(play(pkRules, decidedShootout()).currentPeriodLabel == "PK")
        #expect(play(twoHalves()).currentPeriodLabel == "2H")
    }

    /// 백로그: 승부차기 중(킥 없이) 경기를 중단하면 라벨이 PK가 아니라 마지막 기간 라벨이었다
    @Test func abandonedDuringShootoutIsPK() {
        #expect(play(pkRules, twoHalves() + [cmd(.abandonMatch(reason: "x"), at: 6400)]).currentPeriodLabel == "PK")
        #expect(play(twoHalves() + [cmd(.abandonMatch(reason: "x"), at: 6400)]).currentPeriodLabel == "2H")
    }

    @Test func hugeTimestampsDoNotTrap() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.toggleStoppage, at: 1e300)])
        _ = s.snapshot(at: .infinity, warningBeforeEnd: 300, breakWarningBeforeEnd: 300)
        _ = s.snapshot(at: 1e300, warningBeforeEnd: 300, breakWarningBeforeEnd: 300)
        _ = s.snapshot(at: .nan, warningBeforeEnd: 300, breakWarningBeforeEnd: 300)
        #expect(wholeSeconds(.infinity) == 0)
        #expect(wholeSeconds(1e300) == 1_000_000_000_000)
        #expect(wholeSeconds(-5.5) == -6)
    }

    /// Kotlin BacklogEngineTests와 같은 값: validate() 범위 밖 규칙에서 기간 offset 합이 32비트를 넘는다
    @Test func hugePeriodLengthOffsetsDoNotOverflow() {
        let rules = RuleSet(periodCount: 4, periodLength: 2_000_000_000, quarterBreakLength: 0)
        let s = play(rules, [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 10), cmd(.startPeriod, at: 20), cmd(.endPeriod, at: 30),
                             cmd(.startPeriod, at: 40), goal(.home, at: 50, id: "g")])
        #expect(s.periods.last?.offset == 4_000_000_000)
        #expect(s.events.map(\.time.matchSecond) == [4_000_000_010])
        #expect(s.clock(at: 50)?.matchSecond == 4_000_000_010)
    }

    /// Kotlin과 같은 값: 교체 한도 + 연장 추가 교체가 32비트를 넘어도 경고가 붙지 않는다
    @Test func hugeSubstitutionLimitDoesNotOverflow() {
        let rules = RuleSet(extraTime: true, substitutionLimit: Int(Int32.max), extraTimeExtraSubs: 1)
        let s = play(rules, twoHalves() + [cmd(.startPeriod, at: 6400), sub(.home, at: 6500)])
        #expect(s.resolvedEvents.count == 1)
        #expect(s.resolvedEvents.allSatisfy { $0.warnings.isEmpty })
    }

    @Test func deletingDecisiveKickReopensShootout() {
        let reopened = play(pkRules, decidedShootout() + [cmd(.deleteEvent(id: "last"), at: 6500)])
        #expect(reopened.phase == .penalties)
        #expect(reopened.shootout.next == .away)
    }

    @Test func editingDecisiveKickAfterFinishReopensShootout() {
        // h3를 실패로 고치면 2:0, away 2회 남음 → 미정
        let edited = play(pkRules, decidedShootout() + [
            cmd(.editEvent(id: "h3", .penaltyKick(team: .home, number: nil, scored: false)), at: 6500),
        ])
        #expect(edited.phase == .penalties)
        #expect(edited.shootout.goals == TeamPair(home: 2, away: 0))
    }

    @Test func kickNumberCanBeFixedAfterFinish() {
        let s = play(pkRules, decidedShootout() + [
            cmd(.editEvent(id: "h1", .penaltyKick(team: .home, number: 9, scored: true)), at: 6500, id: "e"),
        ])
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.phase == .finished(.completed))
    }
}
