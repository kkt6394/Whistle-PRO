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

struct MatchNoteColorsTests {
    private let colors = TeamColors(home: .white, away: .black, home2: nil, away2: .yellow)

    @Test func noteColorsRoundTrip() throws {
        let note = MatchNote(competition: "Cup", colors: colors)
        let data = try JSONEncoder().encode(note)
        #expect(try JSONDecoder().decode(MatchNote.self, from: data) == note)
        let json = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let inner = try #require(json["colors"] as? [String: Any])
        #expect(inner["home2"] == nil)
        #expect(inner["away2"] as? String == "#FFCC00")
    }

    @Test func oldNoteWithoutColorsDecodes() throws {
        let note = try JSONDecoder().decode(MatchNote.self, from: Data(#"{"competition":"Cup"}"#.utf8))
        #expect(note.colors == nil)
        #expect(!note.isEmpty)
        #expect(MatchNote().isEmpty)
        #expect(MatchNote(colors: nil).isEmpty)
        #expect(!MatchNote(colors: colors).isEmpty)
    }

    @Test func reportAndSummaryUseNoteColors() {
        let setup = MatchSetup(homeColor: .red, awayColor: .blue, homeColor2: .yellow)
        let bundle = MatchBundle(record: makeRecord(setup, [(.startPeriod, 0)]), workout: nil)
        let note = MatchNote(colors: colors)
        let report = MatchReport(bundle: bundle, note: note)
        #expect(report.homeColor == .white && report.awayColor == .black)
        #expect(report.homeColor2 == nil && report.awayColor2 == .yellow)
        let summary = MatchSummary(record: bundle.record).applying(note)
        #expect(summary.homeColor == .white && summary.awayColor == .black)
        #expect(summary.homeColor2 == nil && summary.awayColor2 == .yellow)
        // 노트 색이 없으면 기록 색
        let plain = MatchReport(bundle: bundle, note: MatchNote(competition: "Cup"))
        #expect(plain.homeColor == .red && plain.homeColor2 == .yellow)
        #expect(MatchSummary(record: bundle.record).applying(nil).homeColor == .red)
    }

    @Test func noteColorsClearSecondColor() {
        let setup = MatchSetup(homeColor: .red, awayColor: .blue, homeColor2: .yellow)
        let bundle = MatchBundle(record: makeRecord(setup, [(.startPeriod, 0)]), workout: nil)
        let note = MatchNote(colors: TeamColors(home: .red, away: .blue, home2: nil, away2: nil))
        #expect(MatchReport(bundle: bundle, note: note).homeColor2 == nil)
        #expect(MatchSummary(record: bundle.record).applying(note).homeColor2 == nil)
    }
}
