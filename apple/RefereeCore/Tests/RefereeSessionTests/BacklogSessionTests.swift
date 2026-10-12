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
