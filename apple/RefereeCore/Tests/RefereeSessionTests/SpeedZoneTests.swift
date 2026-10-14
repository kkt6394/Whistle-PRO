import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

/// 북쪽으로 meters만큼 간 지점 (위도 0 근처라 1도 ≈ 111,320m)
private func point(_ t: Double, north meters: Double, kmh: Double, accuracy: Double = 5) -> RoutePoint {
    RoutePoint(t: t, latitude: meters / 111_320, longitude: 0, speed: kmh < 0 ? -1 : kmh / 3.6, accuracy: accuracy)
}

private let halves: [(CommandKind, Double)] = [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)]

struct SpeedZoneTests {
    @Test func segmentsGoToAverageSpeedZone() throws {
        let route = [point(0, north: 0, kmh: 5), point(1, north: 1, kmh: 5),       // 걷기 1m
                     point(2, north: 4, kmh: 10), point(3, north: 7, kmh: 10),     // 5→10 평균 7.5 조깅 3m, 조깅 3m
                     point(4, north: 11, kmh: 16), point(5, north: 15, kmh: 16),   // 13 달리기 4m, 달리기 4m
                     point(6, north: 21, kmh: 24)]                                 // 20 스프린트 6m
        let zones = try #require(WorkoutAnalysis.speedZones(route))
        #expect(abs(zones.walking - 1) < 0.01)
        #expect(abs(zones.jogging - 6) < 0.01)
        #expect(abs(zones.running - 8) < 0.01)
        #expect(abs(zones.sprinting - 6) < 0.01)
    }

    @Test func unknownSpeedUsesDistanceOverTime() throws {
        let zones = try #require(WorkoutAnalysis.speedZones([point(0, north: 0, kmh: -1), point(2, north: 12, kmh: 5)]))
        // 12m / 2초 = 6m/s = 21.6km/h → 스프린트
        #expect(abs(zones.sprinting - 12) < 0.01)
    }

    @Test func gapsAndInaccuratePointsAreSkipped() {
        let route = [point(0, north: 0, kmh: 5), point(5, north: 5, kmh: 5),
                     point(6, north: 6, kmh: 5, accuracy: 30), point(7, north: 7, kmh: 5, accuracy: -1)]
        #expect(WorkoutAnalysis.speedZones(route) == nil)
    }

    @Test func emptyRouteHasNoZones() {
        #expect(WorkoutAnalysis.speedZones([]) == nil)
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2700, value: 5000)]
        #expect(WorkoutAnalysis.overall(log, state: makeState(halves), now: 7000).zones == nil)
    }

    @Test func zonesScaleToWorkoutDistance() throws {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2700, value: 60)]
        log.route = [point(0, north: 0, kmh: 5), point(1, north: 10, kmh: 5), point(2, north: 20, kmh: 24), point(3, north: 30, kmh: 24)]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState(halves), now: 7000)
        let zones = try #require(stats.first?.zones)
        // GPS: 걷기 10m, 달리기 10m(평균 14.5km/h), 스프린트 10m → 워치 거리 60m에 맞춰 두 배
        #expect(abs(zones.total - 60) < 0.01)
        #expect(abs(zones.walking - 20) < 0.01)
        #expect(abs(zones.running - 20) < 0.01)
        #expect(abs(zones.sprinting - 20) < 0.01)
    }

    @Test func zonesSplitByPeriodAndExcludeBreaks() throws {
        var log = WorkoutLog(matchID: UUID())
        log.route = [point(100, north: 0, kmh: 5), point(101, north: 1, kmh: 5),
                     point(3000, north: 0, kmh: 24), point(3001, north: 50, kmh: 24),   // 하프타임: 빼야 한다
                     point(4000, north: 0, kmh: 24), point(4001, north: 7, kmh: 24)]
        let state = makeState(halves)
        let stats = WorkoutAnalysis.perPeriod(log, state: state, now: 7000)
        #expect(abs((stats[0].zones?.walking ?? 0) - 1) < 0.01)
        #expect(stats[0].zones?.sprinting == 0)
        #expect(abs((stats[1].zones?.sprinting ?? 0) - 7) < 0.01)
        let overall = try #require(WorkoutAnalysis.overall(log, state: state, now: 7000).zones)
        #expect(abs(overall.total - 8) < 0.01)   // 거리 샘플이 없으면 GPS 값 그대로
    }

    @Test func scaledKeepsRatioAndIgnoresZero() {
        let zones = SpeedZones(walking: 1, jogging: 1, running: 1, sprinting: 1)
        #expect(zones.scaled(to: 8) == SpeedZones(walking: 2, jogging: 2, running: 2, sprinting: 2))
        #expect(zones.scaled(to: 0) == zones)
        #expect(SpeedZones().scaled(to: 10) == SpeedZones())
    }
}
