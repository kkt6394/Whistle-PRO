import Foundation
import RefereeCore
@testable import RefereeSession

/// 메모리 저장소. failSaves를 켜면 저장이 실패한다.
final class MemoryStore: MatchLogStore, @unchecked Sendable {
    struct SaveFailed: Error {}

    var active: MatchRecord?
    var finished: [MatchRecord] = []
    var failSaves = false

    func loadActive() throws -> MatchRecord? { active }

    func saveActive(_ record: MatchRecord) throws {
        if failSaves { throw SaveFailed() }
        active = record
    }

    func finishActive(_ record: MatchRecord) throws {
        if failSaves { throw SaveFailed() }
        finished.append(record)
        active = nil
    }

    func archived() throws -> [MatchRecord] { finished }
}

final class FakeClock {
    var t: Double = 0
}

func makeState(_ rules: RuleSet = .fifaStandard, _ commands: [(CommandKind, Double)]) -> MatchState {
    MatchEngine.reduce(rules: rules, commands: commands.map { Command(timestamp: $0.1, kind: $0.0) })
}

func tempDirectory() -> URL {
    FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
}
