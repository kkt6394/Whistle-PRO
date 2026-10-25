package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.array
import com.aidenstudio.whistlepro.core.commandsFromJson
import com.aidenstudio.whistlepro.core.commandsToJson
import com.aidenstudio.whistlepro.core.double
import com.aidenstudio.whistlepro.core.long
import com.aidenstudio.whistlepro.core.obj
import com.aidenstudio.whistlepro.core.objects
import com.aidenstudio.whistlepro.core.optionalBoolean
import com.aidenstudio.whistlepro.core.optionalLong
import com.aidenstudio.whistlepro.core.optionalString
import com.aidenstudio.whistlepro.core.present
import com.aidenstudio.whistlepro.core.string
import com.aidenstudio.whistlepro.session.design.Palette
import com.aidenstudio.whistlepro.session.design.RGB
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
    /** 화면 색 (sRGB) */
    val rgb: RGB get() = RGB.hex(hex.removePrefix("#").toInt(16))

    /** 검은 바탕 위 작은 표시(팀 칸의 ＋, 점)에 쓰는 팀 색: 대비가 3:1에 못 미치면(검정·남색 팀) 흰색 쪽으로 섞는다. Swift `markOnDark`와 같다. */
    val markOnDark: RGB
        get() {
            val own = rgb
            val black = Palette.background.dark
            val white = RGB.hex(0xFFFFFF)
            var t = 0.0
            var mark = own
            while (RGB.contrast(mark, black) < 3 && t < 1) {
                t = minOf(1.0, t + 0.05)
                mark = RGB.mix(own, white, t)
            }
            return mark
        }

    /** 구분하기 어려운지 판정에 쓰는 CIE Lab (sRGB D65) */
    private val lab: Triple<Double, Double, Double>
        get() {
            val c = rgb
            fun linear(v: Double) = if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            val r = linear(c.red); val g = linear(c.green); val b = linear(c.blue)
            // sRGB D65 → XYZ, 기준 백색으로 나눔
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
            val fx = f(x); val fy = f(y); val fz = f(z)
            return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
        }

    /**
     * 워치에서 두 팀 점을 구분하기 어려울 만큼 비슷한지 (CIE Lab ΔE76 < 25).
     * 둘 다 어두운 색(L < 35)이면 작은 점에서 색조 차이가 묻혀 더 멀어야(ΔE < 45) 다른 색으로 보인다.
     */
    fun isHardToTell(other: TeamColor): Boolean {
        val a = lab; val b = other.lab
        val d = Math.sqrt((a.first - b.first) * (a.first - b.first) + (a.second - b.second) * (a.second - b.second) +
            (a.third - b.third) * (a.third - b.third))
        val bothDark = a.first < 35 && b.first < 35
        return d < (if (bothDark) 45 else 25)
    }

    companion object {
        val RED = TeamColor("#FF3B30"); val BLUE = TeamColor("#007AFF"); val WHITE = TeamColor("#FFFFFF")
        val BLACK = TeamColor("#000000"); val YELLOW = TeamColor("#FFCC00"); val GREEN = TeamColor("#34C759")
        val ORANGE = TeamColor("#FF9500"); val PURPLE = TeamColor("#AF52DE")
        val SKY = TeamColor("#5AC8FA"); val NAVY = TeamColor("#1C2E6B"); val MAROON = TeamColor("#7B1E3A")
        val PINK = TeamColor("#FF6FAE"); val GRAY = TeamColor("#8E8E93")

        /** 편집 화면의 빠른 견본 (앞 8개는 예전 이름 색) */
        val QUICK_PICKS = listOf(RED, BLUE, WHITE, BLACK, YELLOW, GREEN, ORANGE, PURPLE, SKY, NAVY, MAROON, PINK, GRAY)

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
    val warningBeforeEnd: Long = 300,
    /** 휴식 종료 N초 전 예고 진동 (0이면 끔) */
    val breakWarningBeforeEnd: Long = 300,
    /** GPS 경로 기록 (끄면 배터리를 아끼는 대신 히트맵·스프린트가 없다) */
    val recordsRoute: Boolean = true,
    /** 경기 중 워치 화면 켜 두기 (손목을 내리면 어두워지되 꺼지지 않는다, 배터리를 더 쓴다). 안드로이드 워치만 (2026-10-26) */
    val keepsScreenOn: Boolean = true,
    /** 선수 명단 (없으면 등번호만 입력한다) */
    val homeRoster: List<Player> = emptyList(),
    val awayRoster: List<Player> = emptyList(),
    /** 대회명 (선택, 보고서 머리에 쓴다). 없으면 null — JSON에서도 키를 생략한다 */
    val competition: String? = null,
    /** 팀 보조 색 (두 색 유니폼, 선택). 없으면 null — JSON에서도 키를 생략한다 */
    val homeColor2: TeamColor? = null,
    val awayColor2: TeamColor? = null,
) {
    fun color2(team: Team): TeamColor? = if (team == Team.HOME) homeColor2 else awayColor2

    /** 보조 색이 주색과 같으면(hex 기준) 없는 것으로 만든다 */
    fun normalizedColors(): MatchSetup = copy(
        homeColor2 = homeColor2?.takeIf { it.hex != homeColor.hex },
        awayColor2 = awayColor2?.takeIf { it.hex != awayColor.hex },
    )

    /** 홈 {주, 보조} × 원정 {주, 보조} 모든 쌍 중 하나라도 구분하기 어려우면 true */
    val teamColorsHardToTell: Boolean
        get() {
            val home = listOfNotNull(homeColor, homeColor2)
            val away = listOfNotNull(awayColor, awayColor2)
            return home.any { h -> away.any { h.isHardToTell(it) } }
        }

    fun roster(team: Team): List<Player> = if (team == Team.HOME) homeRoster else awayRoster

    /** 선발로 표시된 선수의 등번호 */
    fun starters(team: Team): Set<Long> = roster(team).filter { it.starter == true }.map { it.number }.toSet()

    fun hasStarters(team: Team): Boolean = roster(team).any { it.starter == true }

    /** 명단에서 등번호로 찾은 이름 (번호가 없거나 명단에 없으면 null) */
    fun playerName(team: Team, number: Long?): String? {
        if (number == null) return null
        return roster(team).firstOrNull { it.number == number }?.name?.takeIf { it.isNotBlank() }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("rules", rules.toJson())
        put("homeName", JsonPrimitive(homeName))
        put("awayName", JsonPrimitive(awayName))
        put("homeColor", JsonPrimitive(homeColor.hex))
        put("awayColor", JsonPrimitive(awayColor.hex))
        put("clockDisplay", JsonPrimitive(clockDisplay.code))
        put("warningBeforeEnd", JsonPrimitive(warningBeforeEnd))
        put("breakWarningBeforeEnd", JsonPrimitive(breakWarningBeforeEnd))
        put("recordsRoute", JsonPrimitive(recordsRoute))
        // 기본값(켬)이면 키를 생략한다: Swift가 쓴 기록 JSON과 같아야 한다 (readsSwiftRecord)
        if (!keepsScreenOn) put("keepsScreenOn", JsonPrimitive(false))
        put("homeRoster", JsonArray(homeRoster.map { it.toJson() }))
        put("awayRoster", JsonArray(awayRoster.map { it.toJson() }))
        competition?.let { put("competition", JsonPrimitive(it)) }
        homeColor2?.let { put("homeColor2", JsonPrimitive(it.hex)) }
        awayColor2?.let { put("awayColor2", JsonPrimitive(it.hex)) }
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
            warningBeforeEnd = json.long("warningBeforeEnd"),
            breakWarningBeforeEnd = json.optionalLong("breakWarningBeforeEnd") ?: 300,
            recordsRoute = json.optionalBoolean("recordsRoute") ?: true,
            keepsScreenOn = json.optionalBoolean("keepsScreenOn") ?: true,
            homeRoster = json.roster("homeRoster"),
            awayRoster = json.roster("awayRoster"),
            competition = json.optionalString("competition"),
            homeColor2 = json.optionalString("homeColor2")?.let(TeamColor::fromCode),
            awayColor2 = json.optionalString("awayColor2")?.let(TeamColor::fromCode),
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

/** 8-4-4-4-12 16진수 36자 (Swift UUID(uuidString:)와 같음) */
private val uuidPattern = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")

/** java UUID.fromString은 "1-2-3-4-5"처럼 짧은 형식도 받으므로 먼저 36자 형식을 검사한다 (Swift는 거부). */
internal fun JsonObject.uuid(key: String): UUID = string(key).let {
    if (!uuidPattern.matches(it)) throw SerializationException("$key: UUID가 아님 $it")
    UUID.fromString(it)
}
