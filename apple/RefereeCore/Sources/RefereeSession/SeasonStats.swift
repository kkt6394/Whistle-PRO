import Foundation

/// 기간 안 경기들의 합계·경기당 평균. 경기 요약(MatchSummary)만으로 만든다.
/// 중단된 경기는 경기 수와 합계에는 넣고, 경기당 평균에서는 뺀다 (짧게 끝나 평균을 낮추므로).
public struct SeasonStats: Equatable, Sendable {
    public struct Totals: Equatable, Sendable {
        public var matches = 0
        public var goals = 0
        public var yellows = 0
        public var reds = 0
        public var sinBins = 0
        public var addedSeconds = 0
        /// 운동 데이터가 있는 경기 수와 그 합
        public var matchesWithWorkout = 0
        public var distance = 0.0
        public var sprints = 0
        /// 평균 심박이 있는 경기 수와 그 합 (경기마다 같은 비중)
        public var matchesWithHeartRate = 0
        public var heartRateSum = 0
        public var zones: SpeedZones?

        public var cards: Int { yellows + reds + sinBins }

        mutating func add(_ s: MatchSummary) {
            matches += 1
            goals += s.goals ?? 0
            yellows += s.yellows ?? 0
            reds += s.reds ?? 0
            sinBins += s.sinBins ?? 0
            addedSeconds += s.addedSeconds ?? 0
            if let d = s.distance {
                matchesWithWorkout += 1
                distance += d
                sprints += s.sprints ?? 0
            }
            if let hr = s.averageHeartRate {
                matchesWithHeartRate += 1
                heartRateSum += hr
            }
            if let z = s.zones { zones = (zones ?? SpeedZones()) + z }
        }
    }

    /// 기간 안 모든 경기
    public var all = Totals()
    /// 완료된 경기만 (경기당 평균용)
    public var completed = Totals()

    public var abandoned: Int { all.matches - completed.matches }

    public var goalsPerMatch: Double? { per(completed.goals, completed.matches) }
    public var cardsPerMatch: Double? { per(completed.cards, completed.matches) }
    public var addedSecondsPerMatch: Double? { per(completed.addedSeconds, completed.matches) }
    public var distancePerMatch: Double? {
        completed.matchesWithWorkout > 0 ? completed.distance / Double(completed.matchesWithWorkout) : nil
    }
    public var sprintsPerMatch: Double? { per(completed.sprints, completed.matchesWithWorkout) }
    public var averageHeartRate: Int? {
        completed.matchesWithHeartRate > 0
            ? Int((Double(completed.heartRateSum) / Double(completed.matchesWithHeartRate)).rounded()) : nil
    }

    private func per(_ total: Int, _ count: Int) -> Double? {
        count > 0 ? Double(total) / Double(count) : nil
    }

    /// 첫 킥오프 시각이 from 이상, to 미만인 경기 (nil이면 그쪽 경계 없음)
    public static func aggregate(_ summaries: [MatchSummary], from: Double? = nil, to: Double? = nil) -> SeasonStats {
        var stats = SeasonStats()
        for summary in summaries {
            if let from, summary.startedAt < from { continue }
            if let to, summary.startedAt >= to { continue }
            stats.all.add(summary)
            if summary.outcome == .completed { stats.completed.add(summary) }
        }
        return stats
    }
}
