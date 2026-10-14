package com.aidenstudio.whistlepro.core

data class PeriodRef(val kind: Kind, /** 1부터 시작 */ val index: Int) {
    enum class Kind { REGULAR, EXTRA }

    /** 2기간: 1H/2H, 4기간: Q1~Q4, 연장: ET1/ET2 */
    fun label(periodCount: Int): String = when (kind) {
        Kind.REGULAR -> if (periodCount == 4) "Q$index" else "${index}H"
        Kind.EXTRA -> "ET$index"
    }
}

sealed interface FinishReason {
    data object Completed : FinishReason
    data class Abandoned(val reason: String) : FinishReason
}

sealed interface Phase {
    data object NotStarted : Phase
    data class Period(val ref: PeriodRef) : Phase
    /** 기간 사이 휴식 (스펙의 break) */
    data class Interval(val after: PeriodRef) : Phase
    data object PreExtraTime : Phase
    data object Penalties : Phase
    data class Finished(val reason: FinishReason) : Phase

    val isPeriod: Boolean get() = this is Period
    val isInterval: Boolean get() = this is Interval
}

data class StoppageInterval(val start: Double, val end: Double?)

data class PeriodRecord(
    val ref: PeriodRef,
    val startedAt: Double,
    val endedAt: Double?,
    /** 규정 길이(초). adjustPeriodLength로 진행 중에도 바뀔 수 있다. */
    val nominalLength: Int,
    /** 이 기간 시작 시점의 누적 경기 시각(초) = 앞선 기간들의 규정 길이 합 */
    val offset: Int,
    val stoppages: List<StoppageInterval>,
)

data class EventTime(
    /** 기록 시점에 진행 중이었거나 마지막으로 끝난 기간 */
    val period: PeriodRef,
    /** 기간 진행 중에 기록됐으면 true (휴식·연장 대기·승부차기 중이면 false) */
    val duringPlay: Boolean,
    val matchSecond: Long,
)

data class RecordedEvent(
    val id: String,
    val timestamp: Double,
    val payload: EventPayload,
    val time: EventTime,
)

enum class EngineError { INVALID_IN_PHASE, INVALID_VALUE, UNKNOWN_EVENT, SHOOTOUT_FIRST_TEAM_MISSING, NOT_YOUR_TURN }

class EngineException(val error: EngineError) : Exception(error.name)

internal fun fail(error: EngineError): Nothing = throw EngineException(error)

/** 경기 상태. 직접 만들지 말고 MatchEngine.reduce로 얻는다. */
data class MatchState(
    val rules: RuleSet,
    val phase: Phase = Phase.NotStarted,
    val periods: List<PeriodRecord> = emptyList(),
    val events: List<RecordedEvent> = emptyList(),
    val regularKickoff: Team? = null,
    val extraKickoff: Team? = null,
    val shootoutFirstTeam: Team? = null,
    /** 다음 정규 기간에 쓸 길이 (adjustPeriodLength로 변경) */
    val regularPeriodLength: Int = rules.periodLength,
    /** 다음 연장 기간에 쓸 길이 */
    val extraPeriodLength: Int = rules.extraTimePeriodLength,
    /** 적용되지 못하고 무시된 명령 ID (로그 순서) */
    val ignoredCommandIds: List<String> = emptyList(),
)

/** 마지막 기간을 바꾼 목록 */
internal fun List<PeriodRecord>.updateLast(transform: (PeriodRecord) -> PeriodRecord): List<PeriodRecord> =
    dropLast(1) + transform(last())
