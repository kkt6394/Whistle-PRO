/// 초 단위 값을 Int로 바꾼다. 손상된 로그의 무한대·NaN·아주 큰 값으로 트랩하지 않게 ±1조 초로 제한한다.
public func wholeSeconds(_ value: Double, rounding: FloatingPointRoundingRule = .down) -> Int {
    guard value.isFinite else { return 0 }
    return Int(min(max(value, -1e12), 1e12).rounded(rounding))
}

public struct ClockReading: Equatable, Sendable {
    public var period: PeriodRef
    /// 기간 시작 후 경과(초, 내림)
    public var elapsed: Int
    /// 누적 경기 시각 = offset + elapsed (추가시간 포함, 클램프하지 않음)
    public var matchSecond: Int
    /// 규정 시간까지 남은 시간 (0 이상)
    public var remaining: Int
    /// 규정 시간 초과분 (0 이상)
    public var addedTime: Int
    /// 이 기간의 누적 중단 시간 = 권장 추가시간
    public var stoppageTotal: Int
    public var stoppageRunning: Bool
}

extension PeriodRecord {
    func elapsedSeconds(at now: Double) -> Double {
        max(0, (endedAt ?? now) - startedAt)
    }

    func stoppageSeconds(at now: Double) -> Double {
        stoppages.reduce(0) { $0 + max(0, ($1.end ?? now) - $1.start) }
    }
}

extension MatchState {
    /// 마지막으로 시작한 기간의 시계. 기간이 끝났으면 종료 시점에 멈춘 값이다.
    public func clock(at now: Double) -> ClockReading? {
        guard let p = periods.last else { return nil }
        let elapsed = wholeSeconds(p.elapsedSeconds(at: now))
        return ClockReading(
            period: p.ref,
            elapsed: elapsed,
            matchSecond: p.offset + elapsed,
            remaining: max(0, p.nominalLength - elapsed),
            addedTime: max(0, elapsed - p.nominalLength),
            stoppageTotal: wholeSeconds(p.stoppageSeconds(at: now)),
            stoppageRunning: p.stoppages.last.map { $0.end == nil } ?? false)
    }

    /// 기간별 누적 중단 시간(초)
    public func stoppageTotals(at now: Double) -> [Int] {
        periods.map { wholeSeconds($0.stoppageSeconds(at: now)) }
    }

    /// 휴식 중일 때 남은 휴식 시간(초, 올림, 0 이상). 휴식이 아니면 nil.
    public func intervalRemaining(at now: Double) -> Int? {
        guard case .interval(let after) = phase, let end = periods.last?.endedAt else { return nil }
        let left = Double(intervalLength(after: after)) - max(0, now - end)
        return max(0, wholeSeconds(left, rounding: .up))
    }

    mutating func toggleStoppage(at t: Double) throws(EngineError) {
        guard phase.isPeriod else { throw .invalidInPhase }
        let i = periods.count - 1
        if let s = periods[i].stoppages.indices.last, periods[i].stoppages[s].end == nil {
            periods[i].stoppages[s].end = t
        } else {
            periods[i].stoppages.append(StoppageInterval(start: t, end: nil))
        }
    }
}
