import Testing
@testable import RefereeCore

/// 최종 리뷰에서 나온 실제 경기 시나리오 결함
struct ReviewFixTests {
    @Test func sinBinnedPlayerLaterSentOffIsCountedOnce() {
        let s = play(RuleSet(sinBin: true), [cmd(.startPeriod, at: 0), card(.home, 5, .sinBin, at: 100), card(.home, 5, .red, at: 160)])
        #expect(s.playersOnField(at: 200) == TeamPair(home: 10, away: 11))
        #expect(s.activeSinBins(at: 200).isEmpty)
        #expect(s.alerts(after: 200, warningBeforeEnd: 0, breakWarningBeforeEnd: 300).allSatisfy { $0.kind != .sinBinEnd })
    }

    @Test func redShownAfterSecondYellowIsNotASecondSendOff() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, 5, .yellow, at: 100), card(.home, 5, .yellow, at: 200),
                      card(.home, 5, .red, at: 201, id: "r")])
        #expect(s.sentOffCounts == TeamPair(home: 1, away: 0))
        #expect(s.playersOnField(at: 300) == TeamPair(home: 10, away: 11))
        #expect(s.resolvedEvents.last?.warnings == [.playerAlreadySentOff])
    }

    @Test func thirdYellowForDismissedPlayerIsFlaggedNotCounted() {
        let s = play([cmd(.startPeriod, at: 0), card(.home, 5, .yellow, at: 100), card(.home, 5, .yellow, at: 200),
                      card(.home, 5, .yellow, at: 300)])
        #expect(s.resolvedEvents.map(\.kind) == [.yellow, .secondYellow, .yellow])
        #expect(s.resolvedEvents.last?.warnings == [.playerAlreadySentOff])
        #expect(s.sentOffCounts == TeamPair(home: 1, away: 0))
    }

    @Test func goalCannotBeDeletedAfterFullTimeDecision() {
        // 2:1 종료 후 골 삭제로 1:1이 되면 "1:1 completed"라는 모순 기록이 된다
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, id: "g1"), goal(.home, at: 200), goal(.away, at: 300),
                      cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300),
                      cmd(.deleteEvent(id: "g1"), at: 6400, id: "d")])
        #expect(s.ignoredCommandIDs == ["d"])
        #expect(s.score == TeamPair(home: 2, away: 1))
    }

    @Test func goalTeamCannotChangeAfterExtraTimeWasDecided() {
        let s = play(RuleSet(extraTime: true, penalties: true),
                     [cmd(.startPeriod, at: 0), goal(.home, at: 100), goal(.away, at: 200, id: "a"),
                      cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300),
                      cmd(.editEvent(id: "a", .goal(team: .home, number: nil, ownGoal: false)), at: 6400, id: "e")])
        #expect(s.phase == .preExtraTime)
        #expect(s.ignoredCommandIDs == ["e"])
    }

    @Test func goalNumberCanStillBeCorrectedAfterFullTime() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, id: "g"), cmd(.endPeriod, at: 2700),
                      cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300),
                      cmd(.editEvent(id: "g", .goal(team: .home, number: 10, ownGoal: false)), at: 6400)])
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.events[0].payload == .goal(team: .home, number: 10, ownGoal: false))
    }

    @Test func goalCanBeCorrectedDuringHalfTime() {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 100, id: "g"), cmd(.endPeriod, at: 2700),
                      cmd(.deleteEvent(id: "g"), at: 2800)])
        #expect(s.ignoredCommandIDs.isEmpty)
        #expect(s.score == TeamPair(home: 0, away: 0))
    }
}
