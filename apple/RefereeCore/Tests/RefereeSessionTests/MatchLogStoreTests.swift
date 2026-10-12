import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct MatchLogStoreTests {
    private func record(createdAt: Double = 0) -> MatchRecord {
        MatchRecord(id: UUID(), createdAt: createdAt, setup: MatchSetup(),
                    commands: [Command(id: "c0", timestamp: 1, kind: .startPeriod)])
    }

    @Test func setupDefaultsAreFifaStandard() {
        let setup = MatchSetup()
        #expect(setup.rules == .fifaStandard)
        #expect(setup.warningBeforeEnd == 300)
        #expect(setup.clockDisplay == .elapsed)
    }

    @Test func noActiveMatchInEmptyDirectory() throws {
        #expect(try FileMatchLogStore(directory: tempDirectory()).loadActive() == nil)
    }

    @Test func activeRecordRoundTrips() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let r = record()
        try store.saveActive(r)
        #expect(try store.loadActive() == r)
    }

    @Test func finishActiveMovesRecordToArchive() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let older = record(createdAt: 10)
        let newer = record(createdAt: 20)
        try store.saveActive(newer)
        try store.finishActive(newer)
        try store.finishActive(older)
        #expect(try store.loadActive() == nil)
        #expect(try store.archived() == [older, newer])
    }

    @Test func corruptActiveIsQuarantined() throws {
        let dir = tempDirectory()
        let store = FileMatchLogStore(directory: dir)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("{ not json".utf8).write(to: dir.appendingPathComponent("active.json"))

        #expect(try store.loadActive() == nil)
        let names = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        #expect(!names.contains("active.json"))
        #expect(names.contains { $0.hasPrefix("active-corrupt-") })
    }
}
