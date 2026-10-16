import Foundation
import RefereeCore

/// 목록 점수판 카드 아래 칩 (디자인 개편 §5.1): 0이거나 없는 값은 만들지 않는다
public enum ListChip: Equatable, Sendable {
    case shootout(home: Int, away: Int)
    case goals(Int)
    case yellows(Int)
    case reds(Int)
    case distance(meters: Double)
}

/// 목록 한 줄에 필요한 값만 (그릴 때 경기 기록을 다시 재생하지 않는다)
public struct MatchListItem: Equatable, Sendable, Identifiable {
    public var id: UUID
    public var startedAt: Double
    public var homeName: String
    public var awayName: String
    public var homeColor: TeamColor
    public var awayColor: TeamColor
    public var homeScore: Int
    public var awayScore: Int
    public var abandoned: Bool
    public var abandonReason: String?
    /// "25분 × 4" (v3 이하 요약은 nil)
    public var ruleText: (count: Int, minutes: Int)?
    public var chips: [ListChip]

    public init(_ s: MatchSummary) {
        id = s.id
        startedAt = s.startedAt
        homeName = s.homeName; awayName = s.awayName
        homeColor = s.homeColor; awayColor = s.awayColor
        homeScore = s.homeScore; awayScore = s.awayScore
        if case .abandoned(let reason) = s.outcome {
            abandoned = true; abandonReason = reason
        } else {
            abandoned = false; abandonReason = nil
        }
        if let c = s.periodCount, let m = s.periodMinutes { ruleText = (c, m) } else { ruleText = nil }
        var chips: [ListChip] = []
        if let pk = s.shootout, pk.count == 2 { chips.append(.shootout(home: pk[0], away: pk[1])) }
        if let g = s.goals, g > 0 { chips.append(.goals(g)) }
        if let y = s.yellows, y > 0 { chips.append(.yellows(y)) }
        if let r = s.reds, r > 0 { chips.append(.reds(r)) }
        if let d = s.distance, d > 0 { chips.append(.distance(meters: d)) }
        self.chips = chips
    }

    // ruleText 튜플 때문에 직접 비교한다
    public static func == (a: Self, b: Self) -> Bool {
        a.id == b.id && a.startedAt == b.startedAt && a.homeName == b.homeName && a.awayName == b.awayName
            && a.homeColor == b.homeColor && a.awayColor == b.awayColor && a.homeScore == b.homeScore
            && a.awayScore == b.awayScore && a.abandoned == b.abandoned && a.abandonReason == b.abandonReason
            && a.ruleText?.count == b.ruleText?.count && a.ruleText?.minutes == b.ruleText?.minutes && a.chips == b.chips
    }
}

/// 목록 맨 위 이번 달 요약 (기기 달력 기준). 평균 규칙은 SeasonStats와 같다
public struct MonthSummary: Equatable, Sendable {
    public var matches: Int
    public var distance: Double
    public var cardsPerMatch: Double?

    public init(matches: Int, distance: Double, cardsPerMatch: Double?) {
        self.matches = matches
        self.distance = distance
        self.cardsPerMatch = cardsPerMatch
    }

    public static func of(_ summaries: [MatchSummary], now: Date, calendar: Calendar) -> MonthSummary {
        guard let month = calendar.dateInterval(of: .month, for: now) else {
            return MonthSummary(matches: 0, distance: 0, cardsPerMatch: nil)
        }
        let stats = SeasonStats.aggregate(summaries, from: month.start.timeIntervalSince1970, to: month.end.timeIntervalSince1970)
        return MonthSummary(matches: stats.all.matches, distance: stats.all.distance, cardsPerMatch: stats.cardsPerMatch)
    }
}
