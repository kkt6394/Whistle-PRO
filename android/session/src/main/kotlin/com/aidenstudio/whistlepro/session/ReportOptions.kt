package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.core.optionalBoolean
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 제출용 보고서의 한 덩어리. key는 저장 키라 바꾸지 않는다 (Swift ReportBlock.rawValue와 같다) */
enum class ReportBlock(val key: String) {
    INFO("info"),
    SCORE("score"),
    GOALS("goals"),
    SUBSTITUTIONS("substitutions"),
    CAUTIONS("cautions"),
    SEND_OFFS("sendOffs"),
    OTHER_REPORT("otherReport"),
    PERIOD_TIMES("periodTimes"),
    FORMAT("format"),
    TOTALS("totals"),
    TIMELINE("timeline"),
    SIGNATURES("signatures");

    companion object {
        /** 기본 순서 (2026-11-04 설계 표) */
        val DEFAULT_ORDER: List<ReportBlock> = entries.toList()
        /** 기본으로 꺼진 블록 (시간순 기록은 어느 협회 양식에도 없고 위 표와 겹친다) */
        val DEFAULT_HIDDEN: Set<ReportBlock> = setOf(TIMELINE)

        fun fromKey(key: String): ReportBlock? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 보고서 구성: 블록 순서 + 꺼진 블록 + 팀 확인 서명 (Swift ReportSections와 같은 JSON 키).
 * `order`에는 모든 블록이 한 번씩 들어 있다 (빠진 블록은 기본 순서 자리에 끼워 넣는다).
 */
class ReportSections(
    order: List<ReportBlock> = ReportBlock.DEFAULT_ORDER,
    hidden: Set<ReportBlock> = ReportBlock.DEFAULT_HIDDEN,
    val teamSignatures: Boolean = true,
) {
    val order: List<ReportBlock> = normalized(order)
    val hidden: Set<ReportBlock> = hidden.toSet()

    fun isOn(block: ReportBlock): Boolean = block !in hidden

    /** 켜진 블록만 순서대로 */
    val visibleBlocks: List<ReportBlock> get() = order.filter(::isOn)

    fun setting(block: ReportBlock, on: Boolean): ReportSections =
        copy(hidden = if (on) hidden - block else hidden + block)

    /** `from` 자리의 블록을 `to` 자리로 (옮긴 뒤 목록 기준 자리, Compose 끌기용). 범위를 벗어나면 그대로 */
    fun moved(from: Int, to: Int): ReportSections {
        if (from !in order.indices || to !in order.indices || from == to) return this
        val list = order.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(order = list)
    }

    /** 한 칸씩 위(-1)·아래(+1)로 (접근성 "위로 옮기기"·"아래로 옮기기"). 끝을 넘으면 그대로 */
    fun moving(block: ReportBlock, by: Int): ReportSections {
        val index = order.indexOf(block)
        return if (index < 0) this else moved(index, index + by)
    }

    fun copy(
        order: List<ReportBlock> = this.order, hidden: Set<ReportBlock> = this.hidden,
        teamSignatures: Boolean = this.teamSignatures,
    ) = ReportSections(order, hidden, teamSignatures)

    override fun equals(other: Any?): Boolean =
        other is ReportSections && order == other.order && hidden == other.hidden && teamSignatures == other.teamSignatures

    override fun hashCode(): Int = (order.hashCode() * 31 + hidden.hashCode()) * 31 + teamSignatures.hashCode()

    override fun toString(): String =
        "ReportSections(order=${order.map { it.key }}, hidden=${hidden.map { it.key }}, teamSignatures=$teamSignatures)"

    /** 옛 앱(워치·폰)이 읽을 수 있게 옛 모양(2026-11-03)의 켬/끔 키도 같이 적는다 */
    fun toJson(): JsonObject = buildJsonObject {
        put("order", JsonArray(order.map { JsonPrimitive(it.key) }))
        put("hidden", JsonArray(ReportBlock.DEFAULT_ORDER.filter { it in hidden }.map { JsonPrimitive(it.key) }))
        put("teamSignatures", JsonPrimitive(teamSignatures))
        LEGACY_KEYS.forEach { put(it.key, JsonPrimitive(isOn(it))) }
    }

    companion object {
        /** 기본값 (시간순 기록만 끔) */
        val STANDARD = ReportSections()

        /** 옛 모양의 켬/끔 키가 있던 블록 */
        private val LEGACY_KEYS = listOf(
            ReportBlock.SUBSTITUTIONS, ReportBlock.TOTALS, ReportBlock.PERIOD_TIMES,
            ReportBlock.FORMAT, ReportBlock.TIMELINE, ReportBlock.SIGNATURES)

        /** 모르는 값·겹친 값을 빼고, 빠진 블록은 기본 순서에서 바로 앞 블록 뒤에 (없으면 맨 앞에) 끼워 넣는다 */
        internal fun normalized(blocks: List<ReportBlock>): List<ReportBlock> {
            val result = blocks.distinct().toMutableList()
            ReportBlock.DEFAULT_ORDER.forEachIndexed { i, block ->
                if (block in result) return@forEachIndexed
                val previous = ReportBlock.DEFAULT_ORDER.subList(0, i).lastOrNull { it in result }
                result.add(previous?.let { result.indexOf(it) + 1 } ?: 0, block)
            }
            return result
        }

        private fun JsonObject.blockKeys(key: String): List<ReportBlock> =
            ((this[key] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.let(ReportBlock::fromKey)
            }

        fun fromJson(json: JsonObject): ReportSections {
            val teamSignatures = json.optionalBoolean("teamSignatures") ?: true
            if ("order" in json || "hidden" in json) {
                // 모르는 블록 키는 버린다. 목록에 없던 (새로 생긴) 블록은 기본 켬/끔
                val listed = json.blockKeys("order")
                val hidden = json.blockKeys("hidden").toSet() +
                    ReportBlock.DEFAULT_HIDDEN.filter { it !in listed }
                return ReportSections(listed, hidden, teamSignatures)
            }
            // 옛 모양: 불 값을 그대로 옮기고 나머지 블록은 기본값, 순서는 기본 순서
            val hidden = ReportBlock.DEFAULT_HIDDEN.toMutableSet()
            LEGACY_KEYS.forEach { block ->
                when (json.optionalBoolean(block.key)) {
                    true -> hidden.remove(block)
                    false -> hidden.add(block)
                    null -> Unit
                }
            }
            return ReportSections(hidden = hidden, teamSignatures = teamSignatures)
        }
    }
}

/**
 * 카드 사유 (IFAB 경기규칙 2026/27 제12조 12.4, 대한축구협회 공식 번역). 표시 문구는 앱이 key로 찾는다 (CardReasonText).
 * 사유를 더할 때: 여기 항목과 CAUTIONS·SEND_OFFS 목록, 앱의 CardReasonText 두 곳, apple/i18n 문구만 고치면 된다
 * (단위 테스트는 entries를 돌며 확인한다).
 */
enum class CardReason(val key: String) {
    CAUTION_DELAY("caution.delay"),
    CAUTION_DISSENT("caution.dissent"),
    CAUTION_ENTERING("caution.entering"),
    CAUTION_DISTANCE("caution.distance"),
    CAUTION_PERSISTENT("caution.persistent"),
    CAUTION_UNSPORTING("caution.unsporting"),
    CAUTION_REVIEW_AREA("caution.reviewArea"),
    CAUTION_REVIEW_SIGNAL("caution.reviewSignal"),
    SEND_OFF_HANDBALL("sendOff.handball"),
    SEND_OFF_HANDBALL_NON_DELIBERATE("sendOff.handballNonDeliberate"),
    SEND_OFF_DOGSO("sendOff.dogso"),
    SEND_OFF_SERIOUS_FOUL("sendOff.seriousFoul"),
    SEND_OFF_SPITTING("sendOff.spitting"),
    SEND_OFF_VIOLENT("sendOff.violent"),
    SEND_OFF_OFFENSIVE("sendOff.offensive"),
    SEND_OFF_SECOND_CAUTION("sendOff.secondCaution"),
    SEND_OFF_VOR("sendOff.vor");

    companion object {
        /** 경고 사유 (IFAB 원문 순서 = 사유 시트 순서) */
        val CAUTIONS = listOf(
            CAUTION_DELAY, CAUTION_DISSENT, CAUTION_ENTERING, CAUTION_DISTANCE,
            CAUTION_PERSISTENT, CAUTION_UNSPORTING, CAUTION_REVIEW_AREA, CAUTION_REVIEW_SIGNAL)
        /** 퇴장 사유 (IFAB 원문 순서) */
        val SEND_OFFS = listOf(
            SEND_OFF_HANDBALL, SEND_OFF_HANDBALL_NON_DELIBERATE, SEND_OFF_DOGSO, SEND_OFF_SERIOUS_FOUL,
            SEND_OFF_SPITTING, SEND_OFF_VIOLENT, SEND_OFF_OFFENSIVE, SEND_OFF_SECOND_CAUTION, SEND_OFF_VOR)

        /** 예전에 따로 있던 값 → 지금 값 ("허락 없이 떠남"은 입장·재입장과 한 사유로 합쳤다) */
        private val LEGACY = mapOf("caution.leaving" to CAUTION_ENTERING)

        /** 저장된 값 읽기 (옛 값 포함). 모르는 값이면 null */
        fun fromKey(key: String): CardReason? = entries.firstOrNull { it.key == key } ?: LEGACY[key]
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
