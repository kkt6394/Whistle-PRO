package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.activeSinBins
import com.aidenstudio.whistlepro.core.barredPlayers
import com.aidenstudio.whistlepro.core.resolvedEvents

/** Swift payload.number(in: .primary): 교체는 나가는 번호 */
internal val EventPayload.primaryNumber: Long?
    get() = when (this) {
        is EventPayload.Goal -> number
        is EventPayload.Card -> number
        is EventPayload.Substitution -> numberOut
        is EventPayload.PenaltyKick -> number
    }

/** 퇴장당한 선수의 등번호 (명단에서 흐리게). 고치는 중인 이벤트 자신은 빼고 센다. */
fun MatchState.sentOffNumbers(team: Team, excludingEventID: String? = null): Set<Long> =
    resolvedEvents
        .filter { it.isSendingOff && it.event.payload.team == team && it.event.id != excludingEventID }
        .mapNotNull { it.event.payload.primaryNumber }
        .toSet()

/**
 * 경고 1장을 받고 아직 뛰는 선수의 등번호 (명단 칩의 작은 노란 카드, 2026-11-04 critique, Swift cautionedNumbers).
 * 두 번째 경고·레드로 퇴장했으면 빠진다. 고치는 중인 이벤트 자신은 빼고 센다.
 */
fun MatchState.cautionedNumbers(team: Team, excludingEventID: String? = null): Set<Long> =
    resolvedEvents
        .filter { it.kind == ResolvedEventKind.YELLOW && it.event.payload.team == team && it.event.id != excludingEventID }
        .mapNotNull { it.event.payload.primaryNumber }
        .toSet() - sentOffNumbers(team, excludingEventID)

/** 지금 일시 퇴장 중인 선수의 등번호 (번호 있는 것만). */
fun MatchState.sinBinnedNumbers(team: Team, now: Double): Set<Long> =
    activeSinBins(now).filter { it.team == team }.mapNotNull { it.number }.toSet()

/** 출전 금지(두 번째 일시 퇴장을 마친) 선수의 등번호. filled가 null이면 전부, true/false면 교체로 채운 쪽/아직 못 채운 쪽만. */
fun MatchState.barredNumbers(team: Team, now: Double, filled: Boolean? = null): Set<Long> =
    barredPlayers(now).filter { it.team == team && (filled == null || it.filled == filled) }.map { it.number }.toSet()

/** 지금 뛰는 선수의 등번호. 선발 표시가 없으면 null. 선발에서 시작해 교체를 따라가고 퇴장·출전 금지는 뺀다 (일시 퇴장 중은 그대로). */
fun MatchState.onPitchNumbers(team: Team, setup: MatchSetup, now: Double, excludingEventID: String? = null): Set<Long>? {
    if (!setup.hasStarters(team)) return null
    val pitch = setup.starters(team).toMutableSet()
    for (resolved in resolvedEvents) {
        val p = resolved.event.payload
        if (resolved.kind != ResolvedEventKind.SUBSTITUTION || p !is EventPayload.Substitution) continue
        if (p.team != team || resolved.event.id == excludingEventID) continue
        p.numberOut?.let { pitch.remove(it) }
        p.numberIn?.let { pitch.add(it) }
    }
    return pitch - sentOffNumbers(team, excludingEventID) - barredNumbers(team, now)
}
