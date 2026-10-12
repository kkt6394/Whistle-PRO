public struct ActiveSinBin: Equatable, Sendable {
    public var eventId: String
    public var team: Team
    public var number: Int?
    /// 남은 일시 퇴장 시간(초). 경기 시계 기준이라 휴식 중에는 줄지 않는다.
    public var remainingSeconds: Double

    /// 화면 표시용 (올림)
    public var remaining: Int { Int(remainingSeconds.rounded(.up)) }
}

extension MatchState {
    /// start~now 사이에 실제로 기간이 진행된 시간(휴식 제외)
    func playedSeconds(from start: Double, to now: Double) -> Double {
        periods.reduce(0) { total, p in
            let from = max(p.startedAt, start)
            let to = min(p.endedAt ?? now, now)
            return total + max(0, to - from)
        }
    }

    public func activeSinBins(at now: Double) -> [ActiveSinBin] {
        resolvedEvents.compactMap { r in
            guard r.kind == .sinBin, case .card(let team, let number, _) = r.event.payload else { return nil }
            let left = Double(rules.sinBinLength) - playedSeconds(from: r.event.timestamp, to: now)
            guard left > 0 else { return nil }
            return ActiveSinBin(eventId: r.event.id, team: team, number: number, remainingSeconds: left)
        }
    }
}
