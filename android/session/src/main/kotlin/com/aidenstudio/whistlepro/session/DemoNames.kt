package com.aidenstudio.whistlepro.session

/** 스토어 스크린샷용 데모 이름 두 벌 (워치·폰 공통). 실제 선수·팀과 무관한 가상 이름. */
class DemoNames(
    val homeName: String,
    val awayName: String,
    /** 8명, 번호 1·4·5·7·9·10·14·17, 앞 6명 선발, 14·17은 후보 */
    val homeRoster: List<Player>,
    /** 2명, 번호 4·11 */
    val awayRoster: List<Player>,
    val presetName: String,
    val competition: String,
    val venue: String,
    val referee: String,
) {
    companion object {
        private fun home(names: List<String>): List<Player> {
            val numbers = listOf(1L, 4L, 5L, 7L, 9L, 10L, 14L, 17L)
            return numbers.zip(names).mapIndexed { index, (number, name) ->
                Player(number, name, if (index < 6) true else null)
            }
        }

        val ko = DemoNames(
            homeName = "FC 서울숲", awayName = "한강 유나이티드",
            homeRoster = home(listOf("김민준", "이서준", "박도윤", "최하준", "정시우", "한예준", "조은우", "윤도현")),
            awayRoster = listOf(Player(4, "정우진"), Player(11, "강지호")),
            presetName = "조기축구 25분×4",
            competition = "서울 아마추어 리그", venue = "서울숲 운동장", referee = "김민수",
        )

        val en = DemoNames(
            homeName = "Riverside FC", awayName = "Harbor United",
            homeRoster = home(listOf("Carter", "Nguyen", "Okafor", "Silva", "Park", "Müller", "Johansson", "Dubois")),
            awayRoster = listOf(Player(4, "Rossi"), Player(11, "Tanaka")),
            presetName = "Sunday League 25×4",
            competition = "Riverside Amateur League", venue = "Riverside Park", referee = "A. Kim",
        )

        /** 데모 이름 값이 "en"이면 en, "ko"면 ko, 없거나 그 밖이면 null (스토어 모드 아님). Swift `fromArguments`의 안드로이드판(인텐트 extra) */
        fun fromExtra(value: String?): DemoNames? = when (value) {
            "en" -> en
            "ko" -> ko
            else -> null
        }
    }
}
