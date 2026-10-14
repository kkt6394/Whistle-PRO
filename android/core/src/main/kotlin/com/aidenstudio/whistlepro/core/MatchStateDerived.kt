package com.aidenstudio.whistlepro.core

/** 정규·연장 득점. 자책골은 상대 팀 득점. 승부차기는 포함하지 않는다. */
val MatchState.score: TeamPair<Int>
    get() {
        var s = TeamPair(0, 0)
        for (e in events) {
            val side = e.payload.scoringSide ?: continue
            s = s.with(side, s[side] + 1)
        }
        return s
    }

/** 진행 중이거나 다음에 시작할 기간의 킥오프 팀. 모르면 null. */
val MatchState.kickoffTeam: Team?
    get() {
        val ref = when (val p = phase) {
            Phase.NotStarted -> PeriodRef(PeriodRef.Kind.REGULAR, 1)
            is Phase.Period -> p.ref
            is Phase.Interval -> PeriodRef(p.after.kind, p.after.index + 1)
            Phase.PreExtraTime -> PeriodRef(PeriodRef.Kind.EXTRA, 1)
            Phase.Penalties, is Phase.Finished -> return null
        }
        val first = (if (ref.kind == PeriodRef.Kind.REGULAR) regularKickoff else extraKickoff) ?: return null
        return if (rules.alternateKickoff && ref.index % 2 == 0) first.opponent else first
    }

/** ref 기간이 끝난 뒤의 휴식 길이(초) */
fun MatchState.intervalLength(after: PeriodRef): Int = when (after.kind) {
    PeriodRef.Kind.EXTRA -> rules.extraTimeBreakLength
    PeriodRef.Kind.REGULAR ->
        if (rules.periodCount == 4 && after.index != 2) rules.quarterBreakLength else rules.halfTimeLength
}

/** 마지막으로 시작한 기간의 라벨 (시작 전이면 null). 승부차기 중이거나 승부차기로 끝났으면 "PK". */
val MatchState.currentPeriodLabel: String?
    get() {
        if (phase == Phase.Penalties) return "PK"
        if (phase is Phase.Finished && shootout.kicks.home + shootout.kicks.away > 0) return "PK"
        return periods.lastOrNull()?.ref?.label(rules.periodCount)
    }
