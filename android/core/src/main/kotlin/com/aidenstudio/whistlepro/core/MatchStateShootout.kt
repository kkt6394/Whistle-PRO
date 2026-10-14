package com.aidenstudio.whistlepro.core

data class ShootoutStatus(
    val goals: TeamPair<Int>,
    val kicks: TeamPair<Int>,
    /** 다음에 찰 팀. 선축 팀 미정이거나 승부가 났으면 null. */
    val next: Team?,
    val decided: Boolean,
)

val MatchState.shootout: ShootoutStatus
    get() {
        var goals = TeamPair(0, 0)
        var kicks = TeamPair(0, 0)
        for (e in events) {
            val p = e.payload as? EventPayload.PenaltyKick ?: continue
            kicks = kicks.with(p.team, kicks[p.team] + 1)
            if (p.scored) goals = goals.with(p.team, goals[p.team] + 1)
        }
        val n = rules.penaltyKicksPerTeam
        val decided = if (kicks.home <= n && kicks.away <= n) {
            // 기본 키커 수 안에서는 남은 킥을 다 넣어도 못 따라잡으면 끝
            goals.home > goals.away + (n - kicks.away) || goals.away > goals.home + (n - kicks.home)
        } else {
            // 그 후에는 같은 횟수에서 한 골 앞서면 끝
            kicks.home == kicks.away && goals.home != goals.away
        }
        val first = shootoutFirstTeam
        val next = if (!decided && first != null) {
            if ((kicks.home + kicks.away) % 2 == 0) first else first.opponent
        } else {
            null
        }
        return ShootoutStatus(goals, kicks, next, decided)
    }

internal fun MatchState.setShootoutFirstTeam(team: Team): MatchState {
    if (phase != Phase.Penalties || events.any { it.payload is EventPayload.PenaltyKick }) fail(EngineError.INVALID_IN_PHASE)
    return copy(shootoutFirstTeam = team)
}

internal fun MatchState.checkShootoutTurn(team: Team) {
    if (shootoutFirstTeam == null) fail(EngineError.SHOOTOUT_FIRST_TEAM_MISSING)
    if (shootout.next != team) fail(EngineError.NOT_YOUR_TURN)
}

private val MatchState.finishedCompleted: Boolean
    get() = (phase as? Phase.Finished)?.reason == FinishReason.Completed

/** 승부차기 중이거나 승부차기로 끝난 경기 (킥을 고칠 수 있는 상태) */
internal val MatchState.shootoutIsEditable: Boolean
    get() = phase == Phase.Penalties || (finishedCompleted && shootoutFirstTeam != null)

/** 고친 결과 승부가 다시 미정이면 승부차기로 되돌린다. */
internal fun MatchState.reopenShootoutIfUndecided(): MatchState =
    if (finishedCompleted && shootoutFirstTeam != null && !shootout.decided) copy(phase = Phase.Penalties) else this

internal fun MatchState.finishShootoutIfDecided(): MatchState =
    if (phase == Phase.Penalties && shootout.decided) copy(phase = Phase.Finished(FinishReason.Completed)) else this
