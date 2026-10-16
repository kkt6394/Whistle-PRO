import Testing
@testable import RefereeCore

private let pkRules = RuleSet(penalties: true)

private func kick(_ team: Team, _ scored: Bool, at t: Double, number: Int? = nil, id: String) -> Command {
    cmd(.recordEvent(.penaltyKick(team: team, number: number, scored: scored)), at: t, id: id)
}

/// first 팀부터 번갈아 찬다. results[i]는 i번째 킥 성공 여부, ID는 "k0", "k1", ...
private func kicks(first: Team, _ results: [Bool]) -> [Command] {
    results.enumerated().map { i, scored in
        kick(i % 2 == 0 ? first : first.opponent, scored, at: 7000 + Double(i), id: "k\(i)")
    }
}

private func shootout(first: Team, _ results: [Bool], rules: RuleSet = pkRules) -> MatchState {
    play(rules, twoHalves() + [cmd(.setShootoutFirstTeam(first), at: 6900)] + kicks(first: first, results))
}

struct ShootoutTests {
    @Test func kickBeforeChoosingFirstTeamIsIgnored() {
        let s = play(pkRules, twoHalves() + [kick(.home, true, at: 7000, id: "k")])
        #expect(s.ignoredCommandIDs == ["k"])
    }

    @Test func firstTeamCannotChangeAfterFirstKick() {
        let s = play(pkRules, twoHalves() + [cmd(.setShootoutFirstTeam(.home), at: 6900), kick(.home, true, at: 7000, id: "k0"),
                                             cmd(.setShootoutFirstTeam(.away), at: 7001, id: "late")])
        #expect(s.ignoredCommandIDs == ["late"])
    }

    @Test func teamsMustAlternate() {
        let s = play(pkRules, twoHalves() + [cmd(.setShootoutFirstTeam(.away), at: 6900), kick(.home, true, at: 7000, id: "wrong")])
        #expect(s.ignoredCommandIDs == ["wrong"])
        #expect(s.shootout.next == .away)
    }

    @Test func notDecidedWhileTrailingTeamCanStillCatchUp() {
        // home O O O, away X X → 3:0, away 3회 남음
        let s = shootout(first: .home, [true, false, true, false, true])
        #expect(!s.shootout.decided)
        #expect(s.phase == .penalties)
        #expect(s.shootout.next == .away)
    }

    @Test func endsAsSoonAsOneTeamCannotCatchUp() {
        let s = shootout(first: .home, [true, false, true, false, true, false])
        #expect(s.shootout.goals == TeamPair(home: 3, away: 0))
        #expect(s.shootout.decided)
        #expect(s.shootout.next == nil)
        #expect(s.phase == .finished(.completed))
    }

    @Test func suddenDeathAfterRegulationKicks() {
        let tiedFive = Array(repeating: true, count: 10)
        let afterOne = shootout(first: .home, tiedFive + [true])
        #expect(!afterOne.shootout.decided)

        let done = shootout(first: .home, tiedFive + [true, false])
        #expect(done.shootout.goals == TeamPair(home: 6, away: 5))
        #expect(done.phase == .finished(.completed))
    }

    @Test func kicksPerTeamIsConfigurable() {
        // 3인 승부차기: home O O, away X X → 2:0, away 1회 남음 → 결정
        let s = shootout(first: .home, [true, false, true, false], rules: RuleSet(penalties: true, penaltyKicksPerTeam: 3))
        #expect(s.phase == .finished(.completed))
    }

    @Test func shootoutGoalsDoNotChangeMatchScore() {
        let s = shootout(first: .home, [true, false, true, false, true, false])
        #expect(s.score == TeamPair(home: 0, away: 0))
    }

    @Test func repeatKickerBeforeEveryoneHasKickedWarns() throws {
        let rules = RuleSet(penalties: true, playersPerTeam: 3, minimumPlayers: nil)
        let s = play(rules, twoHalves() + [
            cmd(.setShootoutFirstTeam(.home), at: 6900),
            kick(.home, true, at: 7000, number: 1, id: "h1"), kick(.away, true, at: 7001, number: 7, id: "a1"),
            kick(.home, true, at: 7002, number: 2, id: "h2"), kick(.away, true, at: 7003, number: 8, id: "a2"),
            kick(.home, true, at: 7004, number: 1, id: "repeat"),
        ])
        let repeated = try #require(s.resolvedEvents.first { $0.event.id == "repeat" })
        #expect(repeated.warnings == [.repeatPenaltyKicker])
        #expect(s.resolvedEvents.filter { $0.event.id != "repeat" }.allSatisfy { $0.warnings.isEmpty })
    }

    @Test func repeatKickerAfterEveryoneHasKickedDoesNotWarn() {
        // 자격 있는 선수 3명이 모두 찬 뒤에는 처음 찬 선수가 다시 차도 된다 (경계: 찬 인원 == 자격 인원)
        let rules = RuleSet(penalties: true, playersPerTeam: 3, minimumPlayers: nil)
        let s = play(rules, twoHalves() + [
            cmd(.setShootoutFirstTeam(.home), at: 6900),
            kick(.home, true, at: 7000, number: 1, id: "h1"), kick(.away, true, at: 7001, number: 7, id: "a1"),
            kick(.home, true, at: 7002, number: 2, id: "h2"), kick(.away, true, at: 7003, number: 8, id: "a2"),
            kick(.home, true, at: 7004, number: 3, id: "h3"), kick(.away, true, at: 7005, number: 9, id: "a3"),
            kick(.home, true, at: 7006, number: 1, id: "again"),
        ])
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.resolvedEvents.count == 7)
        #expect(s.resolvedEvents.allSatisfy { $0.warnings.isEmpty })
    }

    @Test func onlyLastKickCanBeDeleted() {
        let base = twoHalves() + [cmd(.setShootoutFirstTeam(.home), at: 6900)] + kicks(first: .home, [true, false])
        let middle = play(pkRules, base + [cmd(.deleteEvent(id: "k0"), at: 7100, id: "d")])
        #expect(middle.ignoredCommandIDs == ["d"])

        let last = play(pkRules, base + [cmd(.deleteEvent(id: "k1"), at: 7100)])
        #expect(last.shootout.kicks == TeamPair(home: 1, away: 0))
        #expect(last.shootout.next == .away)
    }

    @Test func editingKickResultCanDecideShootout() {
        // home O X X, away X X X → 1:0 (미결정). k2·k4(home 2·3번째)를 성공으로 고치면 3:0 → 결정.
        let base = twoHalves() + [cmd(.setShootoutFirstTeam(.home), at: 6900)] + kicks(first: .home, [true, false, false, false, false, false])
        #expect(play(pkRules, base).phase == .penalties)

        let edited = play(pkRules, base + [
            cmd(.editEvent(id: "k2", .penaltyKick(team: .home, number: nil, scored: true)), at: 7100),
            cmd(.editEvent(id: "k4", .penaltyKick(team: .home, number: nil, scored: true)), at: 7101),
        ])
        #expect(edited.shootout.goals == TeamPair(home: 3, away: 0))
        #expect(edited.phase == .finished(.completed))
    }

    @Test func editingKickTeamIsRejected() {
        let base = twoHalves() + [cmd(.setShootoutFirstTeam(.home), at: 6900)] + kicks(first: .home, [true])
        let s = play(pkRules, base + [cmd(.editEvent(id: "k0", .penaltyKick(team: .away, number: nil, scored: true)), at: 7100, id: "e")])
        #expect(s.ignoredCommandIDs == ["e"])
    }
}
