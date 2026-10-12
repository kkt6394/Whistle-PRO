import Foundation
import RefereeCore

public struct WorkoutStats: Equatable, Sendable {
    /// 기간 라벨 (전체 통계면 nil)
    public var label: String?
    public var averageHeartRate: Int?
    public var maxHeartRate: Int?
    /// m
    public var distance: Double
    /// kcal
    public var energy: Double
    public var sprints: Int
}

public enum WorkoutAnalysis {
    /// 스프린트 기준 속도 20km/h (m/s)
    public static let sprintSpeed = 20.0 / 3.6
    /// 이보다 부정확한 GPS 지점은 버린다 (m)
    public static let maximumAccuracy = 20.0
    /// 이보다 길게 지점이 비면 구간을 끊는다 (초)
    public static let maximumGap = 3.0
    /// 이보다 짧은 고속 구간은 GPS 튐으로 보고 세지 않는다 (초, 1Hz에서 3지점)
    public static let minimumSprintDuration = 2.0

    /// 20km/h 이상이 2초 이상 이어진 구간 수. route는 시간순이어야 한다.
    public static func sprintCount(_ route: [RoutePoint]) -> Int {
        var count = 0
        var runStart: Double?
        var runEnd = 0.0
        var lastTime: Double?
        func closeRun() {
            if let start = runStart, runEnd - start >= minimumSprintDuration { count += 1 }
            runStart = nil
        }
        for point in route where point.accuracy >= 0 && point.accuracy <= maximumAccuracy {
            if let lastTime, point.t - lastTime > maximumGap { closeRun() }
            if point.speed >= sprintSpeed {
                if runStart == nil { runStart = point.t }
                runEnd = point.t
            } else {
                closeRun()
            }
            lastTime = point.t
        }
        closeRun()
        return count
    }

    public static func stats(_ log: WorkoutLog, from start: Double, to end: Double, label: String?) -> WorkoutStats {
        stats(log, ranges: [(start, end)], label: label)
    }

    public static func perPeriod(_ log: WorkoutLog, state: MatchState, now: Double) -> [WorkoutStats] {
        state.periods.map { p in
            stats(log, from: p.startedAt, to: p.endedAt ?? now, label: p.ref.label(periodCount: state.rules.periodCount))
        }
    }

    /// 기간 진행 중의 값만 합친다 (휴식 제외).
    public static func overall(_ log: WorkoutLog, state: MatchState, now: Double) -> WorkoutStats {
        stats(log, ranges: state.periods.map { ($0.startedAt, $0.endedAt ?? now) }, label: nil)
    }

    private static func stats(_ log: WorkoutLog, ranges: [(Double, Double)], label: String?) -> WorkoutStats {
        func inRanges(_ t: Double) -> Bool { ranges.contains { t >= $0.0 && t <= $0.1 } }
        let heartRates = log.heartRate.filter { inRanges($0.t) }.map(\.value)
        let average = heartRates.isEmpty ? nil : wholeSeconds(heartRates.reduce(0, +) / Double(heartRates.count), rounding: .toNearestOrAwayFromZero)
        return WorkoutStats(
            label: label,
            averageHeartRate: average,
            maxHeartRate: heartRates.max().map { wholeSeconds($0, rounding: .toNearestOrAwayFromZero) },
            distance: ranges.reduce(0) { $0 + delta(log.distance, from: $1.0, to: $1.1) },
            energy: ranges.reduce(0) { $0 + delta(log.energy, from: $1.0, to: $1.1) },
            sprints: ranges.reduce(0) { total, range in
                total + sprintCount(log.route.filter { $0.t >= range.0 && $0.t <= range.1 })
            })
    }

    /// 누적값 샘플에서 구간 동안 늘어난 양. 값이 줄어들면 운동 세션이 다시 시작돼 0부터 쌓인 것으로 본다.
    private static func delta(_ samples: [WorkoutSample], from start: Double, to end: Double) -> Double {
        var previous = 0.0
        var total = 0.0
        for sample in samples {
            let increment = sample.value >= previous ? sample.value - previous : sample.value
            if sample.t > start && sample.t <= end { total += increment }
            previous = sample.value
        }
        return total
    }
}
