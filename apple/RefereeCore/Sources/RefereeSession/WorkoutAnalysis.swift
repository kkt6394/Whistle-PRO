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
    /// 속도 구간별 거리 (GPS 경로가 없으면 nil)
    public var zones: SpeedZones? = nil
}

/// 속도 구간별 이동 거리 (m)
public struct SpeedZones: Codable, Equatable, Sendable {
    /// 7km/h 미만
    public var walking: Double
    /// 7~13km/h
    public var jogging: Double
    /// 13~20km/h
    public var running: Double
    /// 20km/h 이상 (스프린트 기준과 같다)
    public var sprinting: Double

    public init(walking: Double = 0, jogging: Double = 0, running: Double = 0, sprinting: Double = 0) {
        self.walking = walking
        self.jogging = jogging
        self.running = running
        self.sprinting = sprinting
    }

    public var total: Double { walking + jogging + running + sprinting }

    /// 비율은 그대로 두고 합이 total이 되게 (GPS 거리와 워치 거리 차이를 맞춘다)
    public func scaled(to total: Double) -> SpeedZones {
        guard self.total > 0, total > 0 else { return self }
        let factor = total / self.total
        return SpeedZones(walking: walking * factor, jogging: jogging * factor, running: running * factor, sprinting: sprinting * factor)
    }

    public static func + (a: SpeedZones, b: SpeedZones) -> SpeedZones {
        SpeedZones(walking: a.walking + b.walking, jogging: a.jogging + b.jogging,
                   running: a.running + b.running, sprinting: a.sprinting + b.sprinting)
    }
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
    /// 조깅 시작 속도 7km/h (m/s)
    public static let joggingSpeed = 7.0 / 3.6
    /// 달리기 시작 속도 13km/h (m/s)
    public static let runningSpeed = 13.0 / 3.6
    /// 사람이 낼 수 있는 가장 빠른 이동 (m/s): 전력 질주(약 10 m/s)보다 조금 빠르게. 이보다 빨리 튄 점은 위치 오류
    public static let maximumSpeed = 12.0

    /// 쓸 GPS 지점: 정확도 20m 이하, 시간순, 혼자 튄 점은 뺀다 — 앞·뒤 지점 모두와 maximumSpeed보다 빠르게 떨어져 있고
    /// 앞·뒤끼리는 그렇지 않은 점 (휴대폰 위치가 한 번 섞이는 등, 2026-10-30). 거리·히트맵·이벤트 위치·리플레이가 함께 쓴다
    public static func usableRoute(_ route: [RoutePoint]) -> [RoutePoint] {
        let points = route.filter { $0.accuracy >= 0 && $0.accuracy <= maximumAccuracy }.sorted { $0.t < $1.t }
        guard points.count >= 3 else { return points }
        return points.indices.compactMap { i in
            if i == 0 || i == points.count - 1 { return points[i] }
            let before = points[i - 1], point = points[i], after = points[i + 1]
            let spike = jumps(before, point) && jumps(point, after) && !jumps(before, after)
            return spike ? nil : point
        }
    }

    private static func jumps(_ a: RoutePoint, _ b: RoutePoint) -> Bool {
        let dt = b.t - a.t
        return dt > 0 && distance(a, b) / dt > maximumSpeed
    }

    /// GPS 경로의 속도 구간별 거리. 연속한 두 지점(정확도 20m 이하, 간격 3초 이하) 사이 거리를
    /// 두 지점 평균 속도의 구간에 더한다. 속도를 모르면 거리÷시간을 쓴다. 쓸 구간이 없으면 nil.
    public static func speedZones(_ route: [RoutePoint]) -> SpeedZones? {
        var zones = SpeedZones()
        var found = false
        var previous: RoutePoint?
        for point in usableRoute(route) {
            defer { previous = point }
            guard let previous else { continue }
            let dt = point.t - previous.t
            guard dt > 0, dt <= maximumGap else { continue }
            let meters = distance(previous, point)
            guard meters.isFinite else { continue }
            let speed = previous.speed >= 0 && point.speed >= 0 ? (previous.speed + point.speed) / 2 : meters / dt
            found = true
            switch speed {
            case ..<joggingSpeed: zones.walking += meters
            case ..<runningSpeed: zones.jogging += meters
            case ..<sprintSpeed: zones.running += meters
            default: zones.sprinting += meters
            }
        }
        return found ? zones : nil
    }

