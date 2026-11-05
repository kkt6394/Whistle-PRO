public struct ActiveSinBin: Equatable, Sendable {
    public var eventId: String
    public var team: Team
    public var number: Int?
    /// 남은 일시 퇴장 시간(초). 경기 시계 기준이라 휴식 중에는 줄지 않는다.
    public var remainingSeconds: Double
    /// 두 번째 일시 퇴장 (끝나면 출전 금지)
    public var isSecond: Bool = false

    /// 화면 표시용 (올림)
    public var remaining: Int { wholeSeconds(remainingSeconds, rounding: .up) }
}

/// 두 번째 일시 퇴장 시간을 마쳐 남은 경기에 못 나오는 선수 (IFAB 일시 퇴장 지침)
public struct BarredPlayer: Equatable, Sendable {
    /// 두 번째 일시 퇴장 이벤트
    public var eventId: String
    public var team: Team
    public var number: Int
    /// 그 선수를 나가는 선수로 고른 교체가 기록됨 (팀 인원이 다시 참)
    public var filled: Bool

    public init(eventId: String, team: Team, number: Int, filled: Bool) {
        self.eventId = eventId
        self.team = team
        self.number = number
        self.filled = filled
    }
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
        // 승부차기·종료 후에는 일시 퇴장이 이어지지 않는다 (Law 10.3: 일시 퇴장 중인 선수도 키커 자격)
        switch phase {
        case .penalties, .finished: return []
        default: break
        }
        let resolved = resolvedEvents
        return resolved.enumerated().compactMap { index, r in
            guard r.kind == .sinBin, !r.warnings.contains(.overlappingSinBin),
                  case .card(let team, let number, _) = r.event.payload else { return nil }
            // 일시 퇴장 중 퇴장당하면 일시 퇴장은 끝나고 퇴장 인원으로만 센다
            if let number, resolved[(index + 1)...].contains(where: {
                $0.isSendingOff && $0.event.payload.team == team && $0.event.payload.shirtNumbers.first == number
            }) {
                return nil
            }
            let left = Double(rules.sinBinLength) - playedSeconds(from: r.event.timestamp, to: now)
            guard left > 0 else { return nil }
            return ActiveSinBin(eventId: r.event.id, team: team, number: number, remainingSeconds: left, isSecond: r.isSecondSinBin)
        }
    }

    /// 두 번째 일시 퇴장 시간이 끝났고 그 뒤 퇴장당하지 않은 선수 (기록 순서).
    /// 승부차기·종료 상태에서는 시간과 상관없이 끝난 것으로 본다.
    public func barredPlayers(at now: Double) -> [BarredPlayer] {
        let allEnded = switch phase {
        case .penalties, .finished: true
        default: false
        }
        let resolved = resolvedEvents
        var seen: Set<PlayerKey> = []
        var result: [BarredPlayer] = []
        for (index, r) in resolved.enumerated() {
            guard r.isSecondSinBin, case .card(let team, let number?, _) = r.event.payload,
                  seen.insert(PlayerKey(team: team, number: number)).inserted else { continue }
            let later = resolved[(index + 1)...]
            // 그 뒤 퇴장당하면 퇴장으로만 센다
            if later.contains(where: {
                $0.isSendingOff && $0.event.payload.team == team && $0.event.payload.shirtNumbers.first == number
            }) {
                continue
            }
            guard allEnded || playedSeconds(from: r.event.timestamp, to: now) >= Double(rules.sinBinLength) else { continue }
            let filled = later.contains {
                if case .substitution(team, number?, _) = $0.event.payload { true } else { false }
            }
            result.append(BarredPlayer(eventId: r.event.id, team: team, number: number, filled: filled))
        }
        return result
    }
}
