package com.aidenstudio.whistlepro.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** 경기 규칙 설정. 기본값은 IFAB 경기규칙(FIFA 표준)이다. 시간 단위는 초. */
data class RuleSet(
    val periodCount: Int = 2,
    val periodLength: Int = 2700,
    val halfTimeLength: Int = 900,
    val quarterBreakLength: Int = 0,
    val extraTime: Boolean = false,
    val extraTimePeriodLength: Int = 900,
    val extraTimeBreakLength: Int = 60,
    val penalties: Boolean = false,
    val penaltyKicksPerTeam: Int = 5,
    val playersPerTeam: Int = 11,
    /** null이면 최소 인원 검사를 하지 않는다. */
    val minimumPlayers: Int? = 7,
    /** null이면 교체 무제한. */
    val substitutionLimit: Int? = 5,
    val returnSubstitutions: Boolean = false,
    val extraTimeExtraSubs: Int = 0,
    val secondYellowSendOff: Boolean = true,
    val sinBin: Boolean = false,
    val sinBinLength: Int = 600,
    val alternateKickoff: Boolean = true,
) {
    /** JSON 키와 같은 이름. 선언 순서가 deviations 순서다 (Swift RuleSet.Field와 같게). */
    @Suppress("EnumEntryName")
    enum class Field {
        periodCount, periodLength, halfTimeLength, quarterBreakLength,
        extraTime, extraTimePeriodLength, extraTimeBreakLength,
        penalties, penaltyKicksPerTeam,
        playersPerTeam, minimumPlayers,
        substitutionLimit, returnSubstitutions, extraTimeExtraSubs,
        secondYellowSendOff, sinBin, sinBinLength, alternateKickoff,
    }

    /** 비교·표시용 문자열 값. 보고서의 "적용 규칙" 섹션에서도 쓴다. */
    fun value(field: Field): String = when (field) {
        Field.periodCount -> "$periodCount"
        Field.periodLength -> "$periodLength"
        Field.halfTimeLength -> "$halfTimeLength"
        Field.quarterBreakLength -> "$quarterBreakLength"
        Field.extraTime -> "$extraTime"
        Field.extraTimePeriodLength -> "$extraTimePeriodLength"
        Field.extraTimeBreakLength -> "$extraTimeBreakLength"
        Field.penalties -> "$penalties"
        Field.penaltyKicksPerTeam -> "$penaltyKicksPerTeam"
        Field.playersPerTeam -> "$playersPerTeam"
        Field.minimumPlayers -> minimumPlayers?.toString() ?: "off"
        Field.substitutionLimit -> substitutionLimit?.toString() ?: "unlimited"
        Field.returnSubstitutions -> "$returnSubstitutions"
        Field.extraTimeExtraSubs -> "$extraTimeExtraSubs"
        Field.secondYellowSendOff -> "$secondYellowSendOff"
        Field.sinBin -> "$sinBin"
        Field.sinBinLength -> "$sinBinLength"
        Field.alternateKickoff -> "$alternateKickoff"
    }

    /** 기준(기본: FIFA 표준)과 값이 다른 항목. Field 선언 순서를 따른다. */
    fun deviations(base: RuleSet = FIFA_STANDARD): List<Field> =
        Field.entries.filter { value(it) != base.value(it) }

    /** 스펙 §3 RuleSet 표의 설정 범위를 검사한다. */
    fun validate() {
        fun check(ok: Boolean, field: Field) {
            if (!ok) throw RuleSetException(field)
        }
        check(periodCount in listOf(2, 4), Field.periodCount)
        check(periodLength in 60..5400, Field.periodLength)
        check(halfTimeLength in 0..3600, Field.halfTimeLength)
        check(quarterBreakLength in 0..1800, Field.quarterBreakLength)
        check(extraTimePeriodLength in 60..1800, Field.extraTimePeriodLength)
        check(extraTimeBreakLength in 0..900, Field.extraTimeBreakLength)
        check(penaltyKicksPerTeam in 1..10, Field.penaltyKicksPerTeam)
        check(playersPerTeam in 3..11, Field.playersPerTeam)
        minimumPlayers?.let { check(it in 1..playersPerTeam, Field.minimumPlayers) }
        substitutionLimit?.let { check(it in 0..20, Field.substitutionLimit) }
        check(extraTimeExtraSubs in 0..3, Field.extraTimeExtraSubs)
        check(sinBinLength in 60..1200, Field.sinBinLength)
    }

    /** 선택 항목은 null도 명시해서, 다시 읽을 때 기본값으로 되돌아가지 않게 한다. */
    fun toJson(): JsonObject = buildJsonObject {
        put(Field.periodCount.name, JsonPrimitive(periodCount))
        put(Field.periodLength.name, JsonPrimitive(periodLength))
        put(Field.halfTimeLength.name, JsonPrimitive(halfTimeLength))
        put(Field.quarterBreakLength.name, JsonPrimitive(quarterBreakLength))
        put(Field.extraTime.name, JsonPrimitive(extraTime))
        put(Field.extraTimePeriodLength.name, JsonPrimitive(extraTimePeriodLength))
        put(Field.extraTimeBreakLength.name, JsonPrimitive(extraTimeBreakLength))
        put(Field.penalties.name, JsonPrimitive(penalties))
        put(Field.penaltyKicksPerTeam.name, JsonPrimitive(penaltyKicksPerTeam))
        put(Field.playersPerTeam.name, JsonPrimitive(playersPerTeam))
        put(Field.minimumPlayers.name, minimumPlayers?.let(::JsonPrimitive) ?: JsonNull)
        put(Field.substitutionLimit.name, substitutionLimit?.let(::JsonPrimitive) ?: JsonNull)
        put(Field.returnSubstitutions.name, JsonPrimitive(returnSubstitutions))
        put(Field.extraTimeExtraSubs.name, JsonPrimitive(extraTimeExtraSubs))
        put(Field.secondYellowSendOff.name, JsonPrimitive(secondYellowSendOff))
        put(Field.sinBin.name, JsonPrimitive(sinBin))
        put(Field.sinBinLength.name, JsonPrimitive(sinBinLength))
        put(Field.alternateKickoff.name, JsonPrimitive(alternateKickoff))
    }

    companion object {
        /** 내장 프리셋 "FIFA 표준" (수정·삭제 불가) */
        val FIFA_STANDARD = RuleSet()

        /** 없는 키는 FIFA 기본값을 쓴다. minimumPlayers·substitutionLimit은 명시적 null이면 끔(null). */
        fun fromJson(json: JsonObject): RuleSet {
            val d = FIFA_STANDARD
            // Swift decodeIfPresent처럼 키가 없거나 null이면 기본값
            fun present(f: Field) = json[f.name]?.takeUnless { it is JsonNull }?.jsonPrimitive
            fun int(f: Field, fallback: Int): Int = present(f)?.int ?: fallback
            fun bool(f: Field, fallback: Boolean): Boolean = present(f)?.boolean ?: fallback
            fun optionalInt(f: Field, fallback: Int?): Int? {
                val v = json[f.name] ?: return fallback
                return if (v is JsonNull) null else v.jsonPrimitive.int
            }
            return RuleSet(
                periodCount = int(Field.periodCount, d.periodCount),
                periodLength = int(Field.periodLength, d.periodLength),
                halfTimeLength = int(Field.halfTimeLength, d.halfTimeLength),
                quarterBreakLength = int(Field.quarterBreakLength, d.quarterBreakLength),
                extraTime = bool(Field.extraTime, d.extraTime),
                extraTimePeriodLength = int(Field.extraTimePeriodLength, d.extraTimePeriodLength),
                extraTimeBreakLength = int(Field.extraTimeBreakLength, d.extraTimeBreakLength),
                penalties = bool(Field.penalties, d.penalties),
                penaltyKicksPerTeam = int(Field.penaltyKicksPerTeam, d.penaltyKicksPerTeam),
                playersPerTeam = int(Field.playersPerTeam, d.playersPerTeam),
                minimumPlayers = optionalInt(Field.minimumPlayers, d.minimumPlayers),
                substitutionLimit = optionalInt(Field.substitutionLimit, d.substitutionLimit),
                returnSubstitutions = bool(Field.returnSubstitutions, d.returnSubstitutions),
                extraTimeExtraSubs = int(Field.extraTimeExtraSubs, d.extraTimeExtraSubs),
                secondYellowSendOff = bool(Field.secondYellowSendOff, d.secondYellowSendOff),
                sinBin = bool(Field.sinBin, d.sinBin),
                sinBinLength = int(Field.sinBinLength, d.sinBinLength),
                alternateKickoff = bool(Field.alternateKickoff, d.alternateKickoff),
            )
        }
    }
}

class RuleSetException(val field: RuleSet.Field) : Exception("규칙 범위 오류: ${field.name}")
