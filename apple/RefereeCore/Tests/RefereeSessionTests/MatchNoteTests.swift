import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct MatchNoteTests {
    @Test func competitionRoundTripsAndDefaultsNil() throws {
        let setup = MatchSetup(competition: "2026 서울 리그")
        let data = try JSONEncoder().encode(setup)
        #expect(try JSONDecoder().decode(MatchSetup.self, from: data).competition == "2026 서울 리그")
        let json = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as? [String: Any])
        #expect(json["competition"] == nil)   // 예전 워치·Kotlin과 같은 모양
    }

    @Test func noteRoundTripsAndEmptyRemoves() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let id = UUID()
        try library.saveNote(MatchNote(competition: "리그", venue: "A구장"), id: id)
        #expect(library.note(id: id) == MatchNote(competition: "리그", venue: "A구장"))
        try library.saveNote(MatchNote(), id: id)
        #expect(library.note(id: id) == nil)
    }

    @Test func notesFileIsNotAMatch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        try library.upsert(MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil))
        try library.saveNote(MatchNote(venue: "A구장"), id: UUID())
        #expect(try library.all().count == 1)
        #expect(try library.summaries().count == 1)
    }

    @Test func deleteRemovesNote() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let bundle = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        try library.upsert(bundle)
        try library.saveNote(MatchNote(venue: "A구장"), id: bundle.id)
        try library.delete(id: bundle.id)
        #expect(library.note(id: bundle.id) == nil)
    }
}
