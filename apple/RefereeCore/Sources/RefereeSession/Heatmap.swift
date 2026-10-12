import Foundation
import RefereeCore

/// 히트맵 사각형 한 칸 (위경도 범위)
public struct HeatCell: Equatable, Sendable {
    public var minLatitude: Double
    public var minLongitude: Double
    public var maxLatitude: Double
    public var maxLongitude: Double
    public var count: Int
    /// 가장 많이 머문 칸이 1
    public var intensity: Double
}

public enum Heatmap {
    private static let metersPerDegree = 111_320.0

    /// GPS 지점을 cellMeters 격자로 묶는다. 정확도가 나쁜(20m 초과·음수) 지점은 버린다.
    public static func cells(_ route: [RoutePoint], cellMeters: Double = 4) -> [HeatCell] {
        let points = route.filter { $0.accuracy >= 0 && $0.accuracy <= WorkoutAnalysis.maximumAccuracy }
        guard let reference = points.first else { return [] }
        let latitudeStep = cellMeters / metersPerDegree
        let longitudeStep = cellMeters / (metersPerDegree * cos(reference.latitude * .pi / 180))

        struct Key: Hashable { let row: Int; let column: Int }
        var counts: [Key: Int] = [:]
        for p in points {
            let key = Key(row: Int((p.latitude / latitudeStep).rounded(.down)),
                          column: Int((p.longitude / longitudeStep).rounded(.down)))
            counts[key, default: 0] += 1
        }
        let maximum = Double(counts.values.max() ?? 1)
        return counts.map { key, count in
            HeatCell(
                minLatitude: Double(key.row) * latitudeStep,
                minLongitude: Double(key.column) * longitudeStep,
                maxLatitude: Double(key.row + 1) * latitudeStep,
                maxLongitude: Double(key.column + 1) * longitudeStep,
                count: count,
                intensity: Double(count) / maximum)
        }
    }
}

public struct ChartPoint: Equatable, Sendable {
    /// 첫 킥오프부터 지난 분
    public var minute: Double
    public var value: Double
}

extension WorkoutAnalysis {
    /// 기간 진행 중의 심박수만 그래프 점으로 (휴식 제외)
    public static func heartRateTimeline(_ log: WorkoutLog, state: MatchState, now: Double) -> [ChartPoint] {
        guard let start = state.periods.first?.startedAt else { return [] }
        let ranges = state.periods.map { ($0.startedAt, $0.endedAt ?? now) }
        return log.heartRate
            .filter { sample in ranges.contains { sample.t >= $0.0 && sample.t <= $0.1 } }
            .map { ChartPoint(minute: ($0.t - start) / 60, value: $0.value) }
    }
}
