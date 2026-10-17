package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.resolvedEvents

/** Swift payload.number(in: .primary): 교체는 나가는 번호 */
private val EventPayload.primaryNumber: Long?
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

/** 지금 뛰는 선수의 등번호. 선발 표시가 없으면 null. 선발에서 시작해 교체를 따라가고 퇴장은 뺀다 (일시 퇴장은 그대로). */
fun MatchState.onPitchNumbers(team: Team, setup: MatchSetup, excludingEventID: String? = null): Set<Long>? {
    if (!setup.hasStarters(team)) return null
    val pitch = setup.starters(team).toMutableSet()
    for (resolved in resolvedEvents) {
        val p = resolved.event.payload
        if (resolved.kind != ResolvedEventKind.SUBSTITUTION || p !is EventPayload.Substitution) continue
        if (p.team != team || resolved.event.id == excludingEventID) continue
        p.numberOut?.let { pitch.remove(it) }
        p.numberIn?.let { pitch.add(it) }
    }
    return pitch - sentOffNumbers(team, excludingEventID)
}
