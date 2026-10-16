import Testing
import Foundation
@testable import RefereeSession
import RefereeCore

/// 아이폰 디자인 개편: 목록 줄 값, 이번 달 요약, 요약 v4, 상세 탭
@Suite struct PhoneRedesignTests {
    private func summary(_ start: Double, goals: Int = 0, yellows: Int = 0, reds: Int = 0,
                         distance: Double? = nil, shootout: [Int]? = nil,
                         outcome: ReportOutcome = .completed) -> MatchSummary {
        var s = MatchSummary(record: makeRecord([]))
        s.startedAt = start
        s.goals = goals; s.yellows = yellows; s.reds = reds; s.distance = distance
        s.shootout = shootout; s.outcome = outcome
        s.homeScore = 2; s.awayScore = 1
        return s
    }

    @Test func chipsSkipZeroAndMissingDistance() {
        let item = MatchListItem(summary(0, goals: 3, yellows: 0, reds: 1))
        #expect(item.chips == [.goals(3), .reds(1)])
    }

    @Test func chipsIncludeShootoutFirstAndDistance() {
        let item = MatchListItem(summary(0, goals: 0, distance: 9800, shootout: [4, 3]))
        #expect(item.chips == [.shootout(home: 4, away: 3), .distance(meters: 9800)])
    }

    @Test func zeroDistanceIsNotAChip() {
        #expect(MatchListItem(summary(0, distance: 0)).chips == [])
    }

    @Test func abandonedFlag() {
        #expect(MatchListItem(summary(0, outcome: .abandoned("심판 판단"))).abandoned)
        #expect(!MatchListItem(summary(0)).abandoned)
    }

    @Test func ruleTextFromV4AndNilForOld() {
        var s = summary(0)
        #expect(MatchListItem(s).ruleText! == (2, 45))   // MatchSummary(record:)가 FIFA 표준 규칙을 채운다
        s.periodCount = 4; s.periodMinutes = 25
        #expect(MatchListItem(s).ruleText! == (4, 25))
        s.periodCount = nil; s.periodMinutes = nil
        #expect(MatchListItem(s).ruleText == nil)
    }

    @Test func monthBoundaries() {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let now = cal.date(from: DateComponents(year: 2026, month: 10, day: 16, hour: 12))!
        let oct1 = cal.date(from: DateComponents(year: 2026, month: 10, day: 1))!.timeIntervalSince1970
        let summaries = [
            summary(oct1 - 1, yellows: 5, distance: 10000),          // 9월 30일 23:59:59 → 빠짐
            summary(oct1, yellows: 2, distance: 12000),              // 10월 1일 0시 → 포함
            summary(oct1 + 86_400 * 31, yellows: 9, distance: 9000), // 11월 → 빠짐
            summary(oct1 + 3600, yellows: 1, reds: 1, distance: 0, outcome: .abandoned("x")),
        ]
        let m = MonthSummary.of(summaries, now: now, calendar: cal)
        #expect(m.matches == 2)
        #expect(m.distance == 12000)
        #expect(m.cardsPerMatch == 2.0)   // 완료 경기만 평균 (SeasonStats 규칙)
    }

    @Test func emptyMonth() {
        let m = MonthSummary.of([], now: Date(), calendar: .current)
        #expect(m.matches == 0 && m.distance == 0 && m.cardsPerMatch == nil)
    }

    @Test func v3SummaryDecodesWithoutRuleFields() throws {
        let s = summary(0)
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(s)) as! [String: Any]
        json.removeValue(forKey: "periodCount"); json.removeValue(forKey: "periodMinutes")
        json["summaryVersion"] = 3
        let decoded = try JSONDecoder().decode(MatchSummary.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(decoded.periodCount == nil)
        #expect(!decoded.isCurrent)   // v4가 되어 폰 보관함이 다시 만든다
    }
}
