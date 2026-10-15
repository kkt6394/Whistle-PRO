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
