import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private let metersPerDegree = 111_320.0

private func point(north: Double, east: Double, accuracy: Double = 5) -> RoutePoint {
    let latitude = 37.5 + north / metersPerDegree
    let longitude = 127.0 + east / (metersPerDegree * cos(37.5 * .pi / 180))
    return RoutePoint(t: 0, latitude: latitude, longitude: longitude, speed: 3, accuracy: accuracy)
}

struct HeatmapTests {
    @Test func groupsNearbyPointsIntoCells() {
        let route = [point(north: 1, east: 1), point(north: 1, east: 1), point(north: 1, east: 1), point(north: 21, east: 1)]
        let cells = Heatmap.cells(route, cellMeters: 4)
        #expect(cells.count == 2)
        #expect(cells.map(\.count).sorted() == [1, 3])
        #expect(cells.first { $0.count == 3 }?.intensity == 1)
        #expect(cells.first { $0.count == 1 }.map { abs($0.intensity - 1.0 / 3) < 1e-9 } == true)
    }

    @Test func cellBoundsContainTheirPoints() throws {
        let p = point(north: 1, east: 1)
        let cell = try #require(Heatmap.cells([p], cellMeters: 4).first)
        #expect(cell.minLatitude <= p.latitude && p.latitude < cell.maxLatitude)
        #expect(cell.minLongitude <= p.longitude && p.longitude < cell.maxLongitude)
    }

    @Test func heatmapIgnoresInaccuratePoints() {
        #expect(Heatmap.cells([point(north: 0, east: 0, accuracy: 50), point(north: 1, east: 1, accuracy: -1)]).isEmpty)
        #expect(Heatmap.cells([]).isEmpty)
    }

    @Test func heartRateTimelineSkipsHalfTime() {
        let state = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        var log = WorkoutLog(matchID: UUID())
        log.heartRate = [WorkoutSample(t: 60, value: 120), WorkoutSample(t: 3000, value: 90), WorkoutSample(t: 3660, value: 150)]
        #expect(WorkoutAnalysis.heartRateTimeline(log, state: state, now: 7000)
                == [ChartPoint(minute: 1, value: 120, period: 0), ChartPoint(minute: 61, value: 150, period: 1)])
    }

    /// 그래프가 기간마다 따로 선을 그려 하프타임을 잇지 않게 점에 기간 번호가 있다 (점검표 25)
    @Test func heartRatePointsKnowTheirPeriod() {
        let state = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        var log = WorkoutLog(matchID: UUID())
        log.heartRate = [WorkoutSample(t: 2640, value: 150), WorkoutSample(t: 3620, value: 140)]
        #expect(WorkoutAnalysis.heartRateTimeline(log, state: state, now: 7000).map(\.period) == [0, 1])
    }

    /// 하프타임·경기 전후에 걸은 길은 칠하지 않는다 (2026-10-28 리뷰)
    @Test func heatmapRouteSkipsHalfTimeAndAfterMatch() {
        let state = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        let route = [-60.0, 60, 3000, 3660, 6400].map { t -> RoutePoint in
            var p = point(north: 0, east: 0)
            p.t = t
            return p
        }
        #expect(Heatmap.duringPeriods(route, state: state, now: 7000).map(\.t) == [60, 3660])
    }
}
