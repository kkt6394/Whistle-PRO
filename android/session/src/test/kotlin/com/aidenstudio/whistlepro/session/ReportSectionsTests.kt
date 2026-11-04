package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 보고서 구성(블록 순서·켬/끔)과 카드 사유 저장 값 (Swift ReportSectionsTests와 같은 이름·같은 기대값) */
class ReportSectionsTests {
    private fun decode(json: String) = ReportSections.fromJson(parseJson(json.toByteArray()) as JsonObject)
    private fun JsonObject.keys(key: String) = (this[key] as JsonArray).map { (it as JsonPrimitive).content }

    @Test fun standardOrderAndTimelineOff() {
        val s = ReportSections.STANDARD
        assertEquals(
            listOf("info", "score", "goals", "substitutions", "cautions", "sendOffs", "otherReport",
                "periodTimes", "format", "totals", "timeline", "signatures"),
            s.order.map { it.key })
        assertTrue(s.hidden == setOf(ReportBlock.TIMELINE) && s.teamSignatures)
        assertTrue(!s.isOn(ReportBlock.TIMELINE) && s.isOn(ReportBlock.INFO))
        assertEquals(s.order.filter { it != ReportBlock.TIMELINE }, s.visibleBlocks)
    }

    @Test fun oldShapeDecodes() {
        // 2026-11-03 모양: 일곱 불 값을 모두 적었다. timeline은 저장된 그대로
        val old = decode("""{"substitutions":false,"totals":true,"periodTimes":true,"format":false,"timeline":true,"signatures":true,"teamSignatures":false}""")
        assertEquals(ReportBlock.DEFAULT_ORDER, old.order)
        assertEquals(setOf(ReportBlock.SUBSTITUTIONS, ReportBlock.FORMAT), old.hidden)
        assertTrue(old.isOn(ReportBlock.TIMELINE) && !old.teamSignatures)
        listOf(ReportBlock.INFO, ReportBlock.SCORE, ReportBlock.GOALS, ReportBlock.CAUTIONS, ReportBlock.SEND_OFFS, ReportBlock.OTHER_REPORT)
            .forEach { assertTrue(old.isOn(it)) }
        // 옛 모양에 timeline 키가 없으면 새 기본값(끔)
        assertEquals(ReportSections.STANDARD, decode("{}"))
        assertEquals(ReportSections.STANDARD, decode("""{"timeline":false}"""))
    }

    @Test fun roundTripsAndWritesLegacyKeys() {
        val s = ReportSections.STANDARD.moving(ReportBlock.OTHER_REPORT, -6)
            .setting(ReportBlock.TOTALS, false).setting(ReportBlock.TIMELINE, true).copy(teamSignatures = false)
        val json = s.toJson()
        assertEquals(s, ReportSections.fromJson(json))
        assertEquals(listOf("totals"), json.keys("hidden"))
        assertEquals("otherReport", json.keys("order").first())
        // 옛 앱이 읽는 불 값
        assertEquals(JsonPrimitive(false), json["totals"])
        assertEquals(JsonPrimitive(true), json["timeline"])
    }

    @Test fun unknownKeysDroppedAndMissingBlocksInserted() {
        val s = decode("""{"order":["signatures","zzz","score","goals","score"],"hidden":["goals","yyy"]}""")
        // 빠진 블록은 기본 순서에서 바로 앞 블록 뒤에, 앞 블록이 없으면 맨 앞에
        assertEquals(
            listOf("info", "signatures", "score", "goals", "substitutions", "cautions", "sendOffs", "otherReport",
                "periodTimes", "format", "totals", "timeline"),
            s.order.map { it.key })
        // 목록에 없던 timeline은 기본값(끔), 저장된 hidden은 그대로
        assertEquals(setOf(ReportBlock.GOALS, ReportBlock.TIMELINE), s.hidden)
        assertEquals(listOf(ReportBlock.INFO, ReportBlock.SIGNATURES), s.visibleBlocks.take(2))
        // 목록에 있던 timeline은 저장된 켬을 지킨다
        val on = decode("""{"order":["timeline","info"],"hidden":[]}""")
        assertTrue(on.isOn(ReportBlock.TIMELINE) && on.order.first() == ReportBlock.TIMELINE)
    }

    @Test fun moveAndToggle() {
        var s = ReportSections.STANDARD.moved(0, 2)
        assertEquals(listOf(ReportBlock.SCORE, ReportBlock.GOALS, ReportBlock.INFO), s.order.take(3))
        s = s.moved(11, 0)
        assertEquals(ReportBlock.SIGNATURES, s.order.first())
        assertEquals(s, s.moving(ReportBlock.SIGNATURES, -1))
        s = s.moving(ReportBlock.SIGNATURES, 1)
        assertEquals(listOf(ReportBlock.SCORE, ReportBlock.SIGNATURES), s.order.take(2))
        assertEquals(ReportBlock.TIMELINE, s.moving(ReportBlock.TIMELINE, 1).order.last())
        assertEquals(ReportBlock.entries.size, s.order.size)
        val off = s.setting(ReportBlock.SCORE, false)
        assertTrue(!off.isOn(ReportBlock.SCORE) && ReportBlock.SCORE !in off.visibleBlocks && s.isOn(ReportBlock.SCORE))
        val none = ReportBlock.entries.fold(ReportSections.STANDARD) { acc, b -> acc.setting(b, false) }
        assertTrue(none.visibleBlocks.isEmpty())
    }

