import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private func point(_ t: Double, kmh: Double, accuracy: Double = 5) -> RoutePoint {
    RoutePoint(t: t, latitude: 0, longitude: 0, speed: kmh / 3.6, accuracy: accuracy)
}

private let halves: [(CommandKind, Double)] = [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)]

struct WorkoutAnalysisTests {
    /// 혼자 300m 튄 점(앞·뒤 모두와 12 m/s 초과)은 빼고, 꾸준히 움직인 점은 남긴다
    @Test func usableRouteDropsLoneSpike() {
        func at(_ t: Double, _ east: Double) -> RoutePoint {
            RoutePoint(t: t, latitude: 37.5, longitude: 127.0 + east / (111_320.0 * cos(37.5 * .pi / 180)), speed: -1, accuracy: 5)
        }
        let route = [at(0, 0), at(1, 3), at(2, 300), at(3, 6), at(4, 9)]
        #expect(WorkoutAnalysis.usableRoute(route).map(\.t) == [0, 1, 3, 4])
        let zones = WorkoutAnalysis.speedZones(route)!
        #expect(zones.walking + zones.jogging + zones.running + zones.sprinting < 20)
    }

    @Test func sprintCountsSeparateRuns() {
        let route = [point(0, kmh: 10), point(1, kmh: 22), point(2, kmh: 23), point(3, kmh: 24), point(4, kmh: 10),
                     point(5, kmh: 25), point(6, kmh: 26), point(7, kmh: 27), point(8, kmh: 10)]
        #expect(WorkoutAnalysis.sprintCount(route) == 2)
    }

    @Test func shortBurstIsNotASprint() {
        #expect(WorkoutAnalysis.sprintCount([point(0, kmh: 10), point(1, kmh: 30), point(2, kmh: 10)]) == 0)
    }

    @Test func inaccuratePointsAreIgnored() {
        let route = [point(0, kmh: 10), point(1, kmh: 25, accuracy: 50), point(2, kmh: 25, accuracy: 50), point(3, kmh: 10)]
        #expect(WorkoutAnalysis.sprintCount(route) == 0)
    }

    @Test func longGapSplitsRuns() {
        let route = [point(0, kmh: 25), point(1, kmh: 25), point(2, kmh: 25), point(7, kmh: 25), point(8, kmh: 25), point(9, kmh: 25)]
        #expect(WorkoutAnalysis.sprintCount(route) == 2)
    }

    @Test func perPeriodSplitsDistanceEnergyAndHeartRate() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2700, value: 5000),
                        WorkoutSample(t: 3600, value: 5200), WorkoutSample(t: 6300, value: 10200)]
        log.energy = [WorkoutSample(t: 2700, value: 300), WorkoutSample(t: 6300, value: 650)]
        log.heartRate = [WorkoutSample(t: 100, value: 120), WorkoutSample(t: 200, value: 160), WorkoutSample(t: 4000, value: 150)]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState(halves), now: 7000)
        #expect(stats.map(\.label) == ["1H", "2H"])
        #expect(stats.map(\.distance) == [5000, 5000])
        #expect(stats.map(\.energy) == [300, 350])
        #expect(stats[0].averageHeartRate == 140)
        #expect(stats[0].maxHeartRate == 160)
        #expect(stats[1].averageHeartRate == 150)
    }

    @Test func overallExcludesHalfTime() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2700, value: 5000),
                        WorkoutSample(t: 3600, value: 5200), WorkoutSample(t: 6300, value: 10200)]
        log.heartRate = [WorkoutSample(t: 100, value: 120), WorkoutSample(t: 3000, value: 90), WorkoutSample(t: 4000, value: 150)]
        log.route = [point(100, kmh: 25), point(101, kmh: 25), point(102, kmh: 25),
                     point(3000, kmh: 25), point(3001, kmh: 25), point(3002, kmh: 25)]
        let overall = WorkoutAnalysis.overall(log, state: makeState(halves), now: 7000)
        #expect(overall.label == nil)
        #expect(overall.distance == 10000)
        #expect(overall.averageHeartRate == 135)
        #expect(overall.maxHeartRate == 150)
        #expect(overall.sprints == 1)
    }

    @Test func runningPeriodUsesNow() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 600, value: 1200)]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState([(.startPeriod, 0)]), now: 650)
        #expect(stats.map(\.distance) == [1200])
    }

    @Test func noPeriodsGivesZeroStats() {
        let overall = WorkoutAnalysis.overall(WorkoutLog(matchID: UUID()), state: makeState([]), now: 0)
        #expect(overall == WorkoutStats(label: nil, averageHeartRate: nil, maxHeartRate: nil, distance: 0, energy: 0, sprints: 0))
    }
}
