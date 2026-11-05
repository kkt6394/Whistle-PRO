package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.EventTime
import com.aidenstudio.whistlepro.core.EventWarning
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.resolvedEvents

/** 축구식 분 표기. 화면 문구로 바꾸는 일은 앱이 한다. */
sealed interface EventMinute {
    /** 23' */
    data class Regular(val minute: Long) : EventMinute
    /** 45+2' */
    data class Added(val base: Long, val extra: Long) : EventMinute
    /** 휴식·연장 대기 중 기록 (끝난 기간 라벨) */
    data class AfterPeriod(val label: String) : EventMinute
    /** 승부차기 킥 */
    data object Shootout : EventMinute
}

data class EventRow(
    val id: String,
    val minute: EventMinute,
    val kind: ResolvedEventKind,
    val team: Team,
    /** "#9", "#7 → #14", 번호가 없으면 "" */
    val detail: String,
    /** 승부차기 킥 성공 여부 (킥이 아니면 null) */
    val scored: Boolean?,
    val warnings: List<EventWarning>,
    /** 기록 시점 (기간·경과 초 계산에 쓴다) */
    val time: EventTime,
)

/** 이벤트를 기록한 직후 심판에게 보여줄 안내 */
sealed interface RecordFeedback {
    data object None : RecordFeedback
    /** 경고 누적 퇴장 확인 (등번호를 바로 고칠 수 있어야 한다) */
    data class SecondYellow(val eventId: String) : RecordFeedback
    /** 경고 문구 + 그 이벤트 (안내에 팀·번호를 함께 보인다, 2026-11-04 B1 critique) */
    data class Warning(val warning: EventWarning, val eventId: String) : RecordFeedback
}

/** 심판 화면(기록 목록·기록 직후 안내)에 보이는 경고. 일시 퇴장 겹침·경기장 밖 선수 경고는 문구가 없어 숨긴다. Swift `isShown`과 같음. */
internal val EventWarning.isShown: Boolean
    get() = when (this) {
        EventWarning.SUBSTITUTION_LIMIT_EXCEEDED, EventWarning.REPEAT_PENALTY_KICKER, EventWarning.PLAYER_ALREADY_SENT_OFF,
        EventWarning.BARRED_PLAYER_RETURNED -> true
        EventWarning.OVERLAPPING_SIN_BIN, EventWarning.PLAYER_OFF_PITCH -> false
    }

/** 이벤트 로그 화면용. 최근 것이 먼저. */
val MatchState.eventRows: List<EventRow>
    get() = rows { _, _ -> null }

/** 명단이 있으면 번호 뒤에 이름을 붙인다 ("#10 홍길동"). */
fun MatchState.eventRows(setup: MatchSetup): List<EventRow> = rows(setup::playerName)

/** 마지막 확정 이벤트 한 줄 (Swift `lastEventRow`). 없으면 null. 화면 읽기에 쓴다. */
fun MatchState.lastEventRow(setup: MatchSetup): EventRow? = eventRows(setup).firstOrNull()

private fun MatchState.rows(name: (Team, Long?) -> String?): List<EventRow> = resolvedEvents.reversed().map { r ->
    val team = r.event.payload.team
    fun label(number: Long?): String? {
        if (number == null) return null
        return name(team, number)?.let { "#$number $it" } ?: "#$number"
    }
    var scored: Boolean? = null
    val detail = when (val p = r.event.payload) {
        is EventPayload.Goal -> label(p.number) ?: ""
        is EventPayload.Card -> label(p.number) ?: ""
        is EventPayload.PenaltyKick -> {
            scored = p.scored
            label(p.number) ?: ""
        }
        is EventPayload.Substitution ->
            if (p.numberOut == null && p.numberIn == null) "" else "${label(p.numberOut) ?: "?"} → ${label(p.numberIn) ?: "?"}"
    }
    EventRow(
        id = r.event.id,
        minute = if (r.kind == ResolvedEventKind.PENALTY_KICK) EventMinute.Shootout else minute(r.event.time),
        kind = r.kind,
        team = team,
        detail = detail,
        scored = scored,
        warnings = r.warnings.filter { it.isShown },
        time = r.event.time,
    )
}

/** 0:00~0:59 → 1', 규정 시간 이후 → 45+2' (45:00~45:59가 45+1') */
fun MatchState.minute(time: EventTime): EventMinute {
    val p = periods.firstOrNull { it.ref == time.period }
    if (!time.duringPlay || p == null) return EventMinute.AfterPeriod(time.period.label(rules.periodCount))
    val end = p.offset + p.nominalLength
    if (time.matchSecond >= end) return EventMinute.Added(end / 60, (time.matchSecond - end) / 60 + 1)
    return EventMinute.Regular(time.matchSecond / 60 + 1)
}

/** Swift feedback(forEvent:)와 같은 이름. 부를 때 `feedback(forEvent = id)`로 쓴다. */
fun MatchState.feedback(forEvent: String): RecordFeedback {
    val r = resolvedEvents.firstOrNull { it.event.id == forEvent } ?: return RecordFeedback.None
    if (r.kind == ResolvedEventKind.SECOND_YELLOW && EventWarning.PLAYER_ALREADY_SENT_OFF !in r.warnings) {
        return RecordFeedback.SecondYellow(forEvent)
    }
    r.warnings.firstOrNull { it.isShown }?.let { return RecordFeedback.Warning(it, forEvent) }
    return RecordFeedback.None
}
