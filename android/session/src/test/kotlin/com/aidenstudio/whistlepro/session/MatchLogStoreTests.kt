package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchLogStoreTests {
    private fun record(createdAt: Double = 0.0) = MatchRecord(
        UUID.randomUUID(), createdAt, MatchSetup(), listOf(Command("c0", 1.0, CommandKind.StartPeriod)),
    )

    @Test fun setupDefaultsAreFifaStandard() {
        val setup = MatchSetup()
        assertEquals(RuleSet.FIFA_STANDARD, setup.rules)
        assertEquals(300, setup.warningBeforeEnd)
        assertEquals(ClockDisplay.ELAPSED, setup.clockDisplay)
    }

    /** 예고 시간·명단 번호도 Swift(64비트 Int)처럼 32비트를 넘어도 그대로 읽는다 (Int로 고정하면 다른 번호가 같은 선수가 됐다) */
    @Test fun setupKeepsValuesBeyond32Bits() {
        val json = MatchSetup(
            homeRoster = listOf(Player(3_000_000_000L, "A"), Player(3_000_000_001L, "B")),
            warningBeforeEnd = 3_000_000_000L,
        ).toJson()
        val back = MatchSetup.fromJson(Json.parseToJsonElement(json.toString()).jsonObject)
        assertEquals(3_000_000_000L, back.warningBeforeEnd)
        assertEquals("B", back.playerName(Team.HOME, 3_000_000_001L))
    }

    @Test fun noActiveMatchInEmptyDirectory() {
        assertNull(FileMatchLogStore(tempDirectory()).loadActive())
    }

    @Test fun activeRecordRoundTrips() {
        val store = FileMatchLogStore(tempDirectory())
        val r = record()
        store.saveActive(r)
        assertEquals(r, store.loadActive())
    }

    @Test fun finishActiveMovesRecordToArchive() {
        val store = FileMatchLogStore(tempDirectory())
        val older = record(10.0)
        val newer = record(20.0)
        store.saveActive(newer)
        store.finishActive(newer)
        store.finishActive(older)
        assertNull(store.loadActive())
        assertEquals(listOf(older, newer), store.archived())
    }

    @Test fun corruptActiveIsQuarantined() {
        val dir = tempDirectory()
        val store = FileMatchLogStore(dir)
        dir.mkdirs()
        File(dir, "active.json").writeText("{ not json")

        assertNull(store.loadActive())
        val names = dir.list().orEmpty().toList()
        assertFalse("active.json" in names)
        assertTrue(names.any { it.startsWith("active-corrupt-") })
    }

    /** Swift처럼 UTF-8이 깨진 파일은 대체 문자로 읽지 않고 격리한다 */
    @Test fun invalidUtf8ActiveIsQuarantined() {
        val dir = tempDirectory()
        val store = FileMatchLogStore(dir)
        store.saveActive(record().copy(setup = MatchSetup(homeName = "AB")))
        val file = File(dir, "active.json")
        val bytes = file.readBytes()
        val at = String(bytes, Charsets.UTF_8).indexOf("\"AB\"") + 2
        file.writeBytes(bytes.copyOfRange(0, at) + byteArrayOf(0xFF.toByte()) + bytes.copyOfRange(at, bytes.size))

        assertNull(store.loadActive())
        val names = dir.list().orEmpty().toList()
        assertFalse("active.json" in names)
        assertTrue(names.any { it.startsWith("active-corrupt-") })
    }

    /** Swift SyncTests.storeDeletesArchivedMatchAndWorkout와 같다 */
    @Test fun storeDeletesArchivedMatchAndWorkout() {
        val store = FileMatchLogStore(tempDirectory())
        val r = record()
        val other = record(5.0)
        store.saveActive(r)
        store.saveActiveWorkout(WorkoutLog(r.id))
        store.finishActive(r)
        store.finishActive(other)
        store.deleteArchived(r.id)
        assertEquals(listOf(other), store.archived())
        assertNull(store.archivedWorkout(r.id))
        // 없는 경기를 지워도 실패하지 않는다
        store.deleteArchived(UUID.randomUUID())
    }

    /** 쓰다가 실패해도(디스크 가득 참 등) 임시 파일이 남지 않고 원래 파일은 그대로다 */
    @Test fun failedWriteLeavesNoTempFileAndKeepsOriginal() {
        val dir = tempDirectory().also { it.mkdirs() }
        val file = File(dir, "active.json")
        file.writeText("old")
        val failure = runCatching {
            writeFileAtomically(file, "new".toByteArray()) { out, bytes ->
                out.write(bytes, 0, 1)
                throw java.io.IOException("디스크 가득 참")
            }
        }
        assertTrue(failure.isFailure)
        assertEquals(listOf("active.json"), dir.list().orEmpty().toList())
        assertEquals("old", file.readText())
    }

    @Test fun atomicWriteReplacesFile() {
        val dir = tempDirectory().also { it.mkdirs() }
        val file = File(dir, "active.json")
        file.writeText("old")
        writeFileAtomically(file, "new".toByteArray())
        assertEquals(listOf("active.json"), dir.list().orEmpty().toList())
        assertEquals("new", file.readText())
    }

    /** Swift UUID(uuidString:)처럼 8-4-4-4-12 16진수 36자만 받는다 (java UUID.fromString은 "1-2-3-4-5"도 받는다) */
    @Test fun uuidMustBeCanonical36Characters() {
        fun read(id: String) = kotlinx.serialization.json.buildJsonObject { put("id", kotlinx.serialization.json.JsonPrimitive(id)) }.uuid("id")
        val id = UUID.randomUUID()
        assertEquals(id, read(id.swiftString))
        assertEquals(id, read(id.toString()))
        for (bad in listOf("1-2-3-4-5", "", "${id.swiftString}0", id.swiftString.drop(1), id.swiftString.replace("-", ""),
            "G" + id.swiftString.drop(1), "{${id.swiftString}}", "+" + id.swiftString.drop(1))) {
            kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException>(bad) { read(bad) }
        }
    }

    // 워치 "지난 경기": 끝난 경기는 폰 전송과 별개로 최근 10경기까지 남는다
    // historySurvivesPhoneAcknowledgement는 Sync(M6-3)가 없어 옮기지 않았다

    @Test fun finishedMatchesAppearInHistoryNewestFirst() {
        val store = FileMatchLogStore(tempDirectory())
        val older = record(10.0)
        val newer = record(20.0)
        store.finishActive(older)
        store.finishActive(newer)
        assertEquals(listOf(newer, older), store.history())
    }

    @Test fun historyKeepsOnlyTheMostRecentMatches() {
        val store = FileMatchLogStore(tempDirectory())
        val records = (0 until FileMatchLogStore.HISTORY_LIMIT + 2).map { record(it.toDouble()) }
        records.forEach { store.finishActive(it) }
        val history = store.history()
        assertEquals(FileMatchLogStore.HISTORY_LIMIT, history.size)
        assertEquals(records.last(), history.first())
        assertTrue(records[0] !in history && records[1] !in history)
    }

    /** 지난 경기 리뷰: 워치 목록에서 지울 수 있다. 폰으로 보낼 보관함은 그대로 둔다 */
    @Test fun deleteHistoryRemovesOnlyThatMatch() {
        val store = FileMatchLogStore(tempDirectory())
        val keep = record(10.0)
        val gone = record(20.0)
        store.finishActive(keep)
        store.finishActive(gone)
        store.deleteHistory(gone.id)
        assertEquals(listOf(keep), store.history())
        assertEquals(listOf(keep, gone), store.archived())
        // 없는 경기를 지워도 오류가 아니다
        store.deleteHistory(UUID.randomUUID())
    }

    @Test fun emptyHistoryWithoutFinishedMatches() {
        assertEquals(emptyList(), FileMatchLogStore(tempDirectory()).history())
    }

    /** 무료 경기는 이벤트가 없고, 지난 경기 조회는 유료 기능이라 남기지 않는다 */
    @Test fun freeMatchesAreNotKeptInHistory() {
        val store = FileMatchLogStore(tempDirectory())
        val free = record().copy(unlocked = false)
        store.finishActive(free)
        assertEquals(listOf(free), store.archived())
        assertEquals(emptyList(), store.history())
    }

    /** 시작 시각이 같아도 방금 끝낸 경기는 정리되지 않는다 */
    @Test fun justFinishedMatchSurvivesPruningOnTies() {
        val store = FileMatchLogStore(tempDirectory())
        val records = (0 until FileMatchLogStore.HISTORY_LIMIT + 3).map { record(5.0) }
        for (r in records) {
            store.finishActive(r)
            assertTrue(r in store.history())
        }
        assertEquals(FileMatchLogStore.HISTORY_LIMIT, store.history().size)
    }
}
