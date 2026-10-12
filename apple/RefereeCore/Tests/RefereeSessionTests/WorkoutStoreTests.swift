import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct WorkoutStoreTests {
    @Test func oldSetupWithoutRecordsRouteDecodes() throws {
        var json = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(MatchSetup())) as? [String: Any])
        json.removeValue(forKey: "recordsRoute")
        let setup = try JSONDecoder().decode(MatchSetup.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(setup.recordsRoute)
        #expect(setup == MatchSetup())
    }

    @Test func recordsRouteRoundTrips() throws {
        var setup = MatchSetup()
        setup.recordsRoute = false
        #expect(try JSONDecoder().decode(MatchSetup.self, from: JSONEncoder().encode(setup)) == setup)
    }

    @Test func noActiveWorkoutInitially() throws {
        #expect(try FileMatchLogStore(directory: tempDirectory()).loadActiveWorkout() == nil)
    }

    @Test func workoutLogMovesToArchiveWithItsMatch() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let record = MatchRecord(id: UUID(), createdAt: 0, setup: MatchSetup(), commands: [])
        var log = WorkoutLog(matchID: record.id)
        log.heartRate = [WorkoutSample(t: 1, value: 120)]
        log.route = [RoutePoint(t: 1, latitude: 37.5, longitude: 127, speed: 3, accuracy: 5)]
        try store.saveActive(record)
        try store.saveActiveWorkout(log)
        #expect(try store.loadActiveWorkout() == log)

        try store.finishActive(record)
        #expect(try store.loadActiveWorkout() == nil)
        #expect(try store.archivedWorkout(for: record.id) == log)
        #expect(try store.archived() == [record])
    }

    @Test func workoutOfAnotherMatchIsNotArchived() throws {
        let store = FileMatchLogStore(directory: tempDirectory())
        let record = MatchRecord(id: UUID(), createdAt: 0, setup: MatchSetup(), commands: [])
        try store.saveActive(record)
        try store.saveActiveWorkout(WorkoutLog(matchID: UUID()))
        try store.finishActive(record)
        #expect(try store.archivedWorkout(for: record.id) == nil)
        #expect(try store.loadActiveWorkout() == nil)
    }
}
