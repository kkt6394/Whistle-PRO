package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.array
import com.aidenstudio.whistlepro.core.commandsFromJson
import com.aidenstudio.whistlepro.core.commandsToJson
import com.aidenstudio.whistlepro.core.double
import com.aidenstudio.whistlepro.core.int
import com.aidenstudio.whistlepro.core.obj
import com.aidenstudio.whistlepro.core.objects
import com.aidenstudio.whistlepro.core.optionalBoolean
import com.aidenstudio.whistlepro.core.present
import com.aidenstudio.whistlepro.core.string
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

enum class ClockDisplay(val code: String) {
    ELAPSED("elapsed"), REMAINING("remaining");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: throw SerializationException("알 수 없는 시계 표시: $code")
    }
}

/** 팀 색 "#RRGGBB" (Swift TeamColor와 같은 형식). 예전 이름("red" 등)도 읽는다. */
data class TeamColor(val hex: String) {
    companion object {
        val RED = TeamColor("#FF3B30"); val BLUE = TeamColor("#007AFF"); val WHITE = TeamColor("#FFFFFF")
        val BLACK = TeamColor("#000000"); val YELLOW = TeamColor("#FFCC00"); val GREEN = TeamColor("#34C759")
        val ORANGE = TeamColor("#FF9500"); val PURPLE = TeamColor("#AF52DE")
        private val legacy = mapOf("red" to RED, "blue" to BLUE, "white" to WHITE, "black" to BLACK,
            "yellow" to YELLOW, "green" to GREEN, "orange" to ORANGE, "purple" to PURPLE)
        private val hexPattern = Regex("^#?[0-9A-Fa-f]{6}$")

        fun fromCode(code: String): TeamColor {
            legacy[code]?.let { return it }
            if (!hexPattern.matches(code)) throw SerializationException("알 수 없는 팀 색: $code")
            return TeamColor("#" + code.removePrefix("#").uppercase())
        }
    }
}

/** 한 경기의 설정. 규칙(RuleSet)과 화면·팀 정보를 함께 담는다. */
data class MatchSetup(
    val rules: RuleSet = RuleSet.FIFA_STANDARD,
    val homeName: String = "HOME",
    val awayName: String = "AWAY",
    val homeColor: TeamColor = TeamColor.RED,
    val awayColor: TeamColor = TeamColor.BLUE,
    val clockDisplay: ClockDisplay = ClockDisplay.ELAPSED,
    /** 종료 N초 전 예고 진동 (0이면 끔) */
    val warningBeforeEnd: Int = 300,
    /** GPS 경로 기록 (끄면 배터리를 아끼는 대신 히트맵·스프린트가 없다) */
    val recordsRoute: Boolean = true,
    /** 선수 명단 (없으면 등번호만 입력한다) */
    val homeRoster: List<Player> = emptyList(),
    val awayRoster: List<Player> = emptyList(),
) {
    fun roster(team: Team): List<Player> = if (team == Team.HOME) homeRoster else awayRoster

    /** 명단에서 등번호로 찾은 이름 (번호가 없거나 명단에 없으면 null) */
    fun playerName(team: Team, number: Int?): String? {
        if (number == null) return null
        return roster(team).firstOrNull { it.number == number }?.name
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("rules", rules.toJson())
        put("homeName", JsonPrimitive(homeName))
        put("awayName", JsonPrimitive(awayName))
        put("homeColor", JsonPrimitive(homeColor.hex))
        put("awayColor", JsonPrimitive(awayColor.hex))
        put("clockDisplay", JsonPrimitive(clockDisplay.code))
        put("warningBeforeEnd", JsonPrimitive(warningBeforeEnd))
        put("recordsRoute", JsonPrimitive(recordsRoute))
        put("homeRoster", JsonArray(homeRoster.map { it.toJson() }))
        put("awayRoster", JsonArray(awayRoster.map { it.toJson() }))
    }

    companion object {
        /** 나중에 추가된 필드는 없으면 기본값을 쓴다 (이전 버전 기록 호환). */
        fun fromJson(json: JsonObject) = MatchSetup(
            rules = RuleSet.fromJson(json.obj("rules")),
            homeName = json.string("homeName"),
            awayName = json.string("awayName"),
            homeColor = TeamColor.fromCode(json.string("homeColor")),
            awayColor = TeamColor.fromCode(json.string("awayColor")),
            clockDisplay = ClockDisplay.fromCode(json.string("clockDisplay")),
            warningBeforeEnd = json.int("warningBeforeEnd"),
            recordsRoute = json.optionalBoolean("recordsRoute") ?: true,
            homeRoster = json.roster("homeRoster"),
            awayRoster = json.roster("awayRoster"),
        )

        private fun JsonObject.roster(key: String): List<Player> =
            if (present(key) == null) emptyList() else array(key).objects(key).map(Player::fromJson)
    }
}

/** 저장되는 경기 기록. 상태는 commands를 재생해서 다시 만든다. */
data class MatchRecord(
    val id: UUID,
    val createdAt: Double,
    val setup: MatchSetup,
    val commands: List<Command>,
    /** 킥오프 때 유료 기능이 열려 있었는지. 경기 중에는 바꾸지 않는다 (스펙 §6). */
    val unlocked: Boolean = true,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.swiftString))
        put("createdAt", JsonPrimitive(createdAt))
        put("setup", setup.toJson())
        put("commands", commandsToJson(commands))
        put("unlocked", JsonPrimitive(unlocked))
    }

    companion object {
        /** 유료 기능이 생기기 전(M2~M4)에 저장한 기록에는 unlocked가 없다 → 열린 것으로 읽는다. */
        fun fromJson(json: JsonObject) = MatchRecord(
            id = json.uuid("id"),
            createdAt = json.double("createdAt"),
            setup = MatchSetup.fromJson(json.obj("setup")),
            commands = commandsFromJson(json.array("commands")),
            unlocked = json.optionalBoolean("unlocked") ?: true,
        )
    }
}

/** Swift UUID.uuidString과 같은 대문자 표기 */
val UUID.swiftString: String get() = toString().uppercase()

internal fun JsonObject.uuid(key: String): UUID = string(key).let {
    try {
        UUID.fromString(it)
    } catch (e: IllegalArgumentException) {
        throw SerializationException("$key: UUID가 아님 $it")
    }
}
