import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct MatchReportTests {
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

    /// 백로그: 공유 파일 이름이 "WhistlePRO-1791945042"처럼 숫자였다 → 날짜와 팀 이름
    @Test func fileStemHasDateAndTeams() {
        let setup = MatchSetup(homeName: "FC 서울숲", awayName: "한강/유나이티드:")
        // 2026-10-15 13:00 UTC
        let report = MatchReport(bundle: MatchBundle(record: makeRecord(setup, [(.startPeriod, 1_792_069_200)]), workout: nil))
        #expect(report.fileStem(timeZone: TimeZone(identifier: "Asia/Seoul")!) == "WhistlePRO 2026-10-15 FC 서울숲 vs 한강-유나이티드-")
        #expect(report.fileStem(timeZone: TimeZone(identifier: "America/Los_Angeles")!).hasPrefix("WhistlePRO 2026-10-15 "))
    }

    /// 제출용 보고서의 "경기 방식" 줄은 기간 수·길이·연장·승부차기를 규칙에서 읽는다
    @Test func reportCarriesRules() {
        let rules = RuleSet(periodCount: 4, periodLength: 1500, extraTime: true, penalties: true)
        let report = MatchReport(bundle: MatchBundle(record: makeRecord(MatchSetup(rules: rules), [(.startPeriod, 0)]), workout: nil))
        #expect(report.rules == rules)
    }

    @Test func reportSummarizesMatch() {
        let report = MatchReport(bundle: MatchBundle(record: makeRecord(match, createdAt: -10), workout: nil))
        #expect(report.outcome == .completed)
        #expect(report.score == TeamPair(home: 2, away: 0))
        #expect(report.shootout == nil)
        #expect(report.startedAt == 0)
        #expect(report.goals.map(\.minute) == [.regular(11), .regular(67)])
        #expect(report.goals.map(\.kind) == [.goal, .ownGoal])
        #expect(report.discipline.map(\.kind) == [.yellow, .secondYellow])
        #expect(report.substitutions.map(\.detail) == ["#7 → #14"])
        #expect(report.periods == [
            PeriodSummary(label: "1H", played: 2820, stoppage: 90, added: 120, nominal: 2700),
            PeriodSummary(label: "2H", played: 2700, stoppage: 0, added: 0, nominal: 2700),
        ])
        #expect(report.ruleDeviations.isEmpty)
    }

    /// 경고 1장 있던 선수에게 준 레드만 표시한다 (두 번째 경고를 레드로 기록했을 수 있어 폰 사유 맨 위에 "두 번째 경고", 2026-11-04)
    @Test func redAfterCautionIsMarked() {
        let commands: [(CommandKind, Double)] = [
            (.startPeriod, 0),
            (.recordEvent(.card(team: .home, number: 4, kind: .yellow)), 100),
            (.recordEvent(.card(team: .home, number: 4, kind: .red)), 200),   // 경고 뒤 레드 → 표시
            (.recordEvent(.card(team: .away, number: 4, kind: .red)), 300),   // 다른 팀 같은 번호 → 아님
            (.recordEvent(.card(team: .home, number: 8, kind: .red)), 400),   // 경고 없던 선수 → 아님
            (.recordEvent(.card(team: .home, number: nil, kind: .red)), 500),              // 번호 없음 → 아님
        ]
        let record = makeRecord(commands)
        let report = MatchReport(bundle: MatchBundle(record: record, workout: nil))
        let reds = report.discipline.filter { $0.kind == .red }
        #expect(reds.count == 4)
        #expect(report.redsAfterCaution == [reds[0].eventID])
    }

    @Test func reportWithoutWorkout() {
        let report = MatchReport(bundle: MatchBundle(record: makeRecord(match), workout: nil))
        #expect(report.workoutOverall == nil)
        #expect(report.workoutPeriods.isEmpty)
    }

    @Test func reportWithWorkoutSplitsPeriods() {
        let record = makeRecord(match)
        var workout = WorkoutLog(matchID: record.id)
        workout.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2820, value: 5000), WorkoutSample(t: 6420, value: 9800)]
        let report = MatchReport(bundle: MatchBundle(record: record, workout: workout))
        #expect(report.workoutPeriods.map(\.distance) == [5000, 4800])
        #expect(report.workoutOverall?.distance == 9800)
    }

    @Test func abandonedOutcome() {
        let report = MatchReport(bundle: MatchBundle(
            record: makeRecord([(.startPeriod, 0), (.abandonMatch(reason: "폭우"), 1200)]), workout: nil))
        #expect(report.outcome == .abandoned("폭우"))
        #expect(report.periods.map(\.played) == [1200])
    }

    @Test func shootoutInReport() {
        let setup = MatchSetup(rules: RuleSet(penalties: true))
        let commands: [(CommandKind, Double)] = [
            (.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
            (.setShootoutFirstTeam(.home), 6400),
            (.recordEvent(.penaltyKick(team: .home, number: nil, scored: true)), 6410),
            (.recordEvent(.penaltyKick(team: .away, number: nil, scored: false)), 6420),
        ]
        let report = MatchReport(bundle: MatchBundle(record: makeRecord(setup, commands), workout: nil))
        #expect(report.shootout == TeamPair(home: 1, away: 0))
        #expect(report.ruleDeviations == [RuleLine(field: .penalties, value: "true")])
    }

/// 전후반 2–2, 하프타임 중 카드 1(엔진은 휴식 중 골을 거절한다), 자책골 1 (타임라인 줄마다 기간·초·점수)
private let flow: [(CommandKind, Double)] = [
    (.startPeriod, 0),
    (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 700),        // 12' 1–0
    (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 1350),
    (.recordEvent(.goal(team: .away, number: 11, ownGoal: false)), 2150),      // 36' 1–1
    (.endPeriod, 2820),                                                          // 전반 47:00
    (.recordEvent(.card(team: .home, number: 10, kind: .yellow)), 3000),       // 휴식 중 카드
    (.startPeriod, 3720),
    (.recordEvent(.goal(team: .away, number: 5, ownGoal: true)), 4800),        // 자책골 → 홈 2–1
    (.recordEvent(.goal(team: .away, number: 11, ownGoal: false)), 6300),      // 2–2
    (.endPeriod, 6500),
]

@Test func timelineCarriesPeriodSecondAndScore() {
    let report = MatchReport(bundle: MatchBundle(record: makeRecord(flow), workout: nil))
    #expect(report.timeline.map(\.period) == [0, 0, 0, 0, 1, 1])
    #expect(report.timeline.map(\.second) == [700, 1350, 2150, 2820, 1080, 2580])
    #expect(report.timeline.map(\.scoreAfter) == [
        TeamPair(home: 1, away: 0), nil, TeamPair(home: 1, away: 1), nil,
        TeamPair(home: 2, away: 1), TeamPair(home: 2, away: 2)])
    #expect(report.timeline.last?.scoreAfter == report.score)
    #expect(report.periods.map(\.nominal) == [2700, 2700])
    #expect(report.scoreBefore(period: 0) == TeamPair(home: 0, away: 0))
    #expect(report.scoreBefore(period: 1) == TeamPair(home: 1, away: 1))
}

