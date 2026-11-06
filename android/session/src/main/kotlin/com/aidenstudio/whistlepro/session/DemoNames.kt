package com.aidenstudio.whistlepro.session

/** 스토어 스크린샷용 데모 이름 두 벌 (워치·폰 공통). 실제 선수·팀과 무관한 가상 이름. */
class DemoNames(
    val homeName: String,
    val awayName: String,
    /** 16명, 1~11 선발, 12·14·15·17·20 후보 (데모 이벤트가 쓰는 1·4·5·7·9·10·14·17 포함) */
    val homeRoster: List<Player>,
    /** 16명, 1~11 선발, 13·16·18·19·21 후보 (데모 이벤트가 쓰는 4·11 포함) */
    val awayRoster: List<Player>,
    val presetName: String,
    val competition: String,
    val venue: String,
    val referee: String,
) {
    companion object {
        /** 선발 11명(번호 1~11) + 후보(번호는 benchNumbers). 실제 경기처럼 꽉 찬 명단 — 홍보 영상·스크린샷용 */
        private fun team(starters: List<String>, bench: List<String>, benchNumbers: List<Long>): List<Player> =
            (1L..11L).zip(starters).map { (n, name) -> Player(n, name, true) } +
                benchNumbers.zip(bench).map { (n, name) -> Player(n, name, null) }

        private fun home(starters: List<String>, bench: List<String>) = team(starters, bench, listOf(12L, 14L, 15L, 17L, 20L))
        private fun away(starters: List<String>, bench: List<String>) = team(starters, bench, listOf(13L, 16L, 18L, 19L, 21L))

        val ko = DemoNames(
            homeName = "FC 서울숲", awayName = "한강 유나이티드",
            homeRoster = home(
                listOf("김민준", "서지훈", "임건우", "이서준", "박도윤", "오현우", "최하준", "신유준", "정시우", "한예준", "권태윤"),
                bench = listOf("황민재", "조은우", "안준서", "윤도현", "송재원"),
            ),
            awayRoster = away(
                listOf("배성민", "류지환", "홍승현", "정우진", "문태호", "양동현", "구본혁", "남찬영", "노준혁", "하승우", "강지호"),
                bench = listOf("차민성", "장도윤", "엄태준", "전호진", "고성민"),
            ),
            presetName = "조기축구 25분×4",
            competition = "서울 아마추어 리그", venue = "서울숲 운동장", referee = "김민수",
        )

        val en = DemoNames(
            homeName = "Riverside FC", awayName = "Harbor United",
            homeRoster = home(
                listOf("Carter", "Walsh", "Mensah", "Nguyen", "Okafor", "Hughes", "Silva", "Reyes", "Park", "Müller", "Diallo"),
                bench = listOf("Brennan", "Johansson", "Kowalski", "Dubois", "Ortiz"),
            ),
            awayRoster = away(
                listOf("Fischer", "Lindqvist", "Moreau", "Rossi", "Adeyemi", "Costa", "O'Brien", "Haddad", "Novak", "Petrov", "Tanaka"),
                bench = listOf("Larsen", "Bianchi", "Murphy", "Santos", "Weber"),
            ),
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
