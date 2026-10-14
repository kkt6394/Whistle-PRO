package com.aidenstudio.whistlepro.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

enum class CardKind(val code: String) {
    YELLOW("yellow"), RED("red"), SIN_BIN("sinBin");

    companion object {
        fun fromCode(code: String): CardKind? = entries.firstOrNull { it.code == code }
    }
}

/** 경기 이벤트 내용. 등번호는 모두 선택 입력이다. */
sealed interface EventPayload {
    /** 자책골이면 자책한 선수의 팀이다. */
    val team: Team

    data class Goal(override val team: Team, val number: Int?, val ownGoal: Boolean) : EventPayload
    data class Card(override val team: Team, val number: Int?, val kind: CardKind) : EventPayload
    data class Substitution(override val team: Team, val numberOut: Int?, val numberIn: Int?) : EventPayload
    data class PenaltyKick(override val team: Team, val number: Int?, val scored: Boolean) : EventPayload

    val typeName: String
        get() = when (this) {
            is Goal -> "goal"
            is Card -> "card"
            is Substitution -> "substitution"
            is PenaltyKick -> "penaltyKick"
        }

    /** "goal" → "recordGoal" */
    val recordCommandType: String
        get() = "record" + typeName.replaceFirstChar { it.uppercaseChar() }

    fun toJson(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(typeName))
        putFields(this@EventPayload)
    }

    companion object {
        /** "recordGoal" → "goal". record로 시작하지 않으면 null. */
        fun typeName(forRecordCommand: String): String? {
            if (!forRecordCommand.startsWith("record")) return null
            val rest = forRecordCommand.removePrefix("record")
            if (rest.isEmpty()) return null
            return rest.replaceFirstChar { it.lowercaseChar() }
        }

        fun fromJson(json: JsonObject): EventPayload = fromFields(json.string("type"), json)

        /** type 외의 필드를 읽는다. Command의 평평한 JSON에서도 쓴다. */
        internal fun fromFields(type: String, json: JsonObject): EventPayload {
            val team = json.team("team")
            val number = json.optionalInt("number")
            return when (type) {
                "goal" -> Goal(team, number, json.optionalBoolean("ownGoal") ?: false)
                "card" -> Card(team, number, json.string("kind").let {
                    CardKind.fromCode(it) ?: throw SerializationException("알 수 없는 카드: $it")
                })
                "substitution" -> Substitution(team, json.optionalInt("numberOut"), json.optionalInt("numberIn"))
                "penaltyKick" -> PenaltyKick(team, number, json.boolean("scored"))
                else -> throw SerializationException("알 수 없는 이벤트: $type")
            }
        }
    }
}

private fun JsonObjectBuilder.putFields(payload: EventPayload) {
    put("team", JsonPrimitive(payload.team.code))
    fun putIfPresent(key: String, value: Int?) { if (value != null) put(key, JsonPrimitive(value)) }
    when (payload) {
        is EventPayload.Goal -> {
            putIfPresent("number", payload.number)
            put("ownGoal", JsonPrimitive(payload.ownGoal))
        }
        is EventPayload.Card -> {
            putIfPresent("number", payload.number)
            put("kind", JsonPrimitive(payload.kind.code))
        }
        is EventPayload.Substitution -> {
            putIfPresent("numberOut", payload.numberOut)
            putIfPresent("numberIn", payload.numberIn)
        }
        is EventPayload.PenaltyKick -> {
            putIfPresent("number", payload.number)
            put("scored", JsonPrimitive(payload.scored))
        }
    }
}

sealed interface CommandKind {
    data class SetKickoffTeam(val team: Team) : CommandKind
    data object StartPeriod : CommandKind
    data object EndPeriod : CommandKind
    data class AdjustPeriodLength(val seconds: Int) : CommandKind
    data object ToggleStoppage : CommandKind
    data class SetShootoutFirstTeam(val team: Team) : CommandKind
    data class RecordEvent(val payload: EventPayload) : CommandKind
    data class EditEvent(val id: String, val payload: EventPayload) : CommandKind
    data class DeleteEvent(val id: String) : CommandKind
    data object Undo : CommandKind
    data class AbandonMatch(val reason: String) : CommandKind
}

data class Command(
    val id: String = UUID.randomUUID().toString(),
    /** Unix epoch 초 */
    val timestamp: Double,
    val kind: CommandKind,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("t", JsonPrimitive(timestamp))
        fun type(name: String) = put("type", JsonPrimitive(name))
        when (kind) {
            is CommandKind.SetKickoffTeam -> {
                type("setKickoffTeam")
                put("team", JsonPrimitive(kind.team.code))
            }
            CommandKind.StartPeriod -> type("startPeriod")
            CommandKind.EndPeriod -> type("endPeriod")
            is CommandKind.AdjustPeriodLength -> {
                type("adjustPeriodLength")
                put("seconds", JsonPrimitive(kind.seconds))
            }
            CommandKind.ToggleStoppage -> type("toggleStoppage")
            is CommandKind.SetShootoutFirstTeam -> {
                type("setShootoutFirstTeam")
                put("team", JsonPrimitive(kind.team.code))
            }
            is CommandKind.RecordEvent -> {
                type(kind.payload.recordCommandType)
                putFields(kind.payload)
            }
            is CommandKind.EditEvent -> {
                type("editEvent")
                put("eventId", JsonPrimitive(kind.id))
                put("event", kind.payload.toJson())
            }
            is CommandKind.DeleteEvent -> {
                type("deleteEvent")
                put("eventId", JsonPrimitive(kind.id))
            }
            CommandKind.Undo -> type("undo")
            is CommandKind.AbandonMatch -> {
                type("abandonMatch")
                put("reason", JsonPrimitive(kind.reason))
            }
        }
    }

    companion object {
        /** id가 없으면 UUID를 만든다 (빈 문자열 금지). */
        fun fromJson(json: JsonObject): Command {
            val id = json.optionalString("id") ?: UUID.randomUUID().toString()
            val timestamp = json.double("t")
            val kind = when (val type = json.string("type")) {
                "setKickoffTeam" -> CommandKind.SetKickoffTeam(json.team("team"))
                "startPeriod" -> CommandKind.StartPeriod
                "endPeriod" -> CommandKind.EndPeriod
                "adjustPeriodLength" -> CommandKind.AdjustPeriodLength(json.int("seconds"))
                "toggleStoppage" -> CommandKind.ToggleStoppage
                "setShootoutFirstTeam" -> CommandKind.SetShootoutFirstTeam(json.team("team"))
                "editEvent" -> CommandKind.EditEvent(json.string("eventId"), EventPayload.fromJson(json.obj("event")))
                "deleteEvent" -> CommandKind.DeleteEvent(json.string("eventId"))
                "undo" -> CommandKind.Undo
                "abandonMatch" -> CommandKind.AbandonMatch(json.optionalString("reason") ?: "")
                else -> {
                    val eventType = EventPayload.typeName(forRecordCommand = type)
                        ?: throw SerializationException("알 수 없는 명령: $type")
                    CommandKind.RecordEvent(EventPayload.fromFields(eventType, json))
                }
            }
            return Command(id, timestamp, kind)
        }
    }
}

fun commandsToJson(commands: List<Command>): JsonArray = JsonArray(commands.map { it.toJson() })

fun commandsFromJson(json: JsonArray): List<Command> = json.map {
    Command.fromJson(it as? JsonObject ?: throw SerializationException("명령이 객체가 아님"))
}