    /// 두 지점 사이 거리(m). 경기장 크기라 평면 근사로 충분하다.
    static func distance(_ a: RoutePoint, _ b: RoutePoint) -> Double {
        let metersPerDegree = 111_320.0
        let north = (b.latitude - a.latitude) * metersPerDegree
        let east = (b.longitude - a.longitude) * metersPerDegree * cos((a.latitude + b.latitude) / 2 * .pi / 180)
        return (north * north + east * east).squareRoot()
    }

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
        for point in usableRoute(route) {
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
    /// includeZones: 속도별 거리까지 (워치는 5초마다 부르는데 보여 주지 않아 뺀다 — M7a 리뷰)
    public static func overall(_ log: WorkoutLog, state: MatchState, now: Double, includeZones: Bool = true) -> WorkoutStats {
        stats(log, ranges: state.periods.map { ($0.startedAt, $0.endedAt ?? now) }, label: nil, includeZones: includeZones)
    }

    /// 여러 구간의 값만 합친다 (구역 계산 없음). 리플레이 영상이 경기 도중 값을 구할 때 쓴다.
    public static func stats(_ log: WorkoutLog, ranges: [(Double, Double)]) -> WorkoutStats {
        stats(log, ranges: ranges, label: nil, includeZones: false)
    }

    private static func stats(_ log: WorkoutLog, ranges: [(Double, Double)], label: String?, includeZones: Bool = true) -> WorkoutStats {
        func inRanges(_ t: Double) -> Bool { ranges.contains { t >= $0.0 && t <= $0.1 } }
        let heartRates = log.heartRate.filter { inRanges($0.t) }.map(\.value)
        let average = heartRates.isEmpty ? nil : wholeSeconds(heartRates.reduce(0, +) / Double(heartRates.count), rounding: .toNearestOrAwayFromZero)
        let distances = ranges.map { delta(log.distance, restarts: log.restarts, from: $0.0, to: $0.1) }
        let distance = distances.reduce(0, +)
        // 기간마다 따로 계산해 휴식 시간을 건너는 구간이 생기지 않게 한다. 비율은 GPS로, 양은 그 기간의 워치 거리로 맞춘다:
        // GPS가 일부 기간에만 있으면 그 기간 거리에만 (전체 거리로 늘리면 그 비율이 다른 기간까지 퍼졌다),
        // 워치 거리가 0인 기간은 넣지 않는다 (표시 거리 0km와 합이 맞지 않았다). 워치 거리 샘플이 아예 없으면 GPS 값 그대로.
        // 서 있을 때 GPS 흔들림은 걷기에 쌓이는 근사치다 (M7a 리뷰)
        let gpsZones = !includeZones ? [] : zip(ranges, distances).compactMap { range, watchDistance -> SpeedZones? in
            guard log.distance.isEmpty || watchDistance > 0 else { return nil }
            return speedZones(log.route.filter { $0.t >= range.0 && $0.t <= range.1 })?.scaled(to: watchDistance)
        }
        let zones = gpsZones.isEmpty ? nil : gpsZones.reduce(SpeedZones(), +)
        return WorkoutStats(
            label: label,
            averageHeartRate: average,
            maxHeartRate: heartRates.max().map { wholeSeconds($0, rounding: .toNearestOrAwayFromZero) },
            distance: distance,
            energy: ranges.reduce(0) { $0 + delta(log.energy, restarts: log.restarts, from: $1.0, to: $1.1) },
            sprints: ranges.reduce(0) { total, range in
                total + sprintCount(log.route.filter { $0.t >= range.0 && $0.t <= range.1 })
            },
            zones: zones)
    }

    /// 누적값 샘플에서 구간 동안 늘어난 양. 운동 세션을 새로 시작한 시각(restarts)을 지나면 0부터 다시 쌓인 것으로 보고,
    /// 그 밖에 값이 잠깐 줄면(순서가 뒤바뀐 샘플) 늘어난 양이 없는 것으로 본다.
    /// restarts가 nil(예전 기록)이면 예전처럼 값이 줄면 재시작으로 본다.
    private static func delta(_ samples: [WorkoutSample], restarts: [Double]?, from start: Double, to end: Double) -> Double {
        guard let restarts else { return legacyDelta(samples, from: start, to: end) }
        var base = 0.0
        var previousT = -Double.infinity
        var total = 0.0
        for sample in samples {
            if restarts.contains(where: { $0 > previousT && $0 <= sample.t }) { base = 0 }
            let increment = max(0, sample.value - base)
            if sample.t > start && sample.t <= end { total += increment }
            base = max(base, sample.value)
            previousT = sample.t
        }
        return total
    }

    private static func legacyDelta(_ samples: [WorkoutSample], from start: Double, to end: Double) -> Double {
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
