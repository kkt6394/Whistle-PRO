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
            // 손상된 좌표로 트랩하지 않게 제한해서 변환한다
            let key = Key(row: wholeSeconds(p.latitude / latitudeStep),
                          column: wholeSeconds(p.longitude / longitudeStep))
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
    /// 몇 번째 기간의 점인지 (그래프가 기간마다 따로 선을 그려 휴식 구간을 잇지 않게)
    public var period: Int = 0
}

extension WorkoutAnalysis {
    /// 기간 진행 중의 심박수만 그래프 점으로 (휴식 제외)
    public static func heartRateTimeline(_ log: WorkoutLog, state: MatchState, now: Double) -> [ChartPoint] {
        guard let start = state.periods.first?.startedAt else { return [] }
        let ranges = state.periods.map { ($0.startedAt, $0.endedAt ?? now) }
        return log.heartRate.compactMap { sample in
            guard let period = ranges.firstIndex(where: { sample.t >= $0.0 && sample.t <= $0.1 }) else { return nil }
            return ChartPoint(minute: (sample.t - start) / 60, value: sample.value, period: period)
        }
    }
}

/// 히트맵 전체(와 이벤트 위치)가 들어오는 지도 범위. 상세 화면 지도와 활동 카드 스냅숏이 함께 쓴다.
public struct HeatmapBounds: Equatable, Sendable {
    public var centerLatitude: Double
    public var centerLongitude: Double
    public var northSouthMeters: Double
    public var eastWestMeters: Double

    /// 넓은 쪽 길이의 2.5배 높이에서 보면 여백을 두고 다 들어온다. 최소 150m
    public var viewingDistance: Double {
        max(max(northSouthMeters, eastWestMeters) * 2.5, 150)
    }

    /// 한국은 위성 사진을 경기장 크기까지 확대할 수 없어(Apple 지도 제한) 일반 지도로 그린다.
    public var inKorea: Bool {
        guard (33.0...38.7).contains(centerLatitude), (124.5...131.9).contains(centerLongitude) else { return false }
        // 상자의 남동쪽 모서리는 일본 (후쿠오카·시모노세키·쓰시마·이키). 부산·거제는 북위 35도 위이거나 동경 128.9도 서쪽 (M7a 리뷰)
        return !(centerLatitude < 35.0 && centerLongitude > 128.9)
    }
}

extension Heatmap {
    public static func bounds(cells: [HeatCell], pinCoordinates: [(latitude: Double, longitude: Double)] = []) -> HeatmapBounds? {
        let latitudes = cells.flatMap { [$0.minLatitude, $0.maxLatitude] } + pinCoordinates.map(\.latitude)
        let longitudes = cells.flatMap { [$0.minLongitude, $0.maxLongitude] } + pinCoordinates.map(\.longitude)
        guard !cells.isEmpty, let minLat = latitudes.min(), let maxLat = latitudes.max(),
              let minLon = longitudes.min(), let maxLon = longitudes.max() else { return nil }
        let centerLatitude = (minLat + maxLat) / 2
        return HeatmapBounds(
            centerLatitude: centerLatitude,
            centerLongitude: (minLon + maxLon) / 2,
            northSouthMeters: (maxLat - minLat) * 111_320,
            eastWestMeters: (maxLon - minLon) * 111_320 * cos(centerLatitude * .pi / 180))
    }
}
