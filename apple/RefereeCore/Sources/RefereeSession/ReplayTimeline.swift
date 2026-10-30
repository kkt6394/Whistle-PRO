import Foundation
import RefereeCore

/// 리플레이 영상 한 프레임에 그릴 내용.
public struct ReplayFrame: Equatable {
    /// 경기 시각(Unix epoch 초)
    public var t: Double
    /// 기간 번호 (0부터)
    public var period: Int
    /// nil이면 그 순간 GPS가 없다 (점·꼬리 숨김)
    public var position: GeoPoint?
    /// 점 뒤에 남는 꼬리 (최근 5분, 같은 기간 안)
    public var tail: [GeoPoint]
    /// `points` 중 `t` 이하 개수 (렌더러는 이전 프레임 값부터 이 값까지 새로 칠한다)
    public var heatCount: Int
    /// 점·꼬리 불투명도 0…1 (기간 경계 가까이서 줄어든다)
    public var fade: Double
    /// m
    public var distance: Double
    /// 경기 기간 누적 초
    public var elapsed: Double
    public var sprints: Int
    public var averageHeartRate: Int?
}

/// 경기 기간만 이어 붙인 15초 영상의 시간축. 하프타임은 건너뛰고 승부차기는 기간이 아니라 들어가지 않는다.
public struct ReplayTimeline: Sendable {
    public static let fps = 30
    public static let duration = 15.0
    /// 끝 장면을 붙잡아 두는 시간 (초)
    public static let hold = 1.5
    public static let tailSeconds = 300.0
    public static let fadeSeconds = 0.25
    public static let minimumPoints = 10
    /// 꼬리를 이을 수 있는 가장 빠른 이동 (m/s): 전력 질주(약 10 m/s)보다 조금 빠르게
    public static let maximumTailSpeed = WorkoutAnalysis.maximumSpeed
    /// 영상 0.5초마다 통계를 미리 구한다
    private static let statsStep = 15

    /// 기간 중 + 정확도 유효 지점, 시간순
    public let points: [RoutePoint]
    public let frameCount: Int

    private struct Period {
        var start: Double
        var end: Double
        var length: Double { end - start }
    }

    private let log: WorkoutLog
    private let periods: [Period]
    private let totalLength: Double
    /// 움직이는 마지막 프레임 번호 (이후는 붙잡기)
    private let lastMovingFrame: Int
    private let snapshots: [WorkoutStats]
    private let finalStats: WorkoutStats

    public static func make(log: WorkoutLog, state: MatchState, now: Double) -> ReplayTimeline? {
        let periods = state.periods.map { Period(start: $0.startedAt, end: $0.endedAt ?? now) }.filter { $0.length > 0 }
        guard !periods.isEmpty else { return nil }
        let points = WorkoutAnalysis.usableRoute(log.route).filter { p in periods.contains { p.t >= $0.start && p.t <= $0.end } }
        guard points.count >= minimumPoints else { return nil }
        return ReplayTimeline(log: log, periods: periods, points: points)
    }

    private init(log: WorkoutLog, periods: [Period], points: [RoutePoint]) {
        self.log = log
        self.periods = periods
        self.points = points
        let total = periods.reduce(0) { $0 + $1.length }
        totalLength = total
        let moving = Int((ReplayTimeline.duration - ReplayTimeline.hold) * Double(ReplayTimeline.fps)) - 1 // 404
        lastMovingFrame = moving
        frameCount = Int(ReplayTimeline.duration * Double(ReplayTimeline.fps)) // 450
        let (finalT, _, _) = ReplayTimeline.locate(frame: moving, periods: periods, lastMovingFrame: moving, totalLength: total)
        finalStats = ReplayTimeline.stats(log, periods: periods, upTo: finalT)
        snapshots = stride(from: 0, to: moving, by: ReplayTimeline.statsStep).map { index in
            let (t, _, _) = ReplayTimeline.locate(frame: index, periods: periods, lastMovingFrame: moving, totalLength: total)
            return ReplayTimeline.stats(log, periods: periods, upTo: t)
        }
    }

    /// 프레임의 경기 시각·기간 번호·누적 경기 초
    private static func locate(frame: Int, periods: [Period], lastMovingFrame: Int, totalLength: Double) -> (t: Double, period: Int, elapsed: Double) {
        let index = min(max(frame, 0), lastMovingFrame)
        let elapsed = Double(index) / Double(lastMovingFrame) * totalLength
        var before = 0.0
        for (i, period) in periods.enumerated() {
            // 경계는 앞 기간 끝으로 본다
            if elapsed <= before + period.length || i == periods.count - 1 {
                return (min(period.start + (elapsed - before), period.end), i, elapsed)
            }
            before += period.length
        }
        return (periods[0].start, 0, 0)
    }

