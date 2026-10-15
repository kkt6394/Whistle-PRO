import Testing
import RefereeCore
@testable import RefereeSession

/// GPS는 경기 시간(기간 진행 중)에만 켠다 (2026-10-15 사용자 결정: 하프타임·승부차기·종료 후에는 배터리만 쓴다)
struct RouteTrackingTests {
    @Test func onlyWhilePeriodRuns() {
        #expect(!makeState([]).tracksRoute)
        #expect(makeState([(.startPeriod, 0)]).tracksRoute)
        #expect(!makeState([(.startPeriod, 0), (.endPeriod, 2700)]).tracksRoute)
        #expect(makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600)]).tracksRoute)
    }

    @Test func offDuringPenaltiesAndAfterFinish() {
        let rules = RuleSet(penalties: true)
        let toPenalties: [(CommandKind, Double)] = [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)]
        #expect(!makeState(rules, toPenalties).tracksRoute)
        #expect(!makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600),
                            (.recordEvent(.goal(team: .home, number: nil, ownGoal: false)), 4000), (.endPeriod, 6300)]).tracksRoute)
    }
}
