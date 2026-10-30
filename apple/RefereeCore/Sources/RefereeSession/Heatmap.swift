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

    /// 기간이 진행된 동안의 지점만 고른다: 하프타임·경기 전후에 걸은 길은 칠하지 않는다 (2026-10-28 리뷰)
    public static func duringPeriods(_ route: [RoutePoint], state: MatchState, now: Double) -> [RoutePoint] {
        let ranges = state.periods.map { ($0.startedAt, $0.endedAt ?? now) }
        return route.filter { p in ranges.contains { p.t >= $0.0 && p.t <= $0.1 } }
    }

    /// GPS 지점을 cellMeters 격자로 묶는다. 정확도가 나쁜(20m 초과·음수) 지점은 버린다.
    public static func cells(_ route: [RoutePoint], cellMeters: Double = 4) -> [HeatCell] {
        let points = WorkoutAnalysis.usableRoute(route)
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

    /// 상세 심박 선 그래프용: 같은 기간 안에서 `minutes` 칸마다 평균 한 점으로 줄인다.
    /// 5초마다 찍힌 점을 그대로 이으면 선이 빽빽한 막대처럼 보였다. 기간이 바뀌면 칸을 새로 시작한다 (휴식 구간을 잇지 않게)
    public static func bucketed(_ points: [ChartPoint], minutes: Double) -> [ChartPoint] {
        var result: [ChartPoint] = []
        var bucket: [ChartPoint] = []
        func flush() {
            guard !bucket.isEmpty else { return }
            let n = Double(bucket.count)
            result.append(ChartPoint(minute: bucket.map(\.minute).reduce(0, +) / n,
                                     value: bucket.map(\.value).reduce(0, +) / n,
                                     period: bucket[0].period))
            bucket.removeAll()
        }
        for point in points {
            if let first = bucket.first,
               first.period != point.period || (point.minute / minutes).rounded(.down) != (first.minute / minutes).rounded(.down) {
                flush()
            }
            bucket.append(point)
        }
        flush()
        return result
    }
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

/// 히트맵 전체(와 이벤트 위치)가 들어오는 지도 범위. 상세 화면 지도와 SNS 카드 스냅숏이 함께 쓴다.
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

/// 부드러운 히트맵 격자: 칸마다 머문 양을 둥근 종 모양(가우시안)으로 번지게 더한 값.
/// 네모 칸을 그대로 칠하면 계단처럼 보여서(2026-10-28 사용자 지적) 그림으로 그릴 때는 이 격자를 쓴다.
public struct HeatDensity: Equatable, Sendable {
    /// 가로·세로 픽셀 수
    public var width: Int
    public var height: Int
    /// 줄 단위(첫 줄이 북쪽 끝), 가장 진한 곳이 1
    public var values: [Double]
    public var minLatitude: Double
    public var maxLatitude: Double
    public var minLongitude: Double
    public var maxLongitude: Double

    public func value(x: Int, y: Int) -> Double { values[y * width + x] }
}

extension Heatmap {
    /// 칸 중심마다 머문 양(count)을 반경 `radiusMeters`(표준편차) 가우시안으로 번지게 더한다.
    /// 가장자리가 잘리지 않게 반경의 3배만큼 둘레를 넓히고, 한 변이 `maximumSide` 픽셀을 넘지 않게 해상도를 낮춘다.
    /// 머문 양은 몇 곳(하프타임 자리 등)만 지나치게 진해지지 않게 제곱근으로 누른다.
    public static func density(cells: [HeatCell], radiusMeters: Double = 5,
                               metersPerPixel: Double = 0.5, maximumSide: Int = 512) -> HeatDensity? {
        guard !cells.isEmpty, radiusMeters > 0, metersPerPixel > 0, maximumSide > 1 else { return nil }
        let metersPerDegree = 111_320.0
        let centerLatitude = (cells.map(\.minLatitude).min()! + cells.map(\.maxLatitude).max()!) / 2
        let longitudeScale = metersPerDegree * max(cos(centerLatitude * .pi / 180), 0.01)
        let pad = radiusMeters * 3
        let minLat = cells.map(\.minLatitude).min()! - pad / metersPerDegree
        let maxLat = cells.map(\.maxLatitude).max()! + pad / metersPerDegree
        let minLon = cells.map(\.minLongitude).min()! - pad / longitudeScale
        let maxLon = cells.map(\.maxLongitude).max()! + pad / longitudeScale
        let northSouth = (maxLat - minLat) * metersPerDegree
        let eastWest = (maxLon - minLon) * longitudeScale
        let step = max(metersPerPixel, max(northSouth, eastWest) / Double(maximumSide))
        let width = max(1, min(maximumSide, Int((eastWest / step).rounded(.up))))
        let height = max(1, min(maximumSide, Int((northSouth / step).rounded(.up))))

        var values = [Double](repeating: 0, count: width * height)
        let reach = Int((pad / step).rounded(.up))
        let twoSigmaSquared = 2 * radiusMeters * radiusMeters
        for cell in cells {
            let weight = Double(cell.count).squareRoot()
            // 픽셀 좌표(가로는 서→동, 세로는 북→남)
            let cx = ((cell.minLongitude + cell.maxLongitude) / 2 - minLon) * longitudeScale / step
            let cy = (maxLat - (cell.minLatitude + cell.maxLatitude) / 2) * metersPerDegree / step
            let x0 = max(0, Int(cx) - reach), x1 = min(width - 1, Int(cx) + reach)
            let y0 = max(0, Int(cy) - reach), y1 = min(height - 1, Int(cy) + reach)
            guard x0 <= x1, y0 <= y1 else { continue }
            for y in y0...y1 {
                let dy = (Double(y) + 0.5 - cy) * step
                for x in x0...x1 {
                    let dx = (Double(x) + 0.5 - cx) * step
                    values[y * width + x] += weight * exp(-(dx * dx + dy * dy) / twoSigmaSquared)
                }
            }
        }
        guard let peak = values.max(), peak > 0 else { return nil }
        for i in values.indices { values[i] /= peak }
        return HeatDensity(width: width, height: height, values: values,
                           minLatitude: minLat, maxLatitude: maxLat, minLongitude: minLon, maxLongitude: maxLon)
    }
}

/// 경기장 좌표 히트 격자: 칸 중심을 `Pitch.local`로 경기장 m 좌표에 옮겨 가우시안으로 번지게 더한다.
/// 격자는 가로 width+2×margin, 세로 length+2×margin(m)이고 첫 줄이 골라인 A 쪽이다.
/// 반환 `HeatDensity`의 위경도 필드에는 경기장 m 범위(`minLatitude`=-margin … `maxLatitude`=length+margin, 경도는 width)를 넣는다.
/// 번지는 반경(5 m)·제곱근 누름·정규화는 `Heatmap.density`와 같다. 터치라인·골라인 밖 margin을 넘은 칸은 버린다.
public enum PitchHeat {
    public static let margin = 8.0

    public static func density(cells: [HeatCell], pitch: Pitch, radiusMeters: Double = 5,
                               metersPerPixel: Double = 0.5, maximumSide: Int = 256) -> HeatDensity? {
        guard radiusMeters > 0, metersPerPixel > 0, maximumSide > 1 else { return nil }
        let spots: [(x: Double, y: Double, weight: Double)] = cells.compactMap { cell in
            let p = pitch.local(GeoPoint(latitude: (cell.minLatitude + cell.maxLatitude) / 2,
                                         longitude: (cell.minLongitude + cell.maxLongitude) / 2))
            guard p.x >= -margin, p.x <= pitch.width + margin, p.y >= -margin, p.y <= pitch.length + margin else { return nil }
            return (p.x, p.y, Double(cell.count).squareRoot())
        }
        guard !spots.isEmpty else { return nil }
        let eastWest = pitch.width + 2 * margin
        let northSouth = pitch.length + 2 * margin
        let step = max(metersPerPixel, max(northSouth, eastWest) / Double(maximumSide))
        let width = max(1, min(maximumSide, Int((eastWest / step).rounded(.up))))
        let height = max(1, min(maximumSide, Int((northSouth / step).rounded(.up))))

        var values = [Double](repeating: 0, count: width * height)
        let reach = Int((radiusMeters * 3 / step).rounded(.up))
        let twoSigmaSquared = 2 * radiusMeters * radiusMeters
        for spot in spots {
            let cx = (spot.x + margin) / step
            let cy = (spot.y + margin) / step
            let x0 = max(0, Int(cx) - reach), x1 = min(width - 1, Int(cx) + reach)
            let y0 = max(0, Int(cy) - reach), y1 = min(height - 1, Int(cy) + reach)
            guard x0 <= x1, y0 <= y1 else { continue }
            for y in y0...y1 {
                let dy = (Double(y) + 0.5 - cy) * step
                for x in x0...x1 {
                    let dx = (Double(x) + 0.5 - cx) * step
                    values[y * width + x] += spot.weight * exp(-(dx * dx + dy * dy) / twoSigmaSquared)
                }
            }
        }
        guard let peak = values.max(), peak > 0 else { return nil }
        for i in values.indices { values[i] /= peak }
        return HeatDensity(width: width, height: height, values: values,
                           minLatitude: -margin, maxLatitude: pitch.length + margin,
                           minLongitude: -margin, maxLongitude: pitch.width + margin)
    }
}
