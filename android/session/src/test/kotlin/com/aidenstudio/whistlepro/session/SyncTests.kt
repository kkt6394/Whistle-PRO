package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EventPayload
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncTests {
    private val match = listOf(
        CommandKind.StartPeriod to 100.0,
        CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 600.0,
        CommandKind.EndPeriod to 2800.0, CommandKind.StartPeriod to 3700.0, CommandKind.EndPeriod to 6400.0,
    )

    private fun parse(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

    @Test fun bundleRoundTrips() {
        val record = makeRecord(listOf(CommandKind.StartPeriod to 0.0))
        val workout = WorkoutLog(record.id, heartRate = listOf(WorkoutSample(1.0, 130.0)))
        val bundle = MatchBundle(record, workout)
        assertEquals(record.id, bundle.id)
        assertEquals(bundle, MatchBundle.fromJson(Json.parseToJsonElement(bundle.toJson().toString()).jsonObject))
        // 운동 데이터가 없으면 workout 키도 없다 (Swift Optional 인코딩)
        assertFalse("workout" in MatchBundle(record, null).toJson())
    }

    @Test fun ackRoundTrips() {
        val id = UUID.randomUUID()
        assertEquals(id, SyncPayload.matchID(SyncPayload.ack(id)))
        assertNull(SyncPayload.matchID(mapOf("other" to 1)))
    }

    @Test fun presetsContextRoundTrips() {
        val preset = MatchPreset(UUID.randomUUID(), "조기축구", MatchSetup(rules = RuleSet(periodCount = 4, periodLength = 1500)))
        val context = SyncPayload.context(listOf(preset), Entitlement.NONE)
        assertEquals(listOf(preset), SyncPayload.presets(context))
    }

    /** 같은 내용을 다시 보내도(워치 앱 재설치) 전달되도록 보낸 시각이 들어간다 */
    @Test fun contextCarriesSendTime() {
        val first = SyncPayload.context(emptyList(), Entitlement.NONE, sentAt = 1.0)
        val second = SyncPayload.context(emptyList(), Entitlement.NONE, sentAt = 2.0)
        assertEquals(1.0, first[SyncPayload.SENT_AT_KEY])
        assertEquals(2.0, second[SyncPayload.SENT_AT_KEY])
    }

    /** 새로 설치된 워치 앱은 받은 컨텍스트가 비어 있다 → 폰에 요청해야 한다 */
    @Test fun emptyContextNeedsRequest() {
        assertTrue(SyncPayload.needsContext(emptyMap()))
        assertFalse(SyncPayload.needsContext(SyncPayload.context(emptyList(), Entitlement.NONE)))
    }

    @Test fun contextRequestIsRecognized() {
        assertTrue(SyncPayload.isContextRequest(SyncPayload.contextRequest()))
        assertFalse(SyncPayload.isContextRequest(SyncPayload.ack(UUID.randomUUID())))
        assertNull(SyncPayload.matchID(SyncPayload.contextRequest()))
    }

    /** Kotlin 추가: 지원하지 않는 경기 알림·수신 확인 질문과 답이 오간다 */
    @Test fun unsupportedAndReceiptMessagesRoundTrip() {
        val id = UUID.randomUUID()
        val unsupported = SyncPayload.unsupported(id)
        assertTrue(SyncPayload.isUnsupported(unsupported))
        assertEquals(id, SyncPayload.matchID(unsupported))
        assertFalse(SyncPayload.isUnsupported(SyncPayload.ack(id)))
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        assertEquals(ids, SyncPayload.receiptQueryIDs(SyncPayload.receiptQuery(ids)))
        assertNull(SyncPayload.receiptQueryIDs(emptyMap()))
        assertEquals(ids, SyncPayload.receivedIDs(SyncPayload.receiptReply(ids)))
        assertTrue(SyncPayload.receivedIDs(emptyMap()).isEmpty())
        val name = SyncPayload.outboxFileName(id)
        assertTrue(name.startsWith(id.swiftString + "-") && name.endsWith(".json"))
        assertTrue(name != SyncPayload.outboxFileName(id))
    }

    private fun contextSize(context: Map<String, Any?>): Int =
        context.values.sumOf { (it as? ByteArray)?.size ?: 16 }

    /** 명단이 꽉 찬 프리셋: 3글자 이름이면 열 개는 들어가고 스무 개는 넘친다 (실측 3.3KB/개) */
    @Test fun fullRosterPresetsFitContextUpToTen() {
        val roster = (1L..40L).map { Player(it, "김민준") }
        val preset = MatchPreset(UUID.randomUUID(), "조기축구", MatchSetup(homeRoster = roster, awayRoster = roster))
        assertTrue(SyncPayload.presetsFitContext(emptyList()))
        assertTrue(SyncPayload.presetsFitContext(List(10) { preset }))
        assertFalse(SyncPayload.presetsFitContext(List(20) { preset }))
        val ten = SyncPayload.context(List(10) { preset }, Entitlement.NONE)
        assertTrue(contextSize(ten) < SyncPayload.CONTEXT_LIMIT)
    }

    @Test fun contextKeepsAllPresetsThatFit() {
        val presets = (0 until 3).map { MatchPreset(UUID.randomUUID(), "P$it", MatchSetup()) }
        assertEquals(presets, SyncPayload.presetsFittingContext(presets))
        val context = SyncPayload.context(presets, Entitlement.NONE)
        assertEquals(presets, SyncPayload.presets(context))
    }

    /** 넘치면 앞에서부터 들어가는 만큼만, 구독 상태는 그대로 간다 */
    @Test fun oversizedContextKeepsPrefixAndEntitlement() {
        val roster = (1L..40L).map { Player(it, "가".repeat(20)) }
        val presets = (0 until 20).map { MatchPreset(UUID.randomUUID(), "P$it", MatchSetup(homeRoster = roster, awayRoster = roster)) }
        val fitting = SyncPayload.presetsFittingContext(presets)
        assertTrue(fitting.isNotEmpty() && fitting.size < presets.size)
        assertEquals(presets.take(fitting.size), fitting)
        val entitlement = Entitlement.NONE
        val context = SyncPayload.context(presets, entitlement, sentAt = 5.0)
        assertEquals(fitting, SyncPayload.presets(context))
        assertEquals(entitlement, SyncPayload.entitlement(context))
        assertEquals(5.0, context[SyncPayload.SENT_AT_KEY])
        assertTrue(contextSize(context) <= SyncPayload.CONTEXT_LIMIT)
    }

    @Test fun singleOversizedPresetSendsOnlyEntitlement() {
        val roster = (0L until 2000L).map { Player(it, "가".repeat(20)) }
        val huge = MatchPreset(UUID.randomUUID(), "Huge", MatchSetup(homeRoster = roster))
        assertTrue(SyncPayload.presetsFittingContext(listOf(huge)).isEmpty())
        val context = SyncPayload.context(listOf(huge, MatchPreset(UUID.randomUUID(), "Small", MatchSetup())), Entitlement.NONE)
        assertEquals(emptyList(), SyncPayload.presets(context))
        assertEquals(Entitlement.NONE, SyncPayload.entitlement(context))
    }

    @Test fun presetsFromForeignContextIsNil() {
        assertNull(SyncPayload.presets(emptyMap()))
        assertNull(SyncPayload.presets(mapOf(SyncPayload.PRESETS_KEY to "nope".toByteArray())))
    }

    @Test fun fifaPresetIsStable() {
        assertEquals(MatchSetup(), MatchPreset.FIFA_STANDARD.setup)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), MatchPreset.FIFA_STANDARD.id)
    }

    @Test fun presetStoreRoundTrips() {
        val store = PresetStore(File(tempDirectory(), "presets.json"))
        assertTrue(store.load().isEmpty())
        val presets = listOf(MatchPreset(UUID.randomUUID(), "A", MatchSetup()))
        store.save(presets)
        assertEquals(presets, store.load())
    }

    @Test fun corruptPresetFileLoadsEmpty() {
        val dir = tempDirectory().also { it.mkdirs() }
        val file = File(dir, "presets.json")
        file.writeText("{")
        assertTrue(PresetStore(file).load().isEmpty())
    }

    @Test fun libraryListsNewestFirst() {
        val library = MatchLibrary(tempDirectory())
        val old = MatchBundle(makeRecord(listOf(CommandKind.StartPeriod to 0.0), createdAt = 10.0), null)
        val new = MatchBundle(makeRecord(listOf(CommandKind.StartPeriod to 0.0), createdAt = 20.0), null)
        library.upsert(old)
        library.upsert(new)
        assertEquals(listOf(new, old), library.all())
        assertEquals(old, library.bundle(old.id))
    }

    @Test fun upsertReplacesSameMatch() {
        val library = MatchLibrary(tempDirectory())
        val record = makeRecord(listOf(CommandKind.StartPeriod to 0.0))
        library.upsert(MatchBundle(record, null))
        val updated = MatchBundle(record, WorkoutLog(record.id))
        library.upsert(updated)
        assertEquals(listOf(updated), library.all())
    }

    @Test fun libraryDeletes() {
        val library = MatchLibrary(tempDirectory())
        val bundle = MatchBundle(makeRecord(emptyList()), null)
        library.upsert(bundle)
        library.delete(bundle.id)
        assertTrue(library.all().isEmpty())
        assertNull(library.bundle(bundle.id))
    }

    @Test fun upsertWritesSummaryNewestFirst() {
        val library = MatchLibrary(tempDirectory())
        val old = makeRecord(match, createdAt = 10.0)
        val new = makeRecord(match, createdAt = 20.0)
        library.upsert(MatchBundle(old, null))
        library.upsert(MatchBundle(new, WorkoutLog(new.id)))
        assertEquals(
            listOf(MatchSummary.of(MatchBundle(new, WorkoutLog(new.id))), MatchSummary.of(MatchBundle(old, null))),
            library.summaries(),
        )
        assertEquals(listOf(new.id, old.id), library.all().map { it.id })
    }

    @Test fun summaryIsBackfilledForBundlesSavedWithoutOne() {
        val dir = tempDirectory().also { it.mkdirs() }
        val record = makeRecord(match)
        File(dir, "${record.id.swiftString}.json").writeText(MatchBundle(record, null).toJson().toString())
        val library = MatchLibrary(dir)
        assertEquals(listOf(MatchSummary.of(MatchBundle(record, null))), library.summaries())
        assertTrue(File(dir, "${record.id.swiftString}.summary.json").exists())
    }

    @Test fun rebuildsOldSummaryOnce() {
        val library = MatchLibrary(tempDirectory())
        val record = makeRecord(listOf(CommandKind.StartPeriod to 0.0, CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 60.0))
        library.upsert(MatchBundle(record, null))
        // 이전 버전 요약 흉내: 통계 키를 지운다
        val file = File(library.directory, record.id.swiftString + ".summary.json")
        val old = parse(file).filterKeys { it !in setOf("summaryVersion", "goals", "yellows", "reds", "sinBins", "addedSeconds") }
        file.writeText(JsonObject(old).toString())
        assertFalse(MatchSummary.fromJson(parse(file)).isCurrent)

        val rebuilt = library.summaries()
        assertEquals(1, rebuilt.first().goals)
        assertEquals(MatchSummary.CURRENT_VERSION, rebuilt.first().summaryVersion)
        assertTrue(MatchSummary.fromJson(parse(file)).isCurrent)
    }

    @Test fun oldSummaryIsKeptWhenBundleIsUnreadable() {
        val library = MatchLibrary(tempDirectory())
        val record = makeRecord(match)
        library.upsert(MatchBundle(record, null))
        val file = File(library.directory, record.id.swiftString + ".summary.json")
        file.writeText(JsonObject(parse(file).filterKeys { it != "summaryVersion" }).toString())
        File(library.directory, record.id.swiftString + ".json").writeText("broken")
        assertEquals(listOf(record.id), library.summaries().map { it.id })
        // 다음부터는 깨진 기록을 다시 읽지 않는다
        assertTrue(MatchSummary.fromJson(parse(file)).isCurrent)
    }

    @Test fun quickSummariesDoNotRebuild() {
        val library = MatchLibrary(tempDirectory())
        val record = makeRecord(listOf(CommandKind.StartPeriod to 0.0, CommandKind.RecordEvent(EventPayload.Goal(Team.HOME, 9, false)) to 60.0))
        library.upsert(MatchBundle(record, null))
        val file = File(library.directory, record.id.swiftString + ".summary.json")
        file.writeText(JsonObject(parse(file).filterKeys { it != "summaryVersion" && it != "goals" }).toString())

        assertNull(library.summaries(rebuild = false).first().goals)
        assertFalse(MatchSummary.fromJson(parse(file)).isCurrent)
        assertEquals(1, library.summaries().first().goals)
        // 다시 만든 요약은 다음 호출에서 그대로 쓴다 (전체 기록을 지워도 같은 값)
        assertTrue(File(library.directory, record.id.swiftString + ".json").delete())
        assertEquals(1, library.summaries().first().goals)
    }

    @Test fun deleteRemovesSummary() {
        val library = MatchLibrary(tempDirectory())
        val record = makeRecord(match)
        library.upsert(MatchBundle(record, null))
        library.delete(record.id)
        assertTrue(library.summaries().isEmpty())
    }

    /** Swift가 인코딩한 묶음(shared/fixtures/swift-bundle.json)을 upsert 없이 그대로 읽는다 */
    @Test fun readsSwiftEncodedBundle() {
        val fixture = File(System.getProperty("fixtureDir"), "swift-bundle.json")
        val dir = tempDirectory().also { it.mkdirs() }
        val id = UUID.fromString("6F9619FF-8B86-D011-B42D-00C04FC964FF")
        fixture.copyTo(File(dir, "${id.swiftString}.json"))
        val library = MatchLibrary(dir)

        val summary = library.summaries().single()
        assertEquals(id, summary.id)
        assertEquals("FC 서울", summary.homeName)
        assertEquals("수원 FC", summary.awayName)
        assertEquals(50.0, summary.createdAt)
        assertEquals(100.0, summary.startedAt)
        assertEquals(1, summary.homeScore)
        assertEquals(ReportOutcome.InProgress, summary.outcome)
        assertEquals(1, summary.goals)

        val bundle = assertNotNull(library.bundle(id))
        assertEquals(3, bundle.record.commands.size)
        assertEquals(listOf(WorkoutSample(110.0, 140.0)), bundle.workout?.heartRate)
        assertEquals(5400.0, bundle.workout?.distance?.last()?.value)
        assertEquals(bundle, MatchBundle.fromJson(parse(fixture)))
        assertEquals(parse(fixture).keys, bundle.toJson().keys)
    }

    @Test fun restoreAfterDeleteBringsMatchBack() {
        val library = MatchLibrary(tempDirectory())
        val bundle = MatchBundle(makeRecord(match), null)
        val note = MatchNote(competition = "리그", venue = "A구장")
        library.upsert(bundle)
        library.saveNote(note, bundle.id)
        library.delete(bundle.id)
        assertTrue(File(library.directory, bundle.id.swiftString + ".deleted").exists())
        assertNull(library.note(bundle.id))
        library.restore(bundle, note)
        assertEquals(note, library.note(bundle.id))
        assertTrue(library.upsert(bundle))   // 되돌린 뒤 워치 재전송이 무시되지 않는다
        assertEquals(listOf(bundle.id), library.summaries().map { it.id })
        assertEquals(listOf(bundle.id), library.received(listOf(bundle.id)))
        assertFalse(File(library.directory, bundle.id.swiftString + ".deleted").exists())
        assertEquals(bundle, library.bundle(bundle.id))
    }

    /** 예전 형식(deleted.json)으로 지운 경기도 되돌리면 목록에서 빠지고, 다른 항목은 그대로 둔다 */
    @Test fun restoreRemovesLegacyDeletedEntry() {
        val dir = tempDirectory().also { it.mkdirs() }
        val bundle = MatchBundle(makeRecord(match), null)
        val other = UUID.randomUUID()
        File(dir, "deleted.json").writeText("[\"${bundle.id.swiftString}\",\"${other.toString().uppercase()}\"]")
        val library = MatchLibrary(dir)
        assertFalse(library.upsert(bundle))
        library.restore(bundle)
        assertEquals(listOf(bundle.id), library.all().map { it.id })
        assertEquals(listOf(other), library.received(listOf(other)))
        assertFalse(File(library.directory, "deleted.json").readText().contains(bundle.id.swiftString))
    }

    /** 아이폰 규칙: 사용자가 지운 경기는 다시 와도 저장하지 않는다 */
    @Test fun deletedMatchIsNotResavedByUpsert() {
        val library = MatchLibrary(tempDirectory())
        val bundle = MatchBundle(makeRecord(match), null)
        assertTrue(library.upsert(bundle))
        library.delete(bundle.id)
        assertFalse(library.upsert(bundle))
        assertTrue(library.all().isEmpty())
        assertEquals(listOf(bundle.id), library.received(listOf(bundle.id)))   // 지운 경기도 받은 것으로 답한다
    }

    /** 지우기가 실패하면(파일 대신 폴더 등) 예외를 던진다 — 지움 표시만 남고 기록이 보이는 상태를 조용히 만들지 않는다 */
    @Test fun deleteFailureIsNotSwallowed() {
        val library = MatchLibrary(tempDirectory())
        val id = UUID.randomUUID()
        val blocker = File(library.directory, id.swiftString + ".json")
        File(blocker, "child").also { it.parentFile.mkdirs() }.writeText("x")
        assertFailsWith<Exception> { library.delete(id) }
    }

    /** Swift가 쓴 보관함 파일 세트(shared/fixtures/swift-library): 노트, 명단 키 없는 옛 기록, summaryVersion 3 요약 */
    @Test fun readsSwiftLibraryFiles() {
        val source = File(System.getProperty("fixtureDir"), "swift-library")
        val dir = tempDirectory()
        source.copyRecursively(dir)
        val id = UUID.fromString("7A1B2C3D-0000-4000-8000-0000000000AA")
        val library = MatchLibrary(dir)
        assertEquals(MatchNote("2026 서울 리그", "A구장"), library.note(id))
        assertFalse(MatchSummary.fromJson(parse(File(dir, "${id.swiftString}.summary.json"))).isCurrent)

        val quick = library.summaries(rebuild = false).single()
        assertEquals(3, quick.summaryVersion)
        assertNull(quick.periodCount)
        val rebuilt = library.summaries().single()
        assertEquals(MatchSummary.CURRENT_VERSION, rebuilt.summaryVersion)
        assertEquals(1, rebuilt.goals)
        assertEquals(2, rebuilt.periodCount)
        assertTrue(MatchSummary.fromJson(parse(File(dir, "${id.swiftString}.summary.json"))).isCurrent)   // 한 번만 다시 만든다
        val bundle = assertNotNull(library.bundle(id))
        assertTrue(bundle.record.setup.homeRoster.isEmpty() && bundle.record.setup.awayRoster.isEmpty())
        assertEquals(1, library.all().size)   // notes.json은 경기가 아니다
    }
}
