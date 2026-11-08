import Testing
import RefereeCore
@testable import RefereeSession

struct ReportLayoutTests {
    private let match: [(CommandKind, Double)] = [
        (.startPeriod, 0),
        (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 600),
        (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 900),
        (.toggleStoppage, 1000), (.toggleStoppage, 1090),
        (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 1200),
        (.endPeriod, 2820),
        (.startPeriod, 3720),
        (.recordEvent(.substitution(team: .home, numberOut: 7, numberIn: 14)), 4000),
        (.recordEvent(.goal(team: .away, number: 5, ownGoal: true)), 5000),
        (.endPeriod, 6420),
    ]

    private func report(_ commands: [(CommandKind, Double)], _ setup: MatchSetup = MatchSetup()) -> MatchReport {
        MatchReport(bundle: MatchBundle(record: makeRecord(setup, commands), workout: nil))
    }

    @Test func periodScoresSplitByPeriod() {
        // 전반: 홈 골 1, 후반: 원정 자책골 → 홈 득점
        #expect(report(match).periodScores == [TeamPair(home: 1, away: 0), TeamPair(home: 1, away: 0)])
    }

    /// 휴식 중에는 골을 기록할 수 없다(엔진이 막는다). 교체·카드만 들어가고 기간 점수에는 영향이 없다.
    @Test func breakEventsDoNotAffectPeriodScores() {
        let commands: [(CommandKind, Double)] = [
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .away, number: 3, ownGoal: false)), 100),
            (.endPeriod, 2700),
            (.recordEvent(.substitution(team: .home, numberOut: 1, numberIn: 2)), 2750),
            (.startPeriod, 3600), (.endPeriod, 6300)]
        #expect(report(commands).periodScores == [TeamPair(home: 0, away: 1), TeamPair(home: 0, away: 0)])
    }

    @Test func shootoutGoalsNotInPeriodScores() {
        let commands: [(CommandKind, Double)] = [
            (.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
            (.setShootoutFirstTeam(.home), 6400),
            (.recordEvent(.penaltyKick(team: .home, number: nil, scored: true)), 6410),
            (.recordEvent(.penaltyKick(team: .away, number: nil, scored: false)), 6420)]
        let r = report(commands, MatchSetup(rules: RuleSet(penalties: true)))
        #expect(r.periodScores == [TeamPair(home: 0, away: 0), TeamPair(home: 0, away: 0)])
    }

    @Test func totalsCountCardsAndSubs() {
        let r = report(match)
        #expect(r.totals.home == ReportTotals(goals: 2, yellows: 0, dismissals: 0, substitutions: 1))
        #expect(r.totals.away == ReportTotals(goals: 0, yellows: 2, dismissals: 1, substitutions: 0))
    }

    @Test func ownGoalCountsForOpponent() {
        let r = report(match)
        #expect(r.columnRows.compactMap(\.home).map(\.kind) == [.goal, .substitution, .ownGoal])
        #expect(r.columnRows.compactMap(\.away).map(\.kind) == [.yellow, .secondYellow])
        #expect(r.columnRows.count == 3)
    }

    @Test func columnRowsEmptyWhenNoEvents() {
        #expect(report([(.startPeriod, 0), (.endPeriod, 2700)]).columnRows.isEmpty)
    }

    /// 골 줄: 골·자책골만, 4개까지 한 줄, 5개부터 두 줄(위가 같거나 하나 많게), 8개 넘으면 7개 + 나머지 개수
    @Test func cardGoalRows() {
        let goal = ReportLine(minute: .regular(1), team: .home, kind: .goal, detail: "#1")
        let yellow = ReportLine(minute: .regular(2), team: .away, kind: .yellow, detail: "#4")
        let own = ReportLine(minute: .regular(3), team: .away, kind: .ownGoal, detail: "#5")
        #expect(SNSCardLayout.goalRows([goal, yellow, own]).rows.map { $0.map(\.kind) } == [[.goal, .ownGoal]])
        #expect(SNSCardLayout.goalRows([yellow]).rows.isEmpty)
        func counts(_ n: Int) -> ([Int], Int) {
            let split = SNSCardLayout.goalRows(Array(repeating: goal, count: n))
            return (split.rows.map(\.count), split.more)
        }
        #expect(counts(4) == ([4], 0))
        #expect(counts(5) == ([3, 2], 0))
        #expect(counts(8) == ([4, 4], 0))
        #expect(counts(11) == ([4, 3], 4))
    }

    /// 일반 지도 바탕: 히트맵 범위가 길쭉하든 넓적하든 카드 칸(152~415pt) 안에, 칸 가운데에
    @Test func cardMapFrameKeepsHeatInsideBox() {
        for (northSouth, eastWest) in [(110.0, 70.0), (40.0, 20.0), (60.0, 200.0), (300.0, 30.0)] {
            let frame = SNSCardLayout.mapFrame(northSouthMeters: northSouth, eastWestMeters: eastWest)
            // 히트맵 가운데의 화면 높이 = 카드 가운데 - 남쪽으로 옮긴 거리 / (m/pt)
            let center = SNSCardLayout.height / 2 - frame.southShift / frame.metersPerPoint
            let halfHeight = northSouth * 1.3 / 2 / frame.metersPerPoint
            let halfWidth = eastWest * 1.3 / 2 / frame.metersPerPoint
            #expect(abs(center - (SNSCardLayout.boxTop + SNSCardLayout.boxBottom) / 2) < 1e-9)
            #expect(center - halfHeight >= SNSCardLayout.boxTop - 1e-9)
            #expect(center + halfHeight <= SNSCardLayout.boxBottom + 1e-9)
            #expect(halfWidth * 2 <= SNSCardLayout.width + 1e-9)
        }
    }
}
