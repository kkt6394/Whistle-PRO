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
}