    /// 이미 시작한 기간마다 [시작, min(끝, t)] 구간의 값 (구역 계산 없음)
    private static func stats(_ log: WorkoutLog, periods: [Period], upTo t: Double) -> WorkoutStats {
        let ranges = periods.filter { $0.start <= t }.map { ($0.start, min($0.end, t)) }
        return WorkoutAnalysis.stats(log, ranges: ranges)
    }

    public func frame(_ index: Int) -> ReplayFrame {
        let (t, period, elapsed) = ReplayTimeline.locate(frame: index, periods: periods, lastMovingFrame: lastMovingFrame, totalLength: totalLength)
        let stats = index >= lastMovingFrame ? finalStats : snapshots[index / ReplayTimeline.statsStep]
        let (position, tail) = route(at: t, in: periods[period])
        return ReplayFrame(
            t: t, period: period, position: position, tail: position == nil ? [] : tail,
            heatCount: count(through: t), fade: fade(index), distance: stats.distance,
            elapsed: elapsed, sprints: stats.sprints, averageHeartRate: stats.averageHeartRate)
    }

    /// 크기는 `max(길이, 100)`×`max(폭, 64)` m. 심판 이동 범위는 실제 경기장보다 작아서 그대로 쓰면 작게 그려진다.
    public static func drawnPitch(_ points: [GeoPoint]) -> Pitch? {
        guard let estimate = PitchEstimate.of(points) else { return nil }
        return Pitch(center: estimate.center, length: max(estimate.length, 100), width: max(estimate.width, 64), bearing: estimate.bearing)
    }

    /// `t` 이하 첫 위치 (points는 시간순)
    private func count(through t: Double) -> Int {
        var low = 0, high = points.count
        while low < high {
            let mid = (low + high) / 2
            if points[mid].t <= t { low = mid + 1 } else { high = mid }
        }
        return low
    }

    private func route(at t: Double, in period: Period) -> (GeoPoint?, [GeoPoint]) {
        let end = count(through: t)
        // 같은 기간 안 점의 시작 위치
        var start = end
        while start > 0, points[start - 1].t >= period.start { start -= 1 }
        guard end > start else { return (nil, []) }
        let previous = points[end - 1]
        let recent = Array(points[start..<end].filter { $0.t >= t - ReplayTimeline.tailSeconds })
        // 사람이 낼 수 없는 속도로 튄 곳에서 꼬리를 끊는다: 그 뒤만 잇는다 (긴 직선이 그어졌다 — 2026-10-30, 안드로이드와 같다)
        let cut = recent.indices.dropFirst().last { i in
            let gap = recent[i].t - recent[i - 1].t
            return gap > 0 && WorkoutAnalysis.distance(recent[i - 1], recent[i]) / gap > ReplayTimeline.maximumTailSpeed
        } ?? 0
        let tail = recent[cut...].map { GeoPoint(latitude: $0.latitude, longitude: $0.longitude) }
        let here = GeoPoint(latitude: previous.latitude, longitude: previous.longitude)
        if previous.t == t { return (here, tail) }
        // 뒤 점: 같은 기간 안에서 t 다음 점
        if end < points.count, points[end].t <= period.end {
            let next = points[end]
            let gap = next.t - previous.t
            if gap > WorkoutAnalysis.maximumGap { return (nil, []) }
            let f = (t - previous.t) / gap
            return (GeoPoint(latitude: previous.latitude + (next.latitude - previous.latitude) * f,
                             longitude: previous.longitude + (next.longitude - previous.longitude) * f), tail)
        }
        // 기간의 마지막 점 뒤: 가까우면 마지막 점에 머문다
        return t - previous.t > WorkoutAnalysis.maximumGap ? (nil, []) : (here, tail)
    }

    /// 기간 경계(첫 시작·마지막 끝 제외)까지의 영상 시간 거리 / fadeSeconds, 1로 자른다.
    private func fade(_ index: Int) -> Double {
        let videoTime = Double(min(index, lastMovingFrame)) / Double(ReplayTimeline.fps)
        let videoLength = Double(lastMovingFrame) / Double(ReplayTimeline.fps)
        var before = 0.0
        var nearest = Double.infinity
        for period in periods.dropLast() {
            before += period.length
            nearest = min(nearest, abs(videoTime - before / totalLength * videoLength))
        }
        return min(1, nearest / ReplayTimeline.fadeSeconds)
    }
}
