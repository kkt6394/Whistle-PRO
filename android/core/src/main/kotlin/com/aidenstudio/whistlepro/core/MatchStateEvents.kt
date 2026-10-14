package com.aidenstudio.whistlepro.core

/** 골이면 득점이 인정되는 팀, 아니면 null */
internal val EventPayload.scoringSide: Team?
    get() = (this as? EventPayload.Goal)?.let { if (it.ownGoal) it.team.opponent else it.team }

internal val EventPayload.shirtNumbers: List<Int>
    get() = when (this) {
        is EventPayload.Goal -> listOfNotNull(number)
        is EventPayload.Card -> listOfNotNull(number)
        is EventPayload.PenaltyKick -> listOfNotNull(number)
        is EventPayload.Substitution -> listOfNotNull(numberOut, numberIn)
    }

internal fun MatchState.recordEvent(payload: EventPayload, id: String, t: Double): MatchState {
    validateContent(payload)
    validatePhase(payload)
    if (payload is EventPayload.PenaltyKick) checkShootoutTurn(payload.team)
    return copy(events = events + RecordedEvent(id, t, payload, eventTime(t))).finishShootoutIfDecided()
}

/**
 * 같은 종류 안에서만 내용을 바꾼다. 시각은 원래 기록 시각을 유지한다.
 * 승부차기 킥은 고칠 수 있는 상태에서만, 팀은 바꿀 수 없다(차례가 꼬이므로).
 */
internal fun MatchState.editEvent(id: String, payload: EventPayload): MatchState {
    val i = events.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fail(EngineError.UNKNOWN_EVENT)
    if (events[i].payload.typeName != payload.typeName) fail(EngineError.INVALID_VALUE)
    validateContent(payload)
    if (payload is EventPayload.PenaltyKick) {
        if (!shootoutIsEditable || events[i].payload.team != payload.team) fail(EngineError.INVALID_IN_PHASE)
    }
    if (events[i].payload.scoringSide != payload.scoringSide) ensureScoreIsStillOpen()
    val edited = events.toMutableList().also { it[i] = it[i].copy(payload = payload) }
    return copy(events = edited).reopenShootoutIfUndecided().finishShootoutIfDecided()
}

/** 승부차기 킥은 마지막 킥만 지울 수 있다. */
internal fun MatchState.deleteEvent(id: String): MatchState {
    val i = events.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fail(EngineError.UNKNOWN_EVENT)
    if (events[i].payload is EventPayload.PenaltyKick) {
        val lastKick = events.indexOfLast { it.payload is EventPayload.PenaltyKick }
        if (!shootoutIsEditable || lastKick != i) fail(EngineError.INVALID_IN_PHASE)
    }
    if (events[i].payload.scoringSide != null) ensureScoreIsStillOpen()
    return copy(events = events.filterIndexed { index, _ -> index != i }).reopenShootoutIfUndecided()
}

/**
 * 점수가 바뀌는 골 수정·삭제는 기간 진행 중이나 휴식 중에만 허용한다.
 * 마지막 기간이 끝난 뒤에는 이미 점수로 연장·승부차기·종료가 결정됐으므로, 고치면 기록이 모순된다.
 */
private fun MatchState.ensureScoreIsStillOpen() {
    if (!(phase.isPeriod || phase.isInterval)) fail(EngineError.INVALID_IN_PHASE)
}

private fun MatchState.validateContent(payload: EventPayload) {
    if (!payload.shirtNumbers.all { it in 0..99 }) fail(EngineError.INVALID_VALUE)
    if (payload is EventPayload.Card && payload.kind == CardKind.SIN_BIN && !rules.sinBin) fail(EngineError.INVALID_VALUE)
}

/** 골은 기간 진행 중에만, 카드는 경기 시작 후 종료 전까지, 교체는 승부차기 전까지, 승부차기 킥은 승부차기 중에만. */
private fun MatchState.validatePhase(payload: EventPayload) {
    val allowed = when (payload) {
        is EventPayload.Goal -> phase.isPeriod
        is EventPayload.Card -> phase.isPeriod || phase.isInterval || phase == Phase.PreExtraTime || phase == Phase.Penalties
        is EventPayload.Substitution -> phase.isPeriod || phase.isInterval || phase == Phase.PreExtraTime
        is EventPayload.PenaltyKick -> phase == Phase.Penalties
    }
    if (!allowed) fail(EngineError.INVALID_IN_PHASE)
}

private fun MatchState.eventTime(t: Double): EventTime {
    val p = periods.lastOrNull() ?: fail(EngineError.INVALID_IN_PHASE)
    val elapsed = wholeSeconds(p.elapsedSeconds(t))
    return EventTime(p.ref, p.endedAt == null, p.offset + elapsed)
}
