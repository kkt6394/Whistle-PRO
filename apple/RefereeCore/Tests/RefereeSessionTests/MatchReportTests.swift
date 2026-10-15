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
            PeriodSummary(label: "1H", played: 2820, stoppage: 90, added: 120),
            PeriodSummary(label: "2H", played: 2700, stoppage: 0, added: 0),
        ])
        #expect(report.ruleDeviations.isEmpty)
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
}
