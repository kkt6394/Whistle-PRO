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

        // 안드로이드 readsSwiftLibraryFiles 픽스처: SWIFT_LIBRARY_FIXTURE=<폴더>로 돌리면 보관함 파일 세트를 써 둔다
        // (노트, 명단 키가 없는 옛 형식 기록, summaryVersion 3 요약)
        if let path = ProcessInfo.processInfo.environment["SWIFT_LIBRARY_FIXTURE"] {
            let dir = URL(fileURLWithPath: path)
            try? FileManager.default.removeItem(at: dir)
            let fixed = MatchLibrary(directory: dir)
            var record = makeRecord(MatchSetup(homeName: "FC 서울", awayName: "수원 FC"),
                                    [(.startPeriod, 100), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 600), (.endPeriod, 2800)],
                                    createdAt: 70)
            record.id = UUID(uuidString: "7A1B2C3D-0000-4000-8000-0000000000AA")!
            try fixed.upsert(MatchBundle(record: record, workout: nil))
            try fixed.saveNote(MatchNote(competition: "2026 서울 리그", venue: "A구장"), id: record.id)
            func rewrite(_ name: String, _ edit: (inout [String: Any]) -> Void) throws {
                let url = dir.appendingPathComponent(name)
                var json = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
                edit(&json)
                try JSONSerialization.data(withJSONObject: json).write(to: url)
            }
            try rewrite(record.id.uuidString + ".json") { json in
                var rec = json["record"] as! [String: Any]
                var setup = rec["setup"] as! [String: Any]
                for key in ["homeRoster", "awayRoster", "competition"] { setup.removeValue(forKey: key) }
                rec["setup"] = setup
                json["record"] = rec
            }
            try rewrite(record.id.uuidString + ".summary.json") { json in
                json["summaryVersion"] = 3
                for key in ["periodCount", "periodMinutes"] { json.removeValue(forKey: key) }
            }
        }
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

    @Test func reportHeaderPrefersNoteOverPreset() {
        let setup = MatchSetup(competition: "프리셋 리그")
        let bundle = MatchBundle(record: makeRecord(setup, [(.startPeriod, 0)]), workout: nil)
        #expect(MatchReport(bundle: bundle).competition == "프리셋 리그")
        #expect(MatchReport(bundle: bundle, note: MatchNote(competition: "노트 리그", venue: "A구장"), referee: " 김기태 ").competition == "노트 리그")
        let r = MatchReport(bundle: bundle, note: MatchNote(competition: nil, venue: "A구장"), referee: "  ")
        #expect(r.competition == "프리셋 리그" && r.venue == "A구장" && r.referee == nil)
    }

    @Test func reportHeaderTrimsWhitespaceOnlyValues() {
        let bundle = MatchBundle(record: makeRecord(MatchSetup(competition: "  "), [(.startPeriod, 0)]), workout: nil)
        let r = MatchReport(bundle: bundle, note: MatchNote(competition: " ", venue: "\n"), referee: " 김기태 ")
        #expect(r.competition == nil && r.venue == nil && r.referee == "김기태")
    }
}