    @Test fun cardReasonLists() {
        // IFAB 원문 순서. 퇴장 사유를 더하면 여기 목록만 늘리면 된다
        assertEquals(
            listOf("caution.delay", "caution.dissent", "caution.entering", "caution.distance",
                "caution.persistent", "caution.unsporting", "caution.reviewArea", "caution.reviewSignal"),
            CardReason.CAUTIONS.map { it.key })
        assertEquals(
            listOf("sendOff.handball", "sendOff.handballNonDeliberate", "sendOff.dogso", "sendOff.seriousFoul",
                "sendOff.spitting", "sendOff.violent", "sendOff.offensive", "sendOff.secondCaution", "sendOff.vor"),
            CardReason.SEND_OFFS.map { it.key })
        // 모든 사유가 두 목록 중 정확히 하나에 들어 있다 (사유를 더할 때 목록을 빠뜨리지 않게)
        assertEquals(CardReason.entries.toSet(), (CardReason.CAUTIONS + CardReason.SEND_OFFS).toSet())
        assertTrue(CardReason.CAUTIONS.all { it.key.startsWith("caution.") })
        assertTrue(CardReason.SEND_OFFS.all { it.key.startsWith("sendOff.") })
        CardReason.entries.forEach { assertEquals(it, CardReason.fromKey(it.key)) }
    }

    @Test fun oldLeavingReasonReadsAsEntering() {
        assertEquals(CardReason.CAUTION_ENTERING, CardReason.fromKey("caution.leaving"))
        val note = MatchNote.fromJson(parseJson(
            """{"cardReasons":{"c1":"caution.leaving","c2":"sendOff.vor","c3":"caution.zzz"}}""".toByteArray()) as JsonObject)
        assertEquals(mapOf("c1" to CardReason.CAUTION_ENTERING, "c2" to CardReason.SEND_OFF_VOR), note.cardReasons)
    }

    @Test fun noteNewFieldsRoundTripAndBlankIsNil() {
        val note = MatchNote(
            otherReport = "  킥오프 지연: 10분\n관중 소란  ", matchNumber = " 12 ", assistant1 = "박부심",
            assistant2 = "   ", fourthOfficial = "최대기")
        assertTrue(note.otherReport == "킥오프 지연: 10분\n관중 소란" && note.matchNumber == "12" && note.assistant2 == null)
        assertTrue(!note.isEmpty && !MatchNote(otherReport = "x").isEmpty && MatchNote(matchNumber = " ").isEmpty)
        val json = note.toJson()
        assertEquals(note, MatchNote.fromJson(json))
        assertFalse(json.containsKey("assistant2"))
        assertFalse(MatchNote(competition = "A").toJson().containsKey("otherReport"))
        assertNull(note.copy(otherReport = "  ").otherReport)
        assertNull(MatchNote.fromJson(parseJson("""{"matchNumber":"  "}""".toByteArray()) as JsonObject).matchNumber)
    }

    @Test fun editedKeepsOtherReportAndOfficials() {
        val old = MatchNote(otherReport = "부상", matchNumber = "7", assistant1 = "A1", assistant2 = "A2", fourthOfficial = "F")
        val recorded = TeamColors.from(MatchSetup())
        val kept = MatchNote.edited("B", null, recorded, recorded, keeping = old)
        assertTrue(kept.otherReport == "부상" && kept.matchNumber == "7" && kept.assistant2 == "A2" && kept.fourthOfficial == "F")
        val changed = MatchNote.edited(
            "B", null, recorded, recorded, keeping = old,
            matchNumber = "8", assistant1 = null, assistant2 = "새", fourthOfficial = " ")
        assertTrue(changed.otherReport == "부상" && changed.matchNumber == "8" && changed.assistant1 == null &&
            changed.assistant2 == "새" && changed.fourthOfficial == null)
    }

    @Test fun reportExposesNoteFields() {
        val record = makeRecord(MatchSetup(), listOf(CommandKind.StartPeriod to 0.0))
        val note = MatchNote(otherReport = "경기장 상태: 젖음", matchNumber = "A-3", assistant2 = "이부심", fourthOfficial = "최대기")
        val r = MatchReport(MatchBundle(record, null), note)
        assertTrue(r.otherReport == "경기장 상태: 젖음" && r.matchNumber == "A-3")
        assertTrue(r.assistants == listOf("이부심") && r.fourthOfficial == "최대기")
        val plain = MatchReport(MatchBundle(record, null))
        assertTrue(plain.otherReport == null && plain.assistants.isEmpty() && plain.matchNumber == null)
    }
}
