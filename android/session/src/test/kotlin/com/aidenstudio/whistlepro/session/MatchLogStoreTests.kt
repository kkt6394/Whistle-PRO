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
}
