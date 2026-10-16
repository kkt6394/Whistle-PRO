import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

private func point(_ t: Double, kmh: Double) -> RoutePoint {
    RoutePoint(t: t, latitude: 0, longitude: 0, speed: kmh / 3.6, accuracy: 5)
}

struct BacklogSessionTests {
    @Test func distanceSurvivesWorkoutRestart() {
        // 1000초에 2000m, 운동 세션 재시작으로 1500초에 100m부터 다시, 2700초에 1500m
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 1000, value: 2000),
                        WorkoutSample(t: 1500, value: 100), WorkoutSample(t: 2700, value: 1500)]
        log.restarts = [1200]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState([(.startPeriod, 0), (.endPeriod, 2700)]), now: 3000)
        #expect(stats.map(\.distance) == [3500])
    }

    /// 백로그: 재시작 뒤 첫 누적값이 재시작 전 값보다 크면 차이만 더해 적게 셌다 (값이 줄어드는지로만 재시작을 알았다)
    @Test func restartWithLargerFirstValueCountsFully() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 500, value: 300),
                        WorkoutSample(t: 1500, value: 400)]
        log.restarts = [600]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState([(.startPeriod, 0), (.endPeriod, 2700)]), now: 3000)
        #expect(stats.map(\.distance) == [700])
    }

    /// 재시작 표시 없이 값이 잠깐 줄어도(순서가 뒤바뀐 샘플) 새로 시작한 것으로 보지 않는다
    @Test func dipWithoutRestartIsNotCountedAgain() {
        var log = WorkoutLog(matchID: UUID())
        log.distance = [WorkoutSample(t: 0, value: 0), WorkoutSample(t: 100, value: 500),
                        WorkoutSample(t: 110, value: 480), WorkoutSample(t: 200, value: 700)]
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState([(.startPeriod, 0), (.endPeriod, 2700)]), now: 3000)
        #expect(stats.map(\.distance) == [700])
    }

    /// 새 기록은 재시작이 없어도 "restarts": []를 쓴다 — 키가 없는 예전 기록과 구분한다 (리뷰)
    @Test func newLogsAlwaysWriteRestarts() throws {
        let log = WorkoutLog(matchID: UUID())
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(log)) as! [String: Any]
        #expect((json["restarts"] as? [Double]) == [])
        var restarted = log
        restarted.restarts = [10]
        #expect(try JSONDecoder().decode(WorkoutLog.self, from: JSONEncoder().encode(restarted)).restarts == [10])
    }

    /// 예전 기록(키 없음)은 예전처럼 값이 줄면 재시작으로 본다 — 이미 3500m로 보이던 경기가 2000m로 바뀌지 않게 (리뷰)
    @Test func legacyLogKeepsDecreaseHeuristic() throws {
        var json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(WorkoutLog(matchID: UUID()))) as! [String: Any]
        json["restarts"] = nil
        json["distance"] = [["t": 0, "value": 0], ["t": 1000, "value": 2000], ["t": 1500, "value": 100], ["t": 2700, "value": 1500]]
        let log = try JSONDecoder().decode(WorkoutLog.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(log.restarts == nil)
        let stats = WorkoutAnalysis.perPeriod(log, state: makeState([(.startPeriod, 0), (.endPeriod, 2700)]), now: 3000)
        #expect(stats.map(\.distance) == [3500])
    }

    @Test func twoSampleSpikeIsNotASprint() {
        #expect(WorkoutAnalysis.sprintCount([point(0, kmh: 10), point(1, kmh: 30), point(2, kmh: 30), point(3, kmh: 10)]) == 0)
        #expect(WorkoutAnalysis.sprintCount([point(0, kmh: 10), point(1, kmh: 30), point(2, kmh: 30), point(3, kmh: 30)]) == 1)
    }

    @Test func deletedMatchIsNotStoredAgain() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let bundle = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        #expect(try library.upsert(bundle))
        try library.delete(id: bundle.id)
        #expect(try !library.upsert(bundle))
        #expect(try library.summaries().isEmpty)
        #expect(try library.bundle(id: bundle.id) == nil)
    }

    /// 경기 확인 누락 후속: 워치가 물은 경기 중 폰이 가진 경기(지운 경기 포함)만 답한다
    @Test func libraryAnswersReceivedAmongAsked() throws {
        let library = MatchLibrary(directory: tempDirectory())
        let stored = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        let deleted = MatchBundle(record: makeRecord([(.startPeriod, 10)]), workout: nil)
        let missing = UUID()
        try library.upsert(stored)
        try library.upsert(deleted)
        try library.delete(id: deleted.id)
        #expect(library.received(among: [stored.id, missing, deleted.id]) == [stored.id, deleted.id])
        #expect(MatchLibrary(directory: tempDirectory()).received(among: [missing]).isEmpty)
    }

    @Test func receiptQueryRoundTrips() {
        let ids = [UUID(), UUID()]
        #expect(SyncPayload.receiptQueryIDs(from: SyncPayload.receiptQuery(ids)) == ids)
        #expect(SyncPayload.receivedIDs(from: SyncPayload.receiptReply(ids)) == ids)
        // 다른 메시지는 질문이 아니고, 이전 폰 앱의 빈 답은 아무것도 확인하지 않는다
        #expect(SyncPayload.receiptQueryIDs(from: SyncPayload.contextRequest()) == nil)
        #expect(SyncPayload.receivedIDs(from: [:]).isEmpty)
    }

    /// 백로그: deleted.json이 손상되면 다음 삭제 때 지운 경기 목록이 모두 사라졌다 (지운 경기가 다시 저장됨)
    @Test func corruptedDeletedListDoesNotResurrectDeletedMatches() throws {
        let directory = tempDirectory()
        let library = MatchLibrary(directory: directory)
        let first = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        let second = MatchBundle(record: makeRecord([(.startPeriod, 10)]), workout: nil)
        try library.upsert(first)
        try library.upsert(second)
        try library.delete(id: first.id)
        // 예전 형식 목록 파일이 깨져 있어도
        try Data("{not json".utf8).write(to: directory.appendingPathComponent("deleted.json"))
        try library.delete(id: second.id)
        #expect(try !library.upsert(first))
        #expect(try !library.upsert(second))
    }

    /// 예전 형식(deleted.json 하나)으로 지운 경기도 계속 지운 것으로 본다
    @Test func legacyDeletedListStillRespected() throws {
        let directory = tempDirectory()
        let library = MatchLibrary(directory: directory)
        let bundle = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(Set([bundle.id])).write(to: directory.appendingPathComponent("deleted.json"))
        #expect(try !library.upsert(bundle))
    }

    /// 받았는지 답할 때 예전 목록(deleted.json)으로 지운 경기도 받은 것으로 센다 (목록은 한 번만 읽는다)
    @Test func receivedAmongIncludesLegacyDeletedList() throws {
        let directory = tempDirectory()
        let library = MatchLibrary(directory: directory)
        let stored = MatchBundle(record: makeRecord([(.startPeriod, 0)]), workout: nil)
        let marked = MatchBundle(record: makeRecord([(.startPeriod, 5)]), workout: nil)
        let legacyA = UUID(), legacyB = UUID(), missing = UUID()
        try library.upsert(stored)
        try library.upsert(marked)
        try library.delete(id: marked.id)
        try JSONEncoder().encode(Set([legacyA, legacyB])).write(to: directory.appendingPathComponent("deleted.json"))
        #expect(library.received(among: [legacyA, missing, stored.id, marked.id, legacyB]) == [legacyA, stored.id, marked.id, legacyB])
    }

    /// 백로그: 같은 경기를 다시 보내면 끝난 이전 전송이 새 전송의 파일(같은 이름)을 지울 수 있었다 → 전송마다 다른 파일
    @Test func outboxFileNameIsUniquePerTransfer() {
        let id = UUID()
        let a = SyncPayload.outboxFileName(for: id), b = SyncPayload.outboxFileName(for: id)
        #expect(a != b)
        #expect(a.hasPrefix(id.uuidString) && a.hasSuffix(".json"))
    }

    @Test func unsupportedPayloadRoundTrips() {
        let id = UUID()
        let info = SyncPayload.unsupported(id)
        #expect(SyncPayload.isUnsupported(info))
        #expect(SyncPayload.matchID(from: info) == id)
        #expect(!SyncPayload.isUnsupported(SyncPayload.ack(id)))
    }

    @Test func subscriptionProductsAreShared() {
        #expect(SubscriptionProducts.ids == ["com.aidenstudio.whistlepro.pro.monthly", "com.aidenstudio.whistlepro.pro.yearly"])
        #expect(SubscriptionProducts.contains("com.aidenstudio.whistlepro.pro.yearly"))
        #expect(!SubscriptionProducts.contains("com.aidenstudio.whistlepro.pro.lifetime"))
    }
}

/// 최종 리뷰: 보고서·화면 표시 경로도 손상된 큰 타임스탬프에서 죽지 않아야 한다
struct HugeTimestampSessionTests {
    @Test func reportSurvivesHugeTimestamps() {
        let record = makeRecord([(.startPeriod, 0), (.endPeriod, 1e300)])
        let report = MatchReport(bundle: MatchBundle(record: record, workout: nil))
        #expect(report.periods.count == 1)
    }

    @Test func preExtraTimeClockSurvivesHugeNow() {
        let s = makeState(RuleSet(extraTime: true), [(.startPeriod, 0), (.endPeriod, 2700), (.startPeriod, 3600), (.endPeriod, 6300)])
        #expect(MatchDisplay(state: s, now: 1e300, clockDisplay: .elapsed).screen == .preExtraTime)
        #expect(MatchDisplay(state: s, now: .infinity, clockDisplay: .elapsed).intervalClock == "00:00")
    }
}
