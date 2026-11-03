package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.core.optionalBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 제출용 보고서에 넣을 것 (끄면 그 부분을 뺀다). 키가 없으면 켜진 것으로 읽는다. (Swift ReportSections와 같은 JSON 키) */
data class ReportSections(
    val substitutions: Boolean = true,
    val totals: Boolean = true,
    val periodTimes: Boolean = true,
    val format: Boolean = true,
    val timeline: Boolean = true,
    val signatures: Boolean = true,
    val teamSignatures: Boolean = true,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("substitutions", JsonPrimitive(substitutions))
        put("totals", JsonPrimitive(totals))
        put("periodTimes", JsonPrimitive(periodTimes))
        put("format", JsonPrimitive(format))
        put("timeline", JsonPrimitive(timeline))
        put("signatures", JsonPrimitive(signatures))
        put("teamSignatures", JsonPrimitive(teamSignatures))
    }

    companion object {
        /** 전부 넣는 기본값 */
        val STANDARD = ReportSections()

        fun fromJson(json: JsonObject) = ReportSections(
            substitutions = json.optionalBoolean("substitutions") ?: true,
            totals = json.optionalBoolean("totals") ?: true,
            periodTimes = json.optionalBoolean("periodTimes") ?: true,
            format = json.optionalBoolean("format") ?: true,
            timeline = json.optionalBoolean("timeline") ?: true,
            signatures = json.optionalBoolean("signatures") ?: true,
            teamSignatures = json.optionalBoolean("teamSignatures") ?: true,
        )
    }
}

/** 카드 사유 (IFAB 경기 규칙의 경고·퇴장 사유). 표시 문구는 앱이 key로 찾는다. */
enum class CardReason(val key: String) {
    CAUTION_UNSPORTING("caution.unsporting"),
    CAUTION_DISSENT("caution.dissent"),
    CAUTION_PERSISTENT("caution.persistent"),
    CAUTION_DELAY("caution.delay"),
    CAUTION_DISTANCE("caution.distance"),
    CAUTION_ENTERING("caution.entering"),
    CAUTION_LEAVING("caution.leaving"),
    SEND_OFF_SERIOUS_FOUL("sendOff.seriousFoul"),
    SEND_OFF_VIOLENT("sendOff.violent"),
    SEND_OFF_SPITTING("sendOff.spitting"),
    SEND_OFF_HANDBALL("sendOff.handball"),
    SEND_OFF_DOGSO("sendOff.dogso"),
    SEND_OFF_OFFENSIVE("sendOff.offensive"),
    SEND_OFF_SECOND_CAUTION("sendOff.secondCaution");

    companion object {
        val CAUTIONS = listOf(
            CAUTION_UNSPORTING, CAUTION_DISSENT, CAUTION_PERSISTENT, CAUTION_DELAY,
            CAUTION_DISTANCE, CAUTION_ENTERING, CAUTION_LEAVING)
        val SEND_OFFS = listOf(
            SEND_OFF_SERIOUS_FOUL, SEND_OFF_VIOLENT, SEND_OFF_SPITTING, SEND_OFF_HANDBALL,
            SEND_OFF_DOGSO, SEND_OFF_OFFENSIVE, SEND_OFF_SECOND_CAUTION)

        fun fromKey(key: String): CardReason? = entries.firstOrNull { it.key == key }
    }
}

/** 보고서 머리에서 비어 있는 항목 (앱이 "빠진 정보" 안내를 만든다) */
enum class ReportMissing { REFEREE, COMPETITION, VENUE }

/** 시간순 줄 목록의 한 줄 */
sealed interface TimelineRow {
    data class Event(val line: ReportLine) : TimelineRow
    /** 끝난 기간: 라벨과 그 기간 끝의 누적 점수 */
    data class PeriodEnd(val label: String, val score: TeamPair<Int>) : TimelineRow
    data class Shootout(val score: TeamPair<Int>) : TimelineRow
}

/** 경기 결과 */
sealed interface ReportResult {
    data class Win(val team: Team, val shootout: TeamPair<Int>? = null) : ReportResult
    data object Draw : ReportResult
    data class Unfinished(val outcome: ReportOutcome) : ReportResult
}
