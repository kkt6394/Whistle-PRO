package com.aidenstudio.whistlepro.core

enum class Team(val code: String) {
    HOME("home"), AWAY("away");

    val opponent: Team get() = if (this == HOME) AWAY else HOME

    companion object {
        /** "home"/"away" → Team. 모르는 값이면 null. */
        fun fromCode(code: String): Team? = entries.firstOrNull { it.code == code }
    }
}

/** 홈/원정 한 쌍의 값. JSON으로는 [home, away] 배열로 인코딩한다. */
data class TeamPair<T>(val home: T, val away: T) {
    operator fun get(team: Team): T = if (team == Team.HOME) home else away

    fun with(team: Team, value: T): TeamPair<T> = if (team == Team.HOME) copy(home = value) else copy(away = value)
}
