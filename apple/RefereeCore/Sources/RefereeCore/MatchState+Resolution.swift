public enum ResolvedEventKind: String, Sendable {
    case goal, ownGoal, yellow, secondYellow, red, sinBin, substitution, penaltyKick
}

public enum EventWarning: String, Sendable {
    case substitutionLimitExceeded, repeatPenaltyKicker
}

public struct ResolvedEvent: Equatable, Sendable {
    public var event: RecordedEvent
    public var kind: ResolvedEventKind
    public var warnings: [EventWarning]
}

public enum MatchWarning: Equatable, Sendable {
    case belowMinimumPlayers(Team)

    public var code: String {
        switch self {
        case .belowMinimumPlayers(let team): "belowMinimumPlayers:\(team.rawValue)"
        }
    }
}

private struct PlayerKey: Hashable {
    let team: Team
    let number: Int
}

extension MatchState {
    /// 이벤트를 기록 순서대로 해석한다. 수정·삭제가 있으면 다시 계산되므로 저장하지 않는다.
    public var resolvedEvents: [ResolvedEvent] {
        var cautioned: Set<PlayerKey> = []
        var subs = TeamPair(home: 0, away: 0)
        let extraStart = extraTimeReachedAt
        return events.map { e in
            var warnings: [EventWarning] = []
            let kind: ResolvedEventKind
            switch e.payload {
            case .goal(_, _, let ownGoal):
                kind = ownGoal ? .ownGoal : .goal
            case .card(let team, let number, let card):
                switch card {
                case .red: kind = .red
                case .sinBin: kind = .sinBin
                case .yellow:
                    if rules.secondYellowSendOff, let number,
                       !cautioned.insert(PlayerKey(team: team, number: number)).inserted {
                        kind = .secondYellow
                    } else {
                        kind = .yellow
                    }
                }
            case .substitution(let team, _, _):
                kind = .substitution
                subs[team] += 1
                if let limit = substitutionLimit(at: e.timestamp, extraStart: extraStart), subs[team] > limit {
                    warnings.append(.substitutionLimitExceeded)
                }
            case .penaltyKick:
                kind = .penaltyKick
            }
            return ResolvedEvent(event: e, kind: kind, warnings: warnings)
        }
    }

    public var substitutionCounts: TeamPair<Int> {
        var counts = TeamPair(home: 0, away: 0)
        for e in events {
            if case .substitution(let team, _, _) = e.payload { counts[team] += 1 }
        }
        return counts
    }

    /// 퇴장(레드, 경고 누적) 인원
    public var sentOffCounts: TeamPair<Int> {
        var counts = TeamPair(home: 0, away: 0)
        for r in resolvedEvents where r.kind == .red || r.kind == .secondYellow {
            counts[r.event.payload.team] += 1
        }
        return counts
    }

    public func playersOnField(at now: Double) -> TeamPair<Int> {
        let off = sentOffCounts
        return TeamPair(home: rules.playersPerTeam - off.home, away: rules.playersPerTeam - off.away)
    }

    public func matchWarnings(at now: Double) -> [MatchWarning] {
        guard let minimum = rules.minimumPlayers else { return [] }
        let onField = playersOnField(at: now)
        return Team.allCases.filter { onField[$0] < minimum }.map { .belowMinimumPlayers($0) }
    }

    /// 연장전에 들어간 경우 마지막 정규 기간이 끝난 시각
    var extraTimeReachedAt: Double? {
        guard rules.extraTime,
              let lastRegular = periods.first(where: { $0.ref == PeriodRef(.regular, rules.periodCount) }),
              let end = lastRegular.endedAt,
              phase == .preExtraTime || periods.contains(where: { $0.ref.kind == .extra })
        else { return nil }
        return end
    }

    private func substitutionLimit(at t: Double, extraStart: Double?) -> Int? {
        guard !rules.returnSubstitutions, let limit = rules.substitutionLimit else { return nil }
        if let extraStart, t >= extraStart { return limit + rules.extraTimeExtraSubs }
        return limit
    }
}
