extension MatchState {
    /// 정규·연장 득점. 자책골은 상대 팀 득점. 승부차기는 포함하지 않는다.
    public var score: TeamPair<Int> {
        var s = TeamPair(home: 0, away: 0)
        for e in events {
            if case .goal(let team, _, let ownGoal) = e.payload {
                s[ownGoal ? team.opponent : team] += 1
            }
        }
        return s
    }

    /// 진행 중이거나 다음에 시작할 기간의 킥오프 팀. 모르면 nil.
    public var kickoffTeam: Team? {
        let ref: PeriodRef
        switch phase {
        case .notStarted: ref = PeriodRef(.regular, 1)
        case .period(let current): ref = current
        case .interval(let after): ref = PeriodRef(after.kind, after.index + 1)
        case .preExtraTime: ref = PeriodRef(.extra, 1)
        case .penalties, .finished: return nil
        }
        guard let first = ref.kind == .regular ? regularKickoff : extraKickoff else { return nil }
        return rules.alternateKickoff && ref.index % 2 == 0 ? first.opponent : first
    }

    /// ref 기간이 끝난 뒤의 휴식 길이(초)
    public func intervalLength(after ref: PeriodRef) -> Int {
        switch ref.kind {
        case .extra: rules.extraTimeBreakLength
        case .regular: rules.periodCount == 4 && ref.index != 2 ? rules.quarterBreakLength : rules.halfTimeLength
        }
    }

    /// 마지막으로 시작한 기간의 라벨 (시작 전이면 nil)
    public var currentPeriodLabel: String? {
        periods.last?.ref.label(periodCount: rules.periodCount)
    }
}
