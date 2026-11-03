import Foundation
import Testing
@testable import RefereeSession

/// 시즌 통계 기간 경계 (M7a 리뷰: 앱 타깃에 있어 테스트하지 못했다)
struct StatsRangeTests {
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    private func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: y, month: m, day: d, hour: h))!
    }

    @Test func customIncludesWholeEndDay() {
        let b = StatsRange.custom.bounds(customFrom: date(2026, 10, 1, 15), customTo: date(2026, 10, 3, 9), calendar: calendar)
        #expect(b.from == date(2026, 10, 1).timeIntervalSince1970)
        #expect(b.to == date(2026, 10, 4).timeIntervalSince1970)
    }

    /// 시작을 끝 뒤로 옮기면 조용히 "경기가 없습니다"였다 → 시작 날 하루로 본다
    @Test func customEndBeforeStartUsesStartDay() {
        let b = StatsRange.custom.bounds(customFrom: date(2026, 10, 5), customTo: date(2026, 10, 3), calendar: calendar)
        #expect(b.from == date(2026, 10, 5).timeIntervalSince1970)
        #expect(b.to == date(2026, 10, 6).timeIntervalSince1970)
    }

    /// 화면에 적는 기간 (2026-11-03 critique)
    @Test func shownDays() {
        let now = date(2026, 11, 3, 17)
        let year = StatsRange.thisYear.shownDays(customFrom: now, customTo: now, matchStarts: [], now: now, calendar: calendar)
        #expect(year?.first == date(2026, 1, 1) && year?.last == date(2026, 11, 3))
        let month = StatsRange.last30Days.shownDays(customFrom: now, customTo: now, matchStarts: [], now: now, calendar: calendar)
        #expect(month?.first == date(2026, 10, 4) && month?.last == date(2026, 11, 3))
        let starts = [date(2026, 5, 2, 10).timeIntervalSince1970, date(2025, 9, 1, 20).timeIntervalSince1970]
        let all = StatsRange.all.shownDays(customFrom: now, customTo: now, matchStarts: starts, now: now, calendar: calendar)
        #expect(all?.first == date(2025, 9, 1) && all?.last == date(2026, 5, 2))
        #expect(StatsRange.all.shownDays(customFrom: now, customTo: now, matchStarts: [], now: now, calendar: calendar) == nil)
        let custom = StatsRange.custom.shownDays(customFrom: date(2026, 10, 5, 9), customTo: date(2026, 10, 3), matchStarts: [],
                                                 now: now, calendar: calendar)
        #expect(custom?.first == date(2026, 10, 5) && custom?.last == date(2026, 10, 5))
    }

    @Test func thisYearStartsOnJanuaryFirst() {
        let b = StatsRange.thisYear.bounds(customFrom: Date(), customTo: Date(), now: date(2026, 10, 16, 12), calendar: calendar)
        #expect(b.from == date(2026, 1, 1).timeIntervalSince1970)
        #expect(b.to == nil)
    }

    /// 이전 앱 버전의 요약 디코더(통계 값 없음)가 새 요약(운동 값 zones 포함)을 읽는다
    @Test func oldSummaryDecoderReadsNewSummary() throws {
        struct OldSummary: Decodable {
            var id: UUID
            var homeName: String
            var homeScore: Int
        }
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 100, value: 300)]
        log.route = (0..<20).map { RoutePoint(t: Double($0), latitude: 37 + Double($0) * 0.0001, longitude: 127, speed: 3, accuracy: 5) }
        let record = makeRecord([(.startPeriod, 0), (.endPeriod, 2700)])
        log.matchID = record.id
        let summary = MatchSummary(bundle: MatchBundle(record: record, workout: log))
        let old = try JSONDecoder().decode(OldSummary.self, from: JSONEncoder().encode(summary))
        #expect(old.id == record.id)
    }
}
