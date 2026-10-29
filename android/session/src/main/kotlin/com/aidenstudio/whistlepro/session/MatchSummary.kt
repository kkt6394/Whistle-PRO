package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.FinishReason
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.resolvedEvents
import com.aidenstudio.whistlepro.core.array
import com.aidenstudio.whistlepro.core.double
import com.aidenstudio.whistlepro.core.long
import com.aidenstudio.whistlepro.core.obj
import com.aidenstudio.whistlepro.core.optionalDouble
import com.aidenstudio.whistlepro.core.optionalLong
import com.aidenstudio.whistlepro.core.optionalString
import com.aidenstudio.whistlepro.core.present
import com.aidenstudio.whistlepro.core.score
import com.aidenstudio.whistlepro.core.shootout
import com.aidenstudio.whistlepro.core.string
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/** 경기 결과. Swift 자동 Codable 형식: {"completed":{}}, {"abandoned":{"_0":"사유"}} */
sealed interface ReportOutcome {
    data object InProgress : ReportOutcome
    data object Completed : ReportOutcome
    data class Abandoned(val reason: String) : ReportOutcome

    fun toJson(): JsonObject = when (this) {
        InProgress -> buildJsonObject { put("inProgress", JsonObject(emptyMap())) }
        Completed -> buildJsonObject { put("completed", JsonObject(emptyMap())) }
        is Abandoned -> buildJsonObject { put("abandoned", buildJsonObject { put("_0", JsonPrimitive(reason)) }) }
    }

    companion object {
        fun fromJson(json: JsonObject): ReportOutcome = when {
            json.present("inProgress") != null -> InProgress
            json.present("completed") != null -> Completed
            json.present("abandoned") != null -> Abandoned(json.obj("abandoned").string("_0"))
            else -> throw SerializationException("outcome을 알 수 없음")
        }
    }
}

