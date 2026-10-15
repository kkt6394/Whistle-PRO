import Testing
@testable import RefereeCore

/// 경기가 끝난 시각 (백로그: 종료 후 5분 안에 앱이 다시 실행되면 운동 종료 시각이 재실행 시각이 됐다)
struct FinishedAtTests {
    private func kick(_ team: Team, _ scored: Bool, at t: Double) -> Command {
        cmd(.recordEvent(.penaltyKick(team: team, number: nil, scored: scored)), at: t)
    }

    @Test func nilUntilFinished() {
        #expect(play([cmd(.startPeriod, at: 0)]).finishedAt == nil)
        #expect(play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700)]).finishedAt == nil)
    }

    @Test func lastPeriodEnd() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6400)])
        #expect(s.finishedAt == 6400)
    }

    @Test func abandonTime() {
        #expect(play([cmd(.startPeriod, at: 0), cmd(.abandonMatch(reason: "x"), at: 1000)]).finishedAt == 1000)
    }

    @Test func decisivePenaltyKick() {
        var commands = [cmd(.startPeriod, at: 0), cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6300),
                        cmd(.setShootoutFirstTeam(.home), at: 6400)]
        var t = 6500.0
        for _ in 0..<3 {
            commands.append(kick(.home, true, at: t)); t += 10
            commands.append(kick(.away, false, at: t)); t += 10
        }
        let s = play(RuleSet(penalties: true), commands)
        // 홈 3골(6540)에는 원정이 남은 셋을 다 넣으면 3:3이 될 수 있다 → 원정 세 번째 실축(6550)에 끝난다
        #expect(s.finishedAt == 6550)
    }

    /// 끝낸 뒤 기록을 고쳐도 끝난 시각은 그대로
    @Test func laterEditKeepsFinishTime() {
        let s = play([cmd(.startPeriod, at: 0), cmd(.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), at: 100, id: "g"),
                      cmd(.endPeriod, at: 2700), cmd(.startPeriod, at: 3600), cmd(.endPeriod, at: 6400),
                      cmd(.editEvent(id: "g", .goal(team: .home, number: 10, ownGoal: false)), at: 7000)])
        #expect(s.finishedAt == 6400)
    }
}
