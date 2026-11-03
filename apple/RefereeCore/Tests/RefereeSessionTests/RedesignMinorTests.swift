import Testing
import Foundation
@testable import RefereeSession
import RefereeCore

/// 디자인 개편 리뷰에서 남은 작은 문제들: 이번 달 거리 글자, 목록 날짜 연도, 시즌 통계 표 줄
@Suite struct RedesignMinorTests {
    private func summary(_ start: Double, outcome: ReportOutcome = .completed, goals: Int = 0, yellows: Int = 0,
                         added: Int = 0, distance: Double? = nil, sprints: Int? = nil) -> MatchSummary {
        var s = MatchSummary(record: makeRecord([], createdAt: start))
        s.startedAt = start
        s.outcome = outcome
        s.goals = goals; s.yellows = yellows; s.reds = 0; s.sinBins = 0
        s.addedSeconds = added
        s.distance = distance
        s.sprints = sprints
        return s
    }

    // MARK: 1. 이번 달 요약 거리

    /// 정수 반올림이면 0.4km가 "0"으로 보였다: 10km 미만은 소수 한 자리
    @Test func monthDistanceUnderTenShowsOneDecimal() {
        #expect(MonthSummary.distanceText(meters: 400) == "0.4")
        #expect(MonthSummary.distanceText(meters: 9800) == "9.8")
        #expect(MonthSummary.distanceText(meters: 0) == "0.0")
        #expect(MonthSummary.distanceText(meters: 1049) == "1.0")
    }

    @Test func monthDistanceTenOrMoreIsWhole() {
        #expect(MonthSummary.distanceText(meters: 10_000) == "10")
        #expect(MonthSummary.distanceText(meters: 42_600) == "43")
        // 9.96km는 한 자리로 올리면 10.0이 되므로 정수 "10"
        #expect(MonthSummary.distanceText(meters: 9_960) == "10")
    }

    // MARK: 2. 목록 날짜 연도

    @Test func yearShownOnlyForOtherYears() {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 16, hour: 12))!
        let jan1 = cal.date(from: DateComponents(year: 2026, month: 1, day: 1))!.timeIntervalSince1970
        let dec3 = cal.date(from: DateComponents(year: 2025, month: 12, day: 3, hour: 19))!.timeIntervalSince1970
        #expect(!MatchListItem.showsYear(startedAt: jan1, now: now, calendar: cal))
        #expect(MatchListItem.showsYear(startedAt: jan1 - 1, now: now, calendar: cal))   // 2025년 12월 31일 23:59:59
        #expect(MatchListItem.showsYear(startedAt: dec3, now: now, calendar: cal))
        // 기기 시계가 틀려 내년 경기가 와도 연도를 붙인다
        let next = cal.date(from: DateComponents(year: 2027, month: 2, day: 1))!.timeIntervalSince1970
        #expect(MatchListItem.showsYear(startedAt: next, now: now, calendar: cal))
    }

    // MARK: 6. 시즌 통계 표 줄

    @Test func addedTimeAndDistanceHaveTotalAndPerMatch() {
        let stats = SeasonStats.aggregate([
            summary(1, goals: 2, yellows: 1, added: 240, distance: 10_000, sprints: 20),
            summary(2, goals: 1, added: 120, distance: 8_000, sprints: 10),
            summary(3, outcome: .abandoned("비"), added: 60, distance: 1_000, sprints: 1),
        ])
        let lines = stats.lines
        #expect(lines.map(\.kind) == [.goals, .yellows, .reds, .addedTime, .distance, .sprints])
        let added = lines.first { $0.kind == .addedTime }!
        #expect(added.total == 420)        // 중단 경기까지 합계
        #expect(added.perMatch == 180)     // 완료 경기만 평균
        let distance = lines.first { $0.kind == .distance }!
        #expect(distance.total == 19_000)
        #expect(distance.perMatch == 9_000)
        #expect(lines.first { $0.kind == .goals }! == StatLine(kind: .goals, total: 3, perMatch: 1.5))
    }

    @Test func linesWithoutWorkoutOrCompletedMatches() {
        // 중단 경기뿐: 합계는 있고 경기당은 없다 ("--")
        let stats = SeasonStats.aggregate([summary(1, outcome: .abandoned("x"), added: 90)])
        let lines = stats.lines
        #expect(lines.map(\.kind) == [.goals, .yellows, .reds, .addedTime])   // 운동 기록이 없으면 거리·스프린트 줄이 없다
        #expect(lines.first { $0.kind == .addedTime }! == StatLine(kind: .addedTime, total: 90, perMatch: nil))
        #expect(lines.allSatisfy { $0.perMatch == nil })
    }

    @Test func sinBinLineOnlyWhenAny() {
        var s = summary(1)
        s.sinBins = 2
        #expect(SeasonStats.aggregate([s]).lines.map(\.kind) == [.goals, .yellows, .reds, .sinBins, .addedTime])
    }

    // MARK: 7. 시즌 통계 critique (2026-11-03): 평균 심박 줄·경기당 열·기간 경기

    @Test func heartRateLineHasOnlyPerMatch() {
        var a = summary(1), b = summary(2), c = summary(3, outcome: .abandoned("비"))
        a.averageHeartRate = 140; b.averageHeartRate = 151; c.averageHeartRate = 100
        let lines = SeasonStats.aggregate([a, b, c]).lines
        #expect(lines.last == StatLine(kind: .heartRate, total: nil, perMatch: 146))   // 완료 경기만, 반올림
        #expect(!SeasonStats.aggregate([summary(1)]).lines.contains { $0.kind == .heartRate })
    }

    @Test func perMatchColumnOnlyWithTwoCompletedMatches() {
        #expect(!SeasonStats.aggregate([summary(1)]).showsPerMatch)
        #expect(!SeasonStats.aggregate([summary(1), summary(2, outcome: .abandoned("x"))]).showsPerMatch)
        #expect(SeasonStats.aggregate([summary(1), summary(2)]).showsPerMatch)
    }

    @Test func matchesInRangeKeepsOrderAndBounds() {
        let all = [summary(30), summary(20), summary(10)]
        #expect(SeasonStats.matches(all, from: 10, to: 30).map(\.startedAt) == [20, 10])   // to는 빼고 from은 넣는다
        #expect(SeasonStats.matches(all).count == 3)
    }
}
