package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.optionalString
import com.aidenstudio.whistlepro.core.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 한 경기의 두 팀 색 넷 (주색·보조 색). 경기 설정·노트 위에 덮어쓰는 값이다. (Swift TeamColors와 같은 JSON 키)
 * JSON: {"home":"#RRGGBB","away":"#RRGGBB","home2":"#RRGGBB","away2":"#RRGGBB"}, 없는 보조 색은 키를 생략한다.
 */
data class TeamColors(
    val home: TeamColor,
    val away: TeamColor,
    val home2: TeamColor? = null,
    val away2: TeamColor? = null,
) {
    /** 설정에 네 색을 넣는다. 보조 색이 주색과 같으면 정리한다 */
    fun applied(setup: MatchSetup): MatchSetup =
        setup.copy(homeColor = home, awayColor = away, homeColor2 = home2, awayColor2 = away2).normalizedColors()

    fun setting(team: Team, primary: TeamColor): TeamColors =
        if (team == Team.HOME) copy(home = primary) else copy(away = primary)

    @JvmName("settingSecond")
    fun setting(team: Team, second: TeamColor?): TeamColors =
        if (team == Team.HOME) copy(home2 = second) else copy(away2 = second)

    fun toJson(): JsonObject = buildJsonObject {
        put("home", JsonPrimitive(home.hex))
        put("away", JsonPrimitive(away.hex))
        home2?.let { put("home2", JsonPrimitive(it.hex)) }
        away2?.let { put("away2", JsonPrimitive(it.hex)) }
    }

    companion object {
        fun from(setup: MatchSetup) = TeamColors(setup.homeColor, setup.awayColor, setup.homeColor2, setup.awayColor2)

        fun fromJson(json: JsonObject) = TeamColors(
            home = TeamColor.fromCode(json.string("home")),
            away = TeamColor.fromCode(json.string("away")),
            home2 = json.optionalString("home2")?.let(TeamColor::fromCode),
            away2 = json.optionalString("away2")?.let(TeamColor::fromCode),
        )
    }
}
