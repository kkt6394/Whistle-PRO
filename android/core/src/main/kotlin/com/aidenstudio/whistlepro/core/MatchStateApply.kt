package com.aidenstudio.whistlepro.core

/** 명령 하나를 적용한 새 상태. 허용되지 않으면 EngineException을 던지고 이 상태는 그대로다. */
fun MatchState.apply(command: Command): MatchState {
    val t = command.timestamp
    return when (val k = command.kind) {
        is CommandKind.SetKickoffTeam -> setKickoff(k.team)
        CommandKind.StartPeriod -> startPeriod(t)
        CommandKind.EndPeriod -> endPeriod(t)
        is CommandKind.AdjustPeriodLength -> adjustPeriodLength(k.seconds)
        is CommandKind.AbandonMatch -> abandon(k.reason, t)
        CommandKind.ToggleStoppage -> toggleStoppage(t)
        is CommandKind.RecordEvent -> recordEvent(k.payload, command.id, t)
        is CommandKind.EditEvent -> editEvent(k.id, k.payload)
        is CommandKind.DeleteEvent -> deleteEvent(k.id)
        is CommandKind.SetShootoutFirstTeam -> setShootoutFirstTeam(k.team)
        CommandKind.Undo -> fail(EngineError.INVALID_IN_PHASE) // undo는 MatchEngine.reduce가 로그 단계에서 처리한다
    }
}

private fun MatchState.setKickoff(team: Team): MatchState = when (phase) {
    Phase.NotStarted -> copy(regularKickoff = team)
    Phase.PreExtraTime -> copy(extraKickoff = team)
    else -> fail(EngineError.INVALID_IN_PHASE)
}

private fun MatchState.startPeriod(t: Double): MatchState {
    val ref = when (val p = phase) {
        Phase.NotStarted -> PeriodRef(PeriodRef.Kind.REGULAR, 1)
        is Phase.Interval -> PeriodRef(p.after.kind, p.after.index + 1)
        Phase.PreExtraTime -> PeriodRef(PeriodRef.Kind.EXTRA, 1)
        else -> fail(EngineError.INVALID_IN_PHASE)
    }
    val length = if (ref.kind == PeriodRef.Kind.REGULAR) regularPeriodLength else extraPeriodLength
    val offset = periods.sumOf { it.nominalLength }
    return copy(
        periods = periods + PeriodRecord(ref, t, null, length, offset, emptyList()),
        phase = Phase.Period(ref),
    )
}

private fun MatchState.endPeriod(t: Double): MatchState {
    val p = phase as? Phase.Period ?: fail(EngineError.INVALID_IN_PHASE)
    return closeRunningPeriod(t).let { it.copy(phase = it.phaseAfterEnding(p.ref)) }
}

/** 정규/연장 중 지금 해당하는 쪽의 길이를 바꾼다. 기간 진행 중이면 그 기간에도 즉시 적용된다. */
private fun MatchState.adjustPeriodLength(seconds: Int): MatchState {
    if (seconds !in 60..5400) fail(EngineError.INVALID_VALUE)
    val targetsExtra = when (val p = phase) {
        Phase.NotStarted -> false
        is Phase.Period -> p.ref.kind == PeriodRef.Kind.EXTRA
        is Phase.Interval -> p.after.kind == PeriodRef.Kind.EXTRA
        Phase.PreExtraTime -> true
        Phase.Penalties, is Phase.Finished -> fail(EngineError.INVALID_IN_PHASE)
    }
    var next = if (targetsExtra) copy(extraPeriodLength = seconds) else copy(regularPeriodLength = seconds)
    if (phase.isPeriod) next = next.copy(periods = periods.updateLast { it.copy(nominalLength = seconds) })
    return next
}

private fun MatchState.abandon(reason: String, t: Double): MatchState {
    if (phase is Phase.Finished) fail(EngineError.INVALID_IN_PHASE)
    val closed = if (phase.isPeriod) closeRunningPeriod(t) else this
    return closed.copy(phase = Phase.Finished(FinishReason.Abandoned(reason)), abandonedDuringShootout = phase == Phase.Penalties)
}

/** 진행 중인 기간과 열린 중단 구간을 t에 닫는다. */
internal fun MatchState.closeRunningPeriod(t: Double): MatchState = copy(periods = periods.updateLast { p ->
    val stoppages = p.stoppages.lastOrNull()?.takeIf { it.end == null }
        ?.let { open -> p.stoppages.dropLast(1) + open.copy(end = t) } ?: p.stoppages
    p.copy(endedAt = t, stoppages = stoppages)
})

private fun MatchState.phaseAfterEnding(ref: PeriodRef): Phase {
    val tied = score.home == score.away
    return when (ref.kind) {
        PeriodRef.Kind.REGULAR -> when {
            ref.index < rules.periodCount -> Phase.Interval(ref)
            tied && rules.extraTime -> Phase.PreExtraTime
            tied && rules.penalties -> Phase.Penalties
            else -> Phase.Finished(FinishReason.Completed)
        }
        PeriodRef.Kind.EXTRA -> when {
            ref.index < 2 -> Phase.Interval(ref)
            tied && rules.penalties -> Phase.Penalties
            else -> Phase.Finished(FinishReason.Completed)
        }
    }
}
