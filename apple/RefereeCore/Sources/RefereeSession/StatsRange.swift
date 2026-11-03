import Foundation

/// 시즌 통계 기간 (화면 이름은 앱에서). 첫 킥오프 시각 기준 [from, to)
public enum StatsRange: CaseIterable, Hashable, Sendable {
    case thisYear, last30Days, all, custom

    public func bounds(customFrom: Date, customTo: Date, now: Date = Date(), calendar: Calendar = .current) -> (from: Double?, to: Double?) {
        switch self {
        case .thisYear:
            let start = calendar.dateInterval(of: .year, for: now)?.start
            return (start?.timeIntervalSince1970, nil)
        case .last30Days:
            return (now.addingTimeInterval(-30 * 86_400).timeIntervalSince1970, nil)
        case .all:
            return (nil, nil)
        case .custom:
            // 끝 날짜는 그날 하루 전체를 포함한다. 끝이 시작보다 앞이면 시작 날 하루로 본다 (M7a 리뷰)
            let from = calendar.startOfDay(for: customFrom)
            let end = calendar.startOfDay(for: max(customTo, customFrom))
            let to = calendar.date(byAdding: .day, value: 1, to: end) ?? end
            return (from.timeIntervalSince1970, to.timeIntervalSince1970)
        }
    }

    /// 화면에 적는 기간의 첫날·끝날 (2026-11-03 critique: 지금 보는 기간이 언제부터 언제까지인지 보이지 않았다).
    /// 올해·30일은 오늘까지, 전체는 첫 경기 날~마지막 경기 날(경기가 없으면 nil), 직접은 고른 두 날(끝이 앞이면 시작 날 하루)
    public func shownDays(customFrom: Date, customTo: Date, matchStarts: [Double], now: Date = Date(),
                          calendar: Calendar = .current) -> (first: Date, last: Date)? {
        let today = calendar.startOfDay(for: now)
        switch self {
        case .thisYear:
            return (calendar.dateInterval(of: .year, for: now)?.start ?? today, today)
        case .last30Days:
            return (calendar.startOfDay(for: now.addingTimeInterval(-30 * 86_400)), today)
        case .all:
            guard let first = matchStarts.min(), let last = matchStarts.max() else { return nil }
            return (calendar.startOfDay(for: Date(timeIntervalSince1970: first)),
                    calendar.startOfDay(for: Date(timeIntervalSince1970: last)))
        case .custom:
            let first = calendar.startOfDay(for: customFrom)
            return (first, max(first, calendar.startOfDay(for: customTo)))
        }
    }
}
