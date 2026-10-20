package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.long
import com.aidenstudio.whistlepro.core.optionalBoolean
import com.aidenstudio.whistlepro.core.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.text.BreakIterator

/** 명단의 선수 한 명. starter는 true일 때만 JSON에 쓴다 (false는 읽을 때 null) */
data class Player(val number: Long, val name: String, val starter: Boolean? = null) {
    fun toJson(): JsonObject = buildJsonObject {
        put("number", JsonPrimitive(number))
        put("name", JsonPrimitive(name))
        if (starter == true) put("starter", JsonPrimitive(true))
    }

    companion object {
        fun fromJson(json: JsonObject) = Player(json.long("number"), json.string("name"), json.optionalBoolean("starter")?.takeIf { it })
    }
}

sealed interface RosterIssue {
    /** 0~99 밖의 번호 */
    data class NumberOutOfRange(val number: Long) : RosterIssue
    data class DuplicateNumber(val number: Long) : RosterIssue
    /** 이름이 비었거나 공백뿐 (번호로 구분) */
    data class NameTooLong(val number: Long) : RosterIssue
    data object TooManyPlayers : RosterIssue
}

object Roster {
    /** 워치 숫자패드가 두 자리까지라 같은 범위 */
    val NUMBER_RANGE = 0L..99L
    const val MAXIMUM_NAME_LENGTH = 20
    const val MAXIMUM_PLAYERS = 40

    data class ParseResult(
        val players: List<Player>,
        /** 번호나 이름을 읽지 못해 건너뛴 줄 수 (빈 줄은 세지 않는다) */
        val skippedLines: Int,
    )

    /** Swift Character.isNewline과 같은 줄바꿈 (\r\n은 한 번) */
    private val newline = Regex("\r\n|[\n\r\u000B\u000C\u0085\u2028\u2029]")

    /** 한 줄에 한 명: "10 홍길동", "#7, Kim", "9\tLee". 같은 번호가 다시 나오면 뒤의 것을 쓴다. 번호순으로 돌려준다. */
    fun parse(text: String): ParseResult {
        val byNumber = mutableMapOf<Long, String>()
        var skipped = 0
        for (rawLine in text.split(newline)) {
            val line = rawLine.trim(::isSwiftWhitespace)
            if (line.isEmpty()) continue
            val rest = line.removePrefix("#")
            val digits = rest.takeWhile { it in '0'..'9' }
            val name = rest.drop(digits.length)
                .dropWhile { it.isWhitespace() || it == ',' || it == '.' || it == '-' || it == ':' }
                .trim(::isSwiftWhitespace)
            if (digits.isEmpty() || digits.length > 2 || name.isEmpty()) {
                skipped += 1
                continue
            }
            byNumber[digits.toLong()] = graphemePrefix(name, MAXIMUM_NAME_LENGTH)
        }
        val players = byNumber.map { Player(it.key, it.value) }.sortedBy { it.number }
        return ParseResult(players, skipped)
    }

    data class PasteCounts(val added: Int, val renamed: Int)

    /** 붙여넣기 결과 안내용: 새 번호 수와 이름이 바뀐 번호 수 (같은 이름은 세지 않는다) */
    fun pasteCounts(existing: List<Player>, pasted: List<Player>): PasteCounts {
        val names = mutableMapOf<Long, String>()
        for (player in existing) names.putIfAbsent(player.number, player.name)
        var added = 0
        var renamed = 0
        for (player in pasted) {
            val name = names[player.number]
            if (name == null) added += 1 else if (name != player.name) renamed += 1
        }
        return PasteCounts(added, renamed)
    }

    /** 저장 전에 확인할 문제 (없으면 빈 목록) */
    fun validate(players: List<Player>): List<RosterIssue> {
        val issues = mutableListOf<RosterIssue>()
        if (players.size > MAXIMUM_PLAYERS) issues += RosterIssue.TooManyPlayers
        val seen = mutableSetOf<Long>()
        for (player in players) {
            if (player.number !in NUMBER_RANGE) issues += RosterIssue.NumberOutOfRange(player.number)
            if (!seen.add(player.number) && RosterIssue.DuplicateNumber(player.number) !in issues) {
                issues += RosterIssue.DuplicateNumber(player.number)
            }
            // 이름은 비어도 된다 (Swift와 같음)
            val name = player.name.trim()
            if (graphemeCount(name) > MAXIMUM_NAME_LENGTH) issues += RosterIssue.NameTooLong(player.number)
        }
        return issues
    }

    /** 이름 앞뒤 공백을 지우고 번호순으로 */
    fun normalized(players: List<Player>): List<Player> =
        players.map { Player(it.number, it.name.trim(), it.starter) }.sortedBy { it.number }

    /** Swift CharacterSet.whitespaces (탭 + 공백 문자 Zs) */
    private fun isSwiftWhitespace(c: Char) = c == '\t' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    /** Swift String.count처럼 글자(grapheme) 단위로 센다 */
    private fun graphemeCount(s: String): Int {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(s) }
        var count = 0
        while (breaks.next() != BreakIterator.DONE) count += 1
        return count
    }

    private fun graphemePrefix(s: String, length: Int): String {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(s) }
        var end = 0
        repeat(length) {
            val next = breaks.next()
            if (next == BreakIterator.DONE) return s
            end = next
        }
        return s.substring(0, end)
    }
}
