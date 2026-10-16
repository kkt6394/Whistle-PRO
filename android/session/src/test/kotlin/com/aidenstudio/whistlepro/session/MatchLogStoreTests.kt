package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.RuleSet
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
}