/** 목록·시즌 통계용 가벼운 요약. 경기마다 <id>.summary.json으로 저장한다. (Swift MatchSummary와 같은 JSON 키) */
data class MatchSummary(
    val id: UUID,
    val createdAt: Double,
    /** 첫 킥오프 시각 (킥오프 전이면 기록 생성 시각) */
    val startedAt: Double,
    val homeName: String,
    val awayName: String,
    val homeColor: TeamColor,
    val awayColor: TeamColor,
    /** 팀 보조 색 (두 색 유니폼, 없으면 null) */
    val homeColor2: TeamColor? = null,
    val awayColor2: TeamColor? = null,
    val homeScore: Int,
    val awayScore: Int,
    /** 승부차기 [홈, 원정] (승부차기가 없었으면 null) */
    val shootout: List<Int>?,
    val outcome: ReportOutcome,
    // 아래는 버전 2부터 (이전 요약에는 없다)
    val summaryVersion: Int? = null,
    /** 골 + 자책골 */
    val goals: Int? = null,
    val yellows: Int? = null,
    /** 레드 + 경고 누적 퇴장 */
    val reds: Int? = null,
    val sinBins: Int? = null,
    /** 기간마다 규정 시간을 넘겨 진행한 시간의 합 (초) */
    val addedSeconds: Int? = null,
    /** 운동 데이터 (없으면 null) */
    val distance: Double? = null,
    val averageHeartRate: Int? = null,
    val sprints: Int? = null,
    val zones: SpeedZones? = null,
    // 아래는 버전 4부터: 목록 카드의 "45분 × 2"
    val periodCount: Int? = null,
    /** 기간 하나의 규정 길이 (분) */
    val periodMinutes: Int? = null,
) {
    /** 노트에 바꾼 팀 색이 있으면 그 네 색으로 바꾼 요약 (없으면 그대로) */
    fun applying(note: MatchNote?): MatchSummary {
        val colors = note?.colors ?: return this
        val applied = colors.applied(MatchSetup(homeColor = homeColor, awayColor = awayColor))
        return copy(homeColor = applied.homeColor, awayColor = applied.awayColor,
            homeColor2 = applied.homeColor2, awayColor2 = applied.awayColor2)
    }

    val isCurrent: Boolean get() = (summaryVersion ?: 1) >= CURRENT_VERSION

    fun toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id.swiftString))
        put("createdAt", JsonPrimitive(createdAt))
        put("startedAt", JsonPrimitive(startedAt))
        put("homeName", JsonPrimitive(homeName))
        put("awayName", JsonPrimitive(awayName))
        put("homeColor", JsonPrimitive(homeColor.hex))
        put("awayColor", JsonPrimitive(awayColor.hex))
        homeColor2?.let { put("homeColor2", JsonPrimitive(it.hex)) }
        awayColor2?.let { put("awayColor2", JsonPrimitive(it.hex)) }
        put("homeScore", JsonPrimitive(homeScore))
        put("awayScore", JsonPrimitive(awayScore))
        shootout?.let { s -> put("shootout", JsonArray(s.map { JsonPrimitive(it) })) }
        put("outcome", outcome.toJson())
        summaryVersion?.let { put("summaryVersion", JsonPrimitive(it)) }
        goals?.let { put("goals", JsonPrimitive(it)) }
        yellows?.let { put("yellows", JsonPrimitive(it)) }
        reds?.let { put("reds", JsonPrimitive(it)) }
        sinBins?.let { put("sinBins", JsonPrimitive(it)) }
        addedSeconds?.let { put("addedSeconds", JsonPrimitive(it)) }
        distance?.let { put("distance", JsonPrimitive(it)) }
        averageHeartRate?.let { put("averageHeartRate", JsonPrimitive(it)) }
        sprints?.let { put("sprints", JsonPrimitive(it)) }
        zones?.let { z ->
            put("zones", buildJsonObject {
                put("walking", JsonPrimitive(z.walking))
                put("jogging", JsonPrimitive(z.jogging))
                put("running", JsonPrimitive(z.running))
                put("sprinting", JsonPrimitive(z.sprinting))
            })
        }
        periodCount?.let { put("periodCount", JsonPrimitive(it)) }
        periodMinutes?.let { put("periodMinutes", JsonPrimitive(it)) }
    }

    companion object {
        /**
         * 통계 값이 들어간 요약 형식. 이보다 낮거나 없는 요약은 전체 기록에서 다시 만든다.
         * 3: 경고 누적 퇴장을 옐로에도 센다, 4: 목록 규칙 요약(기간 수·기간 분)
         */
        const val CURRENT_VERSION = 4

        /**
         * 경기 기록만으로 만드는 가벼운 요약 (통계 값 없음). state를 주면 다시 재생하지 않는다
         * (워치 지난 경기: 상세·목록이 같은 경기를 다시 재생하지 않게).
         */
        fun of(record: MatchRecord, state: MatchState = MatchEngine.reduce(record.setup.rules, record.commands)): MatchSummary {
            val setup = record.setup
            val shootoutStatus = state.shootout
            return MatchSummary(
                id = record.id,
                createdAt = record.createdAt,
                startedAt = state.periods.firstOrNull()?.startedAt ?: record.createdAt,
                homeName = setup.homeName,
                awayName = setup.awayName,
                homeColor = setup.homeColor,
                awayColor = setup.awayColor,
                homeColor2 = setup.homeColor2,
                awayColor2 = setup.awayColor2,
                homeScore = state.score.home,
                awayScore = state.score.away,
                shootout = if (shootoutStatus.kicks.home + shootoutStatus.kicks.away > 0)
                    listOf(shootoutStatus.goals.home, shootoutStatus.goals.away) else null,
                outcome = when (val phase = state.phase) {
                    is Phase.Finished -> when (val reason = phase.reason) {
                        FinishReason.Completed -> ReportOutcome.Completed
                        is FinishReason.Abandoned -> ReportOutcome.Abandoned(reason.reason)
                    }
                    else -> ReportOutcome.InProgress
                },
                periodCount = setup.rules.periodCount.toInt(),
                periodMinutes = (setup.rules.periodLength / 60).toInt(),
            )
        }

        /** 폰 보관함용: 시즌 통계 값까지 (보고서를 한 번 만든다) */
        fun of(bundle: MatchBundle): MatchSummary {
            val report = MatchReport(bundle)
            val overall = report.workoutOverall
            return of(bundle.record).copy(
                summaryVersion = CURRENT_VERSION,
                goals = report.goals.size,
                // 꺼낸 옐로: 경고 누적 퇴장도 옐로를 한 장 더 꺼낸다 (시즌 옐로 수가 실제보다 적었다)
                yellows = report.discipline.count { it.kind == ResolvedEventKind.YELLOW || it.kind == ResolvedEventKind.SECOND_YELLOW },
                reds = report.discipline.count { it.kind == ResolvedEventKind.RED || it.kind == ResolvedEventKind.SECOND_YELLOW },
                sinBins = report.discipline.count { it.kind == ResolvedEventKind.SIN_BIN },
                addedSeconds = report.periods.sumOf { it.added }.toInt(),
                distance = overall?.distance,
                averageHeartRate = overall?.averageHeartRate?.toInt(),
                sprints = overall?.sprints,
                zones = overall?.zones,
            )
        }

        fun fromJson(json: JsonObject) = MatchSummary(
            id = json.uuid("id"),
            createdAt = json.double("createdAt"),
            startedAt = json.double("startedAt"),
            homeName = json.string("homeName"),
            awayName = json.string("awayName"),
            homeColor = TeamColor.fromCode(json.string("homeColor")),
            awayColor = TeamColor.fromCode(json.string("awayColor")),
            homeColor2 = json.optionalString("homeColor2")?.let(TeamColor::fromCode),
            awayColor2 = json.optionalString("awayColor2")?.let(TeamColor::fromCode),
            homeScore = json.int("homeScore"),
            awayScore = json.int("awayScore"),
            shootout = json.present("shootout")?.let { json.array("shootout").mapIndexed { i, _ -> json.intAt("shootout", i) } },
            outcome = ReportOutcome.fromJson(json.obj("outcome")),
            summaryVersion = json.optionalInt("summaryVersion"),
            goals = json.optionalInt("goals"),
            yellows = json.optionalInt("yellows"),
            reds = json.optionalInt("reds"),
            sinBins = json.optionalInt("sinBins"),
            addedSeconds = json.optionalInt("addedSeconds"),
            distance = json.optionalDouble("distance"),
            averageHeartRate = json.optionalInt("averageHeartRate"),
            sprints = json.optionalInt("sprints"),
            zones = json.present("zones")?.let {
                val z = json.obj("zones")
                SpeedZones(z.double("walking"), z.double("jogging"), z.double("running"), z.double("sprinting"))
            },
            periodCount = json.optionalInt("periodCount"),
            periodMinutes = json.optionalInt("periodMinutes"),
        )

        private fun JsonObject.int(key: String): Int = long(key).toInt()
        private fun JsonObject.optionalInt(key: String): Int? = optionalLong(key)?.toInt()
        private fun JsonObject.intAt(key: String, index: Int): Int =
            (array(key)[index] as? JsonPrimitive)?.content?.toIntOrNull() ?: throw SerializationException("$key[$index]: 정수가 아님")
    }
}
