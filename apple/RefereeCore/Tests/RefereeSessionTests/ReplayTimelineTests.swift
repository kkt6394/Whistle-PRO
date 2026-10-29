import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private let metersPerDegree = 111_320.0

/// 전반 0~2700, 하프타임, 후반 3600~6300
private let regularCommands: [(CommandKind, Double)] = [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)]

/// 기간 안에서 2초 간격 경로(오가며 달리고 가끔 스프린트), 심박 1초 간격, 거리 누적 샘플
private func makeLog(periods: [(Double, Double)], routePeriods: [Int]? = nil) -> WorkoutLog {
    var log = WorkoutLog(matchID: UUID())
    var total = 0.0
    for (index, period) in periods.enumerated() {
        var t = period.0
        while t <= period.1 {
            let north = 30 * sin(t / 40)
            let east = 20 * cos(t / 55)
            if routePeriods?.contains(index) ?? true {
                let sprinting = Int(t / 10) % 9 == 0
                log.route.append(RoutePoint(
                    t: t, latitude: 37.5 + north / metersPerDegree,
                    longitude: 127.0 + east / (metersPerDegree * cos(37.5 * .pi / 180)),
                    speed: sprinting ? 6.5 : 3, accuracy: 5))
            }
            t += 2
        }
        var s = period.0
        while s <= period.1 {
            log.heartRate.append(WorkoutSample(t: s, value: 120 + (s / 30).truncatingRemainder(dividingBy: 40)))
            if Int(s) % 5 == 0 {
                total += 10
                log.distance.append(WorkoutSample(t: s, value: total))
            }
            s += 1
        }
        total += 5
    }
    return log
}

private func regular() -> (WorkoutLog, MatchState) {
    (makeLog(periods: [(0, 2700), (3600, 6300)]), makeState(regularCommands))
}

private func timeline(_ log: WorkoutLog, _ state: MatchState, now: Double = 7000) throws -> ReplayTimeline {
    try #require(ReplayTimeline.make(log: log, state: state, now: now))
}

struct ReplayTimelineTests {
    @Test func alwaysFourHundredFiftyFrames() throws {
        let (log, state) = regular()
        #expect(try timeline(log, state).frameCount == 450)
        let short = RuleSet(periodLength: 600)
        let shortState = makeState(short, [(.startPeriod, 0), (.endPeriod, 600), (.startPeriod, 700), (.endPeriod, 1300)])
        #expect(try timeline(makeLog(periods: [(0, 600), (700, 1300)]), shortState, now: 2000).frameCount == 450)
        let extra = RuleSet(extraTime: true)
        let extraState = makeState(extra, [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300),
                                           (.startPeriod, 6600), (.endPeriod, 7500), (.startPeriod, 7600), (.endPeriod, 8500)])
        #expect(extraState.periods.count == 4)
        #expect(try timeline(makeLog(periods: [(0, 2700), (3600, 6300), (6600, 7500), (7600, 8500)]), extraState, now: 9000).frameCount == 450)
    }

    @Test func skipsHalftime() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        for i in 0..<timeline.frameCount {
            let t = timeline.frame(i).t
            #expect(!(t > 2700 && t < 3600))
        }
    }

    @Test func firstFrameStartsAtKickoff() throws {
        let (log, state) = regular()
        let frame = try timeline(log, state).frame(0)
        #expect(frame.t == 0)
        #expect(frame.distance == 0)
        #expect(frame.elapsed == 0)
    }

    @Test func lastFrameMatchesOverall() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        let overall = WorkoutAnalysis.overall(log, state: state, now: 7000)
        let frame = timeline.frame(449)
        #expect(frame.distance == overall.distance)
        #expect(frame.sprints == overall.sprints)
        #expect(frame.averageHeartRate == overall.averageHeartRate)
        #expect(overall.sprints > 0)
        #expect(frame.heatCount == timeline.points.count)
        #expect(frame.elapsed == 5400)
    }

    @Test func holdFramesRepeatLast() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        let last = timeline.frame(449)
        for i in 405...449 { #expect(timeline.frame(i) == last) }
    }

    @Test func distanceNeverDecreases() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        var previous = timeline.frame(0)
        for i in 1..<timeline.frameCount {
            let frame = timeline.frame(i)
            #expect(frame.distance >= previous.distance)
            #expect(frame.sprints >= previous.sprints)
            #expect(frame.heatCount >= previous.heatCount)
            previous = frame
        }
    }

    @Test func tailStaysInPeriodAndFiveMinutes() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        let timeOf = Dictionary(uniqueKeysWithValues: timeline.points.map { (GeoPoint(latitude: $0.latitude, longitude: $0.longitude), $0.t) })
        func times(_ tail: [GeoPoint]) -> [Double] { tail.compactMap { timeOf[$0] } }
        // 후반 첫 프레임: 전반 점이 꼬리에 없다
        let secondHalfStart = (0..<450).first { timeline.frame($0).period == 1 }!
        let first = timeline.frame(secondHalfStart)
        #expect(!first.tail.isEmpty)
        #expect(times(first.tail).allSatisfy { $0 > 2700 })
        for i in 0..<450 {
            let frame = timeline.frame(i)
            #expect(times(frame.tail).allSatisfy { $0 >= frame.t - 300 })
        }
    }

    @Test func fadesAtBoundary() throws {
        let (log, state) = regular()
        let timeline = try timeline(log, state)
        let nearest = (0..<405).min { abs(timeline.frame($0).t - 2700) < abs(timeline.frame($1).t - 2700) }!
        #expect(timeline.frame(nearest).fade < 0.2)
        #expect(timeline.frame(100).fade == 1)
        #expect(timeline.frame(0).fade == 1)
    }

    @Test func gapPeriodHidesDotKeepsCounting() throws {
        let log = makeLog(periods: [(0, 2700), (3600, 6300)], routePeriods: [0])
        let state = makeState(regularCommands)
        let timeline = try timeline(log, state)
        var previousDistance = 0.0
        for i in 0..<timeline.frameCount {
            let frame = timeline.frame(i)
            if frame.period == 1 {
                #expect(frame.position == nil)
                #expect(frame.tail.isEmpty)
            }
            #expect(frame.distance >= previousDistance)
            previousDistance = frame.distance
        }
        #expect(timeline.frame(449).distance == WorkoutAnalysis.overall(log, state: state, now: 7000).distance)
        #expect(timeline.frame(449).distance > timeline.frame(200).distance)
    }

    @Test func openLastPeriodUsesNow() throws {
        let log = makeLog(periods: [(0, 2700), (3600, 6000)])
        let state = makeState([(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600)])
        let timeline = try timeline(log, state, now: 6000)
        #expect(timeline.frame(449).t == 6000)
    }

    @Test func nilWithFewPoints() {
        var (log, state) = regular()
        log.route = Array(log.route.prefix(9))
        #expect(ReplayTimeline.make(log: log, state: state, now: 7000) == nil)
    }

    @Test func drawnPitchHasMinimumSize() throws {
        // 40×30m 범위를 훑는 점
        let points = stride(from: 0.0, through: 40, by: 2).flatMap { n in
            [0.0, 30.0].map { e in
                GeoPoint(latitude: 37.5 + n / metersPerDegree, longitude: 127.0 + e / (metersPerDegree * cos(37.5 * .pi / 180)))
            }
        }
        let drawn = try #require(ReplayTimeline.drawnPitch(points))
        let estimate = try #require(PitchEstimate.of(points))
        #expect(drawn.length == 100)
        #expect(drawn.width == 64)
        #expect(drawn.center == estimate.center)
        #expect(drawn.bearing == estimate.bearing)
    }
}
