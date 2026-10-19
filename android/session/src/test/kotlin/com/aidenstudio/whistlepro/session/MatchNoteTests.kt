package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.CommandKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchNoteTests {
    @Test fun competitionRoundTripsAndDefaultsNil() {
        val setup = MatchSetup(competition = "2026 서울 리그")
        assertEquals("2026 서울 리그", MatchSetup.fromJson(setup.toJson()).competition)
        assertNull(MatchSetup().toJson()["competition"])   // 예전 워치·Swift와 같은 모양
    }

    @Test fun noteRoundTripsAndEmptyRemoves() {
        val library = MatchLibrary(tempDirectory())
        val id = UUID.randomUUID()
        library.saveNote(MatchNote(competition = "리그", venue = "A구장"), id)
        assertEquals(MatchNote(competition = "리그", venue = "A구장"), library.note(id))
        library.saveNote(MatchNote(), id)
        assertNull(library.note(id))
    }

    @Test fun notesFileIsNotAMatch() {
        val library = MatchLibrary(tempDirectory())
        library.upsert(MatchBundle(makeRecord(listOf(CommandKind.StartPeriod to 0.0)), null))
        library.saveNote(MatchNote(venue = "A구장"), UUID.randomUUID())
        assertEquals(1, library.all().size)
        assertEquals(1, library.summaries().size)
    }

    @Test fun deleteRemovesNote() {
        val library = MatchLibrary(tempDirectory())
        val bundle = MatchBundle(makeRecord(listOf(CommandKind.StartPeriod to 0.0)), null)
        library.upsert(bundle)
        library.saveNote(MatchNote(venue = "A구장"), bundle.id)
        library.delete(bundle.id)
        assertNull(library.note(bundle.id))
    }
}