@Test func shootoutDoesNotChangeScoreAfter() {
    let setup = MatchSetup(rules: RuleSet(penalties: true))
    let commands: [(CommandKind, Double)] = [
        (.startPeriod, 0), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 600), (.endPeriod, 2700),
        (.startPeriod, 3600), (.recordEvent(.goal(team: .away, number: 11, ownGoal: false)), 5000), (.endPeriod, 6300),
        (.setShootoutFirstTeam(.home), 6400),
        (.recordEvent(.penaltyKick(team: .home, number: nil, scored: true)), 6410),
        (.recordEvent(.penaltyKick(team: .away, number: nil, scored: false)), 6420)]
    let report = MatchReport(bundle: MatchBundle(record: makeRecord(setup, commands), workout: nil))
    #expect(report.timeline.map(\.kind) == [.goal, .goal])                 // 승부차기 킥은 timeline에 없다
    #expect(report.timeline.last?.scoreAfter == TeamPair(home: 1, away: 1))
    #expect(report.timeline.last?.scoreAfter == report.score)              // 승부차기 1:0은 score에 안 섞인다
    #expect(report.shootout == TeamPair(home: 1, away: 0))
}

@Test func extraTimePeriodsIndexFrom2() {
    let setup = MatchSetup(rules: RuleSet(extraTime: true))
    let commands: [(CommandKind, Double)] = [
        (.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
        (.startPeriod, 6600), (.recordEvent(.card(team: .home, number: 3, kind: .yellow)), 6900), (.endPeriod, 7500),
        (.startPeriod, 7800), (.endPeriod, 8700)]
    let report = MatchReport(bundle: MatchBundle(record: makeRecord(setup, commands), workout: nil))
    #expect(report.timeline.map(\.period) == [2])
    #expect(report.periods.map(\.nominal) == [2700, 2700, 900, 900])
}
}
