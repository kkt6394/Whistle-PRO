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
        // 안드로이드 readsSwiftEncodedBundle 픽스처: SWIFT_BUNDLE_FIXTURE=<경로>로 돌리면 고정 값 묶음을 써 둔다
        if let path = ProcessInfo.processInfo.environment["SWIFT_BUNDLE_FIXTURE"] {
            var fixedRecord = makeRecord(MatchSetup(homeName: "FC 서울", awayName: "수원 FC"),
                                         [(.startPeriod, 100), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 600), (.endPeriod, 2800)],
                                         createdAt: 50)
            fixedRecord.id = UUID(uuidString: "6F9619FF-8B86-D011-B42D-00C04FC964FF")!
            var fixedWorkout = WorkoutLog(matchID: fixedRecord.id)
            fixedWorkout.heartRate = [WorkoutSample(t: 110, value: 140)]
            fixedWorkout.distance = [WorkoutSample(t: 100, value: 0), WorkoutSample(t: 2800, value: 5400)]
            try JSONEncoder().encode(MatchBundle(record: fixedRecord, workout: fixedWorkout)).write(to: URL(fileURLWithPath: path))
        }
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

    /// 새로 설치된 워치 앱은 받은 컨텍스트가 비어 있다 → 폰에 요청해야 한다 (폰 앱이 꺼져 있으면 재설치 알림을 놓친다)
    @Test func emptyContextNeedsRequest() throws {
        #expect(SyncPayload.needsContext([:]))
        #expect(!SyncPayload.needsContext(try SyncPayload.context(presets: [], entitlement: .none)))
    }

    @Test func contextRequestIsRecognized() {
        #expect(SyncPayload.isContextRequest(SyncPayload.contextRequest()))
        #expect(!SyncPayload.isContextRequest(SyncPayload.ack(UUID())))
        #expect(SyncPayload.matchID(from: SyncPayload.contextRequest()) == nil)
    }

    /// 명단이 꽉 찬 프리셋: 3글자 이름이면 열 개는 들어가고 스무 개는 넘친다 (실측 3.3KB/개)
    @Test func fullRosterPresetsFitContextUpToTen() throws {
        let roster = (1...40).map { Player(number: $0, name: "김민준") }
        let preset = MatchPreset(id: UUID(), name: "조기축구", setup: MatchSetup(homeRoster: roster, awayRoster: roster))
        #expect(SyncPayload.presetsFitContext([]))
        #expect(SyncPayload.presetsFitContext(Array(repeating: preset, count: 10)))
        #expect(!SyncPayload.presetsFitContext(Array(repeating: preset, count: 20)))
        let ten = try SyncPayload.context(presets: Array(repeating: preset, count: 10), entitlement: .none)
        let size = try PropertyListSerialization.data(fromPropertyList: ten, format: .binary, options: 0).count
        #expect(size < SyncPayload.contextLimit)
    }

    private func contextSize(_ context: [String: Any]) throws -> Int {
        try PropertyListSerialization.data(fromPropertyList: context, format: .binary, options: 0).count
    }

    @Test func contextKeepsAllPresetsThatFit() throws {
        let presets = (0..<3).map { MatchPreset(id: UUID(), name: "P\($0)", setup: MatchSetup()) }
        #expect(SyncPayload.presetsFittingContext(presets) == presets)
        let context = try SyncPayload.context(presets: presets, entitlement: .none)
        #expect(SyncPayload.presets(from: context) == presets)
    }

    /// 넘치면 앞에서부터 들어가는 만큼만, 구독 상태는 그대로 간다
    @Test func oversizedContextKeepsPrefixAndEntitlement() throws {
        let roster = (1...40).map { Player(number: $0, name: String(repeating: "가", count: 20)) }
        let presets = (0..<20).map { MatchPreset(id: UUID(), name: "P\($0)", setup: MatchSetup(homeRoster: roster, awayRoster: roster)) }
        let fitting = SyncPayload.presetsFittingContext(presets)
        #expect(!fitting.isEmpty && fitting.count < presets.count)
        #expect(fitting == Array(presets.prefix(fitting.count)))
        let entitlement = Entitlement.none
        let context = try SyncPayload.context(presets: presets, entitlement: entitlement, sentAt: 5)
        #expect(SyncPayload.presets(from: context) == fitting)
        #expect(SyncPayload.entitlement(from: context) == entitlement)
        #expect(context[SyncPayload.sentAtKey] as? Double == 5)
        #expect(try contextSize(context) <= SyncPayload.contextLimit)
    }

    @Test func singleOversizedPresetSendsOnlyEntitlement() throws {
        let roster = (0..<2000).map { Player(number: $0, name: String(repeating: "가", count: 20)) }
        let huge = MatchPreset(id: UUID(), name: "Huge", setup: MatchSetup(homeRoster: roster))
        #expect(SyncPayload.presetsFittingContext([huge]).isEmpty)
        let context = try SyncPayload.context(presets: [huge, MatchPreset(id: UUID(), name: "Small", setup: MatchSetup())], entitlement: .none)
        #expect(SyncPayload.presets(from: context) == [])
        #expect(SyncPayload.entitlement(from: context) == Entitlement.none)
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

    /// 지난 경기 리뷰: 워치가 이미 재생한 상태로 요약을 만들어 같은 경기를 두 번 재생하지 않는다
    @Test func summaryFromReducedStateMatches() {
        let record = makeRecord(MatchSetup(homeName: "A", awayName: "B"), match, createdAt: 50)
        let state = MatchEngine.reduce(rules: record.setup.rules, commands: record.commands)
        #expect(MatchSummary(record: record, state: state) == MatchSummary(record: record))
    }

    @Test func upsertWritesSummaryNewestFirst() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let old = makeRecord(match, createdAt: 10)
        let new = makeRecord(match, createdAt: 20)
        try library.upsert(MatchBundle(record: old, workout: nil))
        try library.upsert(MatchBundle(record: new, workout: WorkoutLog(matchID: new.id)))
        #expect(try library.summaries() == [MatchSummary(bundle: MatchBundle(record: new, workout: WorkoutLog(matchID: new.id))),
                                            MatchSummary(bundle: MatchBundle(record: old, workout: nil))])
        #expect(try library.all().map(\.id) == [new.id, old.id])
    }

    @Test func summaryIsBackfilledForBundlesSavedWithoutOne() throws {
        let dir = tempDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let record = makeRecord(match)
        try JSONEncoder().encode(MatchBundle(record: record, workout: nil))
            .write(to: dir.appendingPathComponent("\(record.id.uuidString).json"))
        let library = MatchLibrary(directory: dir)
        #expect(try library.summaries() == [MatchSummary(bundle: MatchBundle(record: record, workout: nil))])
        #expect(FileManager.default.fileExists(atPath: dir.appendingPathComponent("\(record.id.uuidString).summary.json").path))
    }

    @Test func rebuildsOldSummaryOnce() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord([(.startPeriod, 0), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 60)])
        try library.upsert(MatchBundle(record: record, workout: nil))
        // 이전 버전 요약 흉내: 통계 키를 지운다
        let url = library.directory.appendingPathComponent(record.id.uuidString + ".summary.json")
        var json = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
        for key in ["summaryVersion", "goals", "yellows", "reds", "sinBins", "addedSeconds"] { json.removeValue(forKey: key) }
        try JSONSerialization.data(withJSONObject: json).write(to: url)
        #expect(try JSONDecoder().decode(MatchSummary.self, from: Data(contentsOf: url)).isCurrent == false)

        let rebuilt = try library.summaries()
        #expect(rebuilt.first?.goals == 1)
        #expect(rebuilt.first?.summaryVersion == MatchSummary.currentVersion)
        #expect(try JSONDecoder().decode(MatchSummary.self, from: Data(contentsOf: url)).isCurrent)
    }

    @Test func oldSummaryIsKeptWhenBundleIsUnreadable() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord(match)
        try library.upsert(MatchBundle(record: record, workout: nil))
        let url = library.directory.appendingPathComponent(record.id.uuidString + ".summary.json")
        var json = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
        json.removeValue(forKey: "summaryVersion")
        try JSONSerialization.data(withJSONObject: json).write(to: url)
        try Data("broken".utf8).write(to: library.directory.appendingPathComponent(record.id.uuidString + ".json"))
        #expect(try library.summaries().map(\.id) == [record.id])
        // 다음부터는 깨진 기록을 다시 읽지 않는다
        #expect(try JSONDecoder().decode(MatchSummary.self, from: Data(contentsOf: url)).isCurrent)
    }

    @Test func quickSummariesDoNotRebuild() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord([(.startPeriod, 0), (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 60)])
        try library.upsert(MatchBundle(record: record, workout: nil))
        let url = library.directory.appendingPathComponent(record.id.uuidString + ".summary.json")
        var json = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
        json.removeValue(forKey: "summaryVersion")
        json.removeValue(forKey: "goals")
        try JSONSerialization.data(withJSONObject: json).write(to: url)

        #expect(try library.summaries(rebuild: false).first?.goals == nil)
        #expect(try JSONDecoder().decode(MatchSummary.self, from: Data(contentsOf: url)).isCurrent == false)
        #expect(try library.summaries().first?.goals == 1)
        // 다시 만든 요약은 다음 호출에서 그대로 쓴다 (전체 기록을 지워도 같은 값)
        try FileManager.default.removeItem(at: library.directory.appendingPathComponent(record.id.uuidString + ".json"))
        #expect(try library.summaries().first?.goals == 1)
    }

    @Test func summaryCarriesSeasonValues() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 2800, value: 6000)]
        log.heartRate = [WorkoutSample(t: 10, value: 150)]
        let record = makeRecord([
            (.startPeriod, 0),
            (.recordEvent(.goal(team: .home, number: 9, ownGoal: false)), 60),
            (.recordEvent(.goal(team: .away, number: 3, ownGoal: true)), 70),
            (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 100),
            (.recordEvent(.card(team: .away, number: 4, kind: .yellow)), 200),
            (.recordEvent(.card(team: .home, number: 5, kind: .red)), 300),
            (.endPeriod, 2800),
        ])
        log.matchID = record.id
        let summary = MatchSummary(bundle: MatchBundle(record: record, workout: log))
        #expect(summary.goals == 2)
        // 꺼낸 옐로 두 장 (두 번째는 경고 누적 퇴장으로 레드에도 센다)
        #expect(summary.yellows == 2)
        #expect(summary.reds == 2)
        #expect(summary.sinBins == 0)
        #expect(summary.addedSeconds == 100)
        #expect(summary.distance == 6000)
        #expect(summary.averageHeartRate == 150)
    }

    @Test func deleteRemovesSummary() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let record = makeRecord(match)
        try library.upsert(MatchBundle(record: record, workout: nil))
        try library.delete(id: record.id)
        #expect(try library.summaries().isEmpty)
    }
}
