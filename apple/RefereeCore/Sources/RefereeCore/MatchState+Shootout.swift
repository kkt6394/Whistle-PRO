public struct ShootoutStatus: Equatable, Sendable {
    public var goals: TeamPair<Int>
    public var kicks: TeamPair<Int>
    /// 다음에 찰 팀. 선축 팀 미정이거나 승부가 났으면 nil.
    public var next: Team?
    public var decided: Bool
}

extension EventPayload {
    var isPenaltyKick: Bool { if case .penaltyKick = self { true } else { false } }
}

extension MatchState {
    public var shootout: ShootoutStatus {
        var goals = TeamPair(home: 0, away: 0)
        var kicks = TeamPair(home: 0, away: 0)
        for e in events {
            if case .penaltyKick(let team, _, let scored) = e.payload {
                kicks[team] += 1
                if scored { goals[team] += 1 }
            }
        }
        let n = rules.penaltyKicksPerTeam
        let decided: Bool
        if kicks.home <= n && kicks.away <= n {
            // 기본 키커 수 안에서는 남은 킥을 다 넣어도 못 따라잡으면 끝
            decided = goals.home > goals.away + (n - kicks.away) || goals.away > goals.home + (n - kicks.home)
        } else {
            // 그 후에는 같은 횟수에서 한 골 앞서면 끝
            decided = kicks.home == kicks.away && goals.home != goals.away
        }
        var next: Team?
        if !decided, let first = shootoutFirstTeam {
            next = (kicks.home + kicks.away) % 2 == 0 ? first : first.opponent
        }
        return ShootoutStatus(goals: goals, kicks: kicks, next: next, decided: decided)
    }

    mutating func setShootoutFirstTeam(_ team: Team) throws(EngineError) {
        guard phase == .penalties, !events.contains(where: { $0.payload.isPenaltyKick }) else { throw .invalidInPhase }
        shootoutFirstTeam = team
    }

    func checkShootoutTurn(_ team: Team) throws(EngineError) {
        guard shootoutFirstTeam != nil else { throw .shootoutFirstTeamMissing }
        guard shootout.next == team else { throw .notYourTurn }
    }

    mutating func finishShootoutIfDecided() {
        if phase == .penalties && shootout.decided { phase = .finished(.completed) }
    }
}
