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
        let context = try SyncPayload.context(presets: [preset], entitlement: .none)
        #expect(SyncPayload.presets(from: context) == [preset])
    }

    /// 같은 내용을 다시 보내도(워치 앱 재설치) 전달되도록 보낸 시각이 들어간다
    @Test func contextCarriesSendTime() throws {
        let first = try SyncPayload.context(presets: [], entitlement: .none, sentAt: 1)
        let second = try SyncPayload.context(presets: [], entitlement: .none, sentAt: 2)
        #expect(first[SyncPayload.sentAtKey] as? Double == 1)
        #expect(second[SyncPayload.sentAtKey] as? Double == 2)
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

/// 최종 리뷰: 목록이 모든 경기의 운동 데이터까지 읽지 않도록 가벼운 요약을 따로 둔다
struct MatchSummaryTests {
    private let match: [(CommandKind, Double)] = [
        (.startPeriod, 100), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 600),
        (.endPeriod, 2800), (.startPeriod, 3700), (.endPeriod, 6400),
    ]

    @Test func summaryDescribesMatch() {
        let summary = MatchSummary(record: makeRecord(MatchSetup(homeName: "A", awayName: "B"), match, createdAt: 50))
        #expect(summary.homeName == "A")
        #expect(summary.awayName == "B")
        #expect(summary.homeScore == 1)
        #expect(summary.awayScore == 0)
        #expect(summary.startedAt == 100)
        #expect(summary.createdAt == 50)
        #expect(summary.outcome == .completed)
        #expect(summary.shootout == nil)
    }

    @Test func abandonedSummary() {
        let summary = MatchSummary(record: makeRecord([(.startPeriod, 0), (.abandonMatch(reason: "폭우"), 10)]))
        #expect(summary.outcome == .abandoned("폭우"))
    }

    @Test func upsertWritesSummaryNewestFirst() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let old = makeRecord(match, createdAt: 10)
        let new = makeRecord(match, createdAt: 20)
        try library.upsert(MatchBundle(record: old, workout: nil))
        try library.upsert(MatchBundle(record: new, workout: WorkoutLog(matchID: new.id)))
        #expect(try library.summaries() == [MatchSummary(record: new), MatchSummary(record: old)])
        #expect(try library.all().map(\.id) == [new.id, old.id])
    }

    @Test func summaryIsBackfilledForBundlesSavedWithoutOne() throws {
        let dir = tempDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let record = makeRecord(match)
        try JSONEncoder().encode(MatchBundle(record: record, workout: nil))
            .write(to: dir.appendingPathComponent("\(record.id.uuidString).json"))
        let library = MatchLibrary(directory: dir)
        #expect(try library.summaries() == [MatchSummary(record: record)])
        #expect(FileManager.default.fileExists(atPath: dir.appendingPathComponent("\(record.id.uuidString).summary.json").path))
    }

    @Test func deleteRemovesSummary() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord(match)
        try library.upsert(MatchBundle(record: record, workout: nil))
        try library.delete(id: record.id)
        #expect(try library.summaries().isEmpty)
    }
}
