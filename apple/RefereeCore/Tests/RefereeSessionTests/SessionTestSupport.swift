import Foundation
import RefereeCore
@testable import RefereeSession

/// 메모리 저장소. failSaves를 켜면 저장이 실패한다.
final class MemoryStore: MatchLogStore, @unchecked Sendable {
    struct SaveFailed: Error {}

    var active: MatchRecord?
    var finished: [MatchRecord] = []
    var activeWorkout: WorkoutLog?
    var finishedWorkouts: [UUID: WorkoutLog] = [:]
    var failSaves = false

    func loadActive() throws -> MatchRecord? { active }

    func saveActive(_ record: MatchRecord) throws {
        if failSaves { throw SaveFailed() }
        active = record
    }

    func finishActive(_ record: MatchRecord) throws {
        if failSaves { throw SaveFailed() }
        finished.append(record)
        if let activeWorkout, activeWorkout.matchID == record.id {
            finishedWorkouts[record.id] = activeWorkout
        }
        active = nil
        activeWorkout = nil
    }

    func discardActive() throws {
        active = nil
        activeWorkout = nil
    }

    func archived() throws -> [MatchRecord] { finished }

    func saveActiveWorkout(_ log: WorkoutLog) throws {
        if failSaves { throw SaveFailed() }
        activeWorkout = log
    }

    func loadActiveWorkout() throws -> WorkoutLog? { activeWorkout }

    func archivedWorkout(for matchID: UUID) throws -> WorkoutLog? { finishedWorkouts[matchID] }

    func deleteArchived(_ matchID: UUID) throws {
        finished.removeAll { $0.id == matchID }
        finishedWorkouts[matchID] = nil
    }
}

final class FakeClock {
    var t: Double = 0
}

func makeState(_ rules: RuleSet, _ commands: [(CommandKind, Double)]) -> MatchState {
    MatchEngine.reduce(rules: rules, commands: commands.map { Command(timestamp: $0.1, kind: $0.0) })
}

/// FIFA 표준 규칙으로 재생
func makeState(_ commands: [(CommandKind, Double)]) -> MatchState {
    makeState(.fifaStandard, commands)
}

func tempDirectory() -> URL {
    FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
}

/// 명령 목록으로 경기 기록을 만든다 (명령 id는 c0, c1, ...)
func makeRecord(_ setup: MatchSetup, _ commands: [(CommandKind, Double)], createdAt: Double = 0) -> MatchRecord {
    MatchRecord(
        id: UUID(), createdAt: createdAt, setup: setup,
        commands: commands.enumerated().map { Command(id: "c\($0.offset)", timestamp: $0.element.1, kind: $0.element.0) })
}

/// 기본 설정(FIFA 표준)으로 경기 기록을 만든다
func makeRecord(_ commands: [(CommandKind, Double)], createdAt: Double = 0) -> MatchRecord {
    makeRecord(MatchSetup(), commands, createdAt: createdAt)
}
