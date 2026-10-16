package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 특정 시점의 경기 모습. 공용 시나리오 비교와 UI 표시에 쓴다.
 * 키 정의: shared/engine-scenarios/README.md. JSON에서 null인 선택 키는 생략한다.
 */
data class MatchSnapshot(
    val phase: String,
    val period: String?,
    val finishReason: String?,
    val abandonReason: String?,
    val score: TeamPair<Int>,
    val shootoutScore: TeamPair<Int>,
    val nextShootoutTeam: Team?,
    val elapsed: Long?,
    val matchSecond: Long?,
    val remaining: Long?,
    val addedTime: Long?,
    val intervalRemaining: Long?,
    val stoppageTotals: List<Long>,
    val stoppageRunning: Boolean,
    val kickoffTeam: Team?,
    val playersOnField: TeamPair<Long>,
    val substitutions: TeamPair<Int>,
    val events: List<Event>,
    val sinBins: List<SinBin>,
    val warnings: List<String>,
    val nextAlert: Alert?,
    val ignored: List<String>,
) {
    data class Event(
        val id: String,
        val type: String,
        val team: Team,
        val number: Long?,
        val numberOut: Long?,
        val numberIn: Long?,
        val matchSecond: Long,
        val warnings: List<String>,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("type", JsonPrimitive(type))
            put("team", JsonPrimitive(team.code))
            putIfPresent("number", number)
            putIfPresent("numberOut", numberOut)
            putIfPresent("numberIn", numberIn)
            put("matchSecond", JsonPrimitive(matchSecond))
            put("warnings", JsonArray(warnings.map(::JsonPrimitive)))
        }
    }

    data class SinBin(val eventId: String, val team: Team, val number: Long?, val remaining: Long) {
        fun toJson(): JsonObject = buildJsonObject {
            put("eventId", JsonPrimitive(eventId))
            put("team", JsonPrimitive(team.code))
            putIfPresent("number", number)
            put("remaining", JsonPrimitive(remaining))
        }
    }

    data class Alert(val kind: String, val at: Double) {
        fun toJson(): JsonObject = buildJsonObject {
            put("kind", JsonPrimitive(kind))
            put("at", JsonPrimitive(at))
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("phase", JsonPrimitive(phase))
        putIfPresent("period", period)
        putIfPresent("finishReason", finishReason)
        putIfPresent("abandonReason", abandonReason)
        put("score", score.toJson())
        put("shootoutScore", shootoutScore.toJson())
        putIfPresent("nextShootoutTeam", nextShootoutTeam?.code)
        putIfPresent("elapsed", elapsed)
        putIfPresent("matchSecond", matchSecond)
        putIfPresent("remaining", remaining)
        putIfPresent("addedTime", addedTime)
        putIfPresent("intervalRemaining", intervalRemaining)
        put("stoppageTotals", JsonArray(stoppageTotals.map(::JsonPrimitive)))
        put("stoppageRunning", JsonPrimitive(stoppageRunning))
        putIfPresent("kickoffTeam", kickoffTeam?.code)
        put("playersOnField", playersOnField.toJson())
        put("substitutions", substitutions.toJson())
        put("events", JsonArray(events.map { it.toJson() }))
        put("sinBins", JsonArray(sinBins.map { it.toJson() }))
        put("warnings", JsonArray(warnings.map(::JsonPrimitive)))
        nextAlert?.let { put("nextAlert", it.toJson()) }
        put("ignored", JsonArray(ignored.map(::JsonPrimitive)))
    }
}

private fun <T : Number> TeamPair<T>.toJson() = JsonArray(listOf(JsonPrimitive(home), JsonPrimitive(away)))

private fun JsonObjectBuilder.putIfPresent(key: String, value: Number?) {
    if (value != null) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putIfPresent(key: String, value: String?) {
    if (value != null) put(key, JsonPrimitive(value))
}

private val Phase.code: String
    get() = when (this) {
        Phase.NotStarted -> "notStarted"
        is Phase.Period -> "period"
        is Phase.Interval -> "interval"
        Phase.PreExtraTime -> "preExtraTime"
        Phase.Penalties -> "penalties"
        is Phase.Finished -> "finished"
    }

private fun snapshotEvent(resolved: ResolvedEvent): MatchSnapshot.Event {
    val p = resolved.event.payload
    var number: Long? = null
    var numberOut: Long? = null
    var numberIn: Long? = null
    when (p) {
        is EventPayload.Goal -> number = p.number
        is EventPayload.Card -> number = p.number
        is EventPayload.PenaltyKick -> number = p.number
        is EventPayload.Substitution -> {
            numberOut = p.numberOut
            numberIn = p.numberIn
        }
    }
    return MatchSnapshot.Event(
        id = resolved.event.id,
        type = resolved.kind.code,
        team = p.team,
        number = number,
        numberOut = numberOut,
        numberIn = numberIn,
        matchSecond = resolved.event.time.matchSecond,
        warnings = resolved.warnings.map { it.code },
    )
}

fun MatchState.snapshot(now: Double, warningBeforeEnd: Long): MatchSnapshot {
    val reading = clock(now)
    val shootoutStatus = shootout
    val reason = (phase as? Phase.Finished)?.reason
    return MatchSnapshot(
        phase = phase.code,
        period = currentPeriodLabel,
        finishReason = when (reason) {
            null -> null
            FinishReason.Completed -> "completed"
            is FinishReason.Abandoned -> "abandoned"
        },
        abandonReason = (reason as? FinishReason.Abandoned)?.reason,
        score = score,
        shootoutScore = shootoutStatus.goals,
        nextShootoutTeam = if (phase == Phase.Penalties) shootoutStatus.next else null,
        elapsed = reading?.elapsed,
        matchSecond = reading?.matchSecond,
        remaining = reading?.remaining,
        addedTime = reading?.addedTime,
        intervalRemaining = intervalRemaining(now),
        stoppageTotals = stoppageTotals(now),
        stoppageRunning = reading?.stoppageRunning ?: false,
        kickoffTeam = kickoffTeam,
        playersOnField = playersOnField(now),
        substitutions = substitutionCounts,
        events = resolvedEvents.map(::snapshotEvent),
        sinBins = activeSinBins(now).map { MatchSnapshot.SinBin(it.eventId, it.team, it.number, it.remaining) },
        warnings = matchWarnings(now).map { it.code },
        nextAlert = nextAlert(now, warningBeforeEnd)?.let { MatchSnapshot.Alert(it.kind.code, it.at) },
        ignored = ignoredCommandIds,
    )
}
