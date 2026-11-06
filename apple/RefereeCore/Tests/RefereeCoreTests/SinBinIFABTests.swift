import Foundation
import Testing
@testable import RefereeCore

/// 일시퇴장 IFAB 기본 지침: 겹친 일시퇴장 무시, 두 번째 뒤 출전 금지, 교체로 나간 선수 레드 (Kotlin SinBinIFABTest와 같은 케이스)
private let sinBinRules = RuleSet(sinBin: true, sinBinLength: 600)

private func subst(_ team: Team, out: Int?, into: Int?, at t: Double, id: String = UUID().uuidString) -> Command {
    cmd(.recordEvent(.substitution(team: team, numberOut: out, numberIn: into)), at: t, id: id)
}

private func kick(_ team: Team, _ number: Int, at t: Double, id: String) -> Command {
    cmd(.recordEvent(.penaltyKick(team: team, number: number, scored: true)), at: t, id: id)
}

/// 홈 7번 두 번째 일시퇴장 (100·800, 1400에 끝남)
private func secondSinBinHome7() -> [Command] {
    [cmd(.startPeriod, at: 0), card(.home, 7, .sinBin, at: 100), card(.home, 7, .sinBin, at: 800)]
}

struct SinBinIFABTests {
    @Test func unnumberedSinBinsAreAlwaysFirst() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.away, nil, .sinBin, at: 100), card(.away, nil, .sinBin, at: 200)])
        #expect(s.resolvedEvents.filter { $0.kind == .sinBin }.map(\.isSecondSinBin) == [false, false])
        #expect(s.resolvedEvents.allSatisfy { $0.warnings.isEmpty })
        #expect(s.playersOnField(at: 300) == TeamPair(home: 11, away: 9))
    }

    @Test func returnSubstitutionBringsPlayerBackOnPitch() {
        let rules = RuleSet(returnSubstitutions: true)
        let s = play(rules, [cmd(.startPeriod, at: 0), subst(.home, out: 5, into: 12, at: 100), subst(.home, out: 12, into: 5, at: 200),
                             card(.home, 5, .red, at: 300, id: "r")])
        let red = s.resolvedEvents.first { $0.event.id == "r" }
        #expect(red?.reducesTeam == true)
        #expect(red?.warnings == [])
        #expect(s.playersOnField(at: 400) == TeamPair(home: 10, away: 11))
    }

    @Test func barredPlayerRedAfterFilledDoesNotReduce() {
        let s = play(sinBinRules, secondSinBinHome7() + [subst(.home, out: 7, into: 12, at: 1500), card(.home, 7, .red, at: 1600, id: "r")])
        let red = s.resolvedEvents.first { $0.event.id == "r" }
        #expect(red?.warnings == [.playerOffPitch])
        #expect(red?.reducesTeam == false)
        #expect(s.playersOnField(at: 1700) == TeamPair(home: 11, away: 11))
        #expect(s.barredPlayers(at: 1700).isEmpty)
    }

    @Test func barredUnfilledRedCountsOnce() {
        let before = play(sinBinRules, secondSinBinHome7())
        #expect(before.barredPlayers(at: 1500).map(\.number) == [7])
        #expect(before.barredPlayers(at: 1500).first?.filled == false)
        #expect(before.playersOnField(at: 1500) == TeamPair(home: 10, away: 11))

        let s = play(sinBinRules, secondSinBinHome7() + [card(.home, 7, .red, at: 1500)])
        #expect(s.playersOnField(at: 1600) == TeamPair(home: 10, away: 11))
        #expect(s.barredPlayers(at: 1600).isEmpty)
    }

    @Test func barredPlayerReturnedWarns() {
        let rules = RuleSet(returnSubstitutions: true, sinBin: true, sinBinLength: 600)
        let s = play(rules, secondSinBinHome7() + [subst(.home, out: 7, into: 12, at: 1500, id: "out"),
                                                   subst(.home, out: 12, into: 7, at: 1600, id: "back")])
        #expect(s.resolvedEvents.first { $0.event.id == "out" }?.warnings == [])
        #expect(s.resolvedEvents.first { $0.event.id == "back" }?.warnings == [.barredPlayerReturned])
    }

    @Test func penaltyEligibilityExcludesUnfilledBarred() {
        let rules = RuleSet(penalties: true, playersPerTeam: 7, minimumPlayers: nil, sinBin: true, sinBinLength: 600)
        // 홈 1~6이 차고, 7번째 홈 킥은 1번이 다시 (홈 7번은 출전 금지·미채움이라 자격 6명)
        let homeNumbers = [1, 2, 3, 4, 5, 6, 1]
        var kicks: [Command] = []
        for (i, number) in homeNumbers.enumerated() {
            kicks.append(kick(.home, number, at: 7000 + Double(i * 2), id: "h\(i)"))
            if i < homeNumbers.count - 1 { kicks.append(kick(.away, 21 + i, at: 7001 + Double(i * 2), id: "a\(i)")) }
        }
        let s = play(rules, secondSinBinHome7() + [cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300),
                                                   cmd(.setShootoutFirstTeam(.home), at: 6900)] + kicks)
        #expect(s.phase == .penalties)
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.resolvedEvents.first { $0.event.id == "h6" }?.warnings == [])
    }

    @Test func sinBinOnSentOffPlayerDoesNotReduce() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), card(.home, 5, .red, at: 100), card(.home, 5, .sinBin, at: 200, id: "x")])
        #expect(s.resolvedEvents.first { $0.event.id == "x" }?.warnings == [.playerAlreadySentOff])
        #expect(s.activeSinBins(at: 300).isEmpty)
        #expect(s.playersOnField(at: 300) == TeamPair(home: 10, away: 11))
    }

    @Test func sinBinOnBarredPlayerDoesNotReduce() {
        let s = play(sinBinRules, secondSinBinHome7() + [card(.home, 7, .sinBin, at: 1500, id: "x")])
        let x = s.resolvedEvents.first { $0.event.id == "x" }
        #expect(x?.warnings == [.overlappingSinBin])
        #expect(x?.isSecondSinBin == false)
        #expect(s.activeSinBins(at: 1600).isEmpty)
        #expect(s.barredPlayers(at: 1600).map(\.number) == [7])
        #expect(s.playersOnField(at: 1600) == TeamPair(home: 10, away: 11))
    }

    @Test func sinBinOnSubstitutedOffPlayerDoesNotReduce() {
        let s = play(sinBinRules, [cmd(.startPeriod, at: 0), subst(.home, out: 5, into: 12, at: 100), card(.home, 5, .sinBin, at: 200, id: "x"),
                                   card(.home, 5, .sinBin, at: 1000, id: "y")])
        #expect(s.resolvedEvents.first { $0.event.id == "x" }?.warnings == [.playerOffPitch])
        // 교체로 나간 뒤의 일시 퇴장은 첫 번째로도 세지 않는다
        #expect(s.resolvedEvents.first { $0.event.id == "y" }?.isSecondSinBin == false)
        #expect(s.activeSinBins(at: 300).isEmpty)
        #expect(s.playersOnField(at: 300) == TeamPair(home: 11, away: 11))
    }
}
