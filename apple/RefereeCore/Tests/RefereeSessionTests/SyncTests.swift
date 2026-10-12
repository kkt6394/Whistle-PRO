import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct SyncTests {
    @Test func bundleRoundTrips() throws {
        let record = makeRecord([(.startPeriod, 0)])
        var workout = WorkoutLog(matchID: record.id)
        workout.heartRate = [WorkoutSample(t: 1, value: 130)]
        let bundle = MatchBundle(record: record, workout: workout)
        #expect(bundle.id == record.id)
        #expect(try JSONDecoder().decode(MatchBundle.self, from: JSONEncoder().encode(bundle)) == bundle)
    }

    @Test func ackRoundTrips() {
        let id = UUID()
        #expect(SyncPayload.matchID(from: SyncPayload.ack(id)) == id)
        #expect(SyncPayload.matchID(from: ["other": 1]) == nil)
    }

    @Test func presetsContextRoundTrips() throws {
        let preset = MatchPreset(id: UUID(), name: "조기축구", setup: MatchSetup(rules: RuleSet(periodCount: 4, periodLength: 1500)))
        let context = try SyncPayload.context(presets: [preset])
        #expect(SyncPayload.presets(from: context) == [preset])
    }

    @Test func presetsFromForeignContextIsNil() {
        #expect(SyncPayload.presets(from: [:]) == nil)
        #expect(SyncPayload.presets(from: [SyncPayload.presetsKey: Data("nope".utf8)]) == nil)
    }

    @Test func fifaPresetIsStable() {
        #expect(MatchPreset.fifaStandard.setup == MatchSetup())
        #expect(MatchPreset.fifaStandard.id == MatchPreset.fifaStandard.id)
    }

    @Test func presetStoreRoundTrips() throws {
        let store = PresetStore(fileURL: tempDirectory().appendingPathComponent("presets.json"))
        #expect(store.load().isEmpty)
        let presets = [MatchPreset(id: UUID(), name: "A", setup: MatchSetup())]
        try store.save(presets)
        #expect(store.load() == presets)
    }

    @Test func corruptPresetFileLoadsEmpty() throws {
        let dir = tempDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent("presets.json")
        try Data("{".utf8).write(to: url)
        #expect(PresetStore(fileURL: url).load().isEmpty)
    }

    @Test func libraryListsNewestFirst() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let old = MatchBundle(record: makeRecord([(.startPeriod, 0)], createdAt: 10), workout: nil)
        let new = MatchBundle(record: makeRecord([(.startPeriod, 0)], createdAt: 20), workout: nil)
        try library.upsert(old)
        try library.upsert(new)
        #expect(try library.all() == [new, old])
        #expect(try library.bundle(id: old.id) == old)
    }

    @Test func upsertReplacesSameMatch() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord([(.startPeriod, 0)])
        try library.upsert(MatchBundle(record: record, workout: nil))
        let updated = MatchBundle(record: record, workout: WorkoutLog(matchID: record.id))
        try library.upsert(updated)
        #expect(try library.all() == [updated])
    }

    @Test func libraryDeletes() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let bundle = MatchBundle(record: makeRecord([]), workout: nil)
        try library.upsert(bundle)
        try library.delete(id: bundle.id)
        #expect(try library.all().isEmpty)
        #expect(try library.bundle(id: bundle.id) == nil)
    }

    @Test func storeDeletesArchivedMatchAndWorkout() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let record = makeRecord([])
        try store.saveActive(record)
        try store.saveActiveWorkout(WorkoutLog(matchID: record.id))
        try store.finishActive(record)
        try store.deleteArchived(record.id)
        #expect(try store.archived().isEmpty)
        #expect(try store.archivedWorkout(for: record.id) == nil)
    }
}
