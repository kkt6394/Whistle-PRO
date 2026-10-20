import Foundation
import Testing
@testable import RefereeCore

private struct ScenarioInput: Decodable {
    let name: String
    let config: RuleSet
    let warningBeforeEnd: Int?
    let breakWarningBeforeEnd: Int?
    let commands: [Command]
}

enum Scenarios {
    static let directory = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // RefereeCoreTests
        .deletingLastPathComponent() // Tests
        .deletingLastPathComponent() // RefereeCore
        .deletingLastPathComponent() // apple
        .deletingLastPathComponent() // 저장소 루트
        .appendingPathComponent("shared/engine-scenarios")

    static var files: [URL] {
        let all = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
        return all.filter { $0.pathExtension == "json" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }
}

struct ScenarioTests {
    @Test func scenarioDirectoryIsFound() {
        #expect(Scenarios.files.count >= 10, "시나리오 디렉터리: \(Scenarios.directory.path)")
    }

    @Test(arguments: Scenarios.files)
    func scenario(_ file: URL) throws {
        let data = try Data(contentsOf: file)
        let input = try JSONDecoder().decode(ScenarioInput.self, from: data)
        let raw = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let expectations = try #require(raw["expect"] as? [[String: Any]])
        // id를 적지 않은 명령에만 "#<인덱스>"를 붙인다 (디코더는 빈 id 대신 UUID를 만든다)
        let rawCommands = try #require(raw["commands"] as? [[String: Any]])
        let commands = input.commands.enumerated().map { index, command in
            var command = command
            if rawCommands[index]["id"] == nil { command.id = "#\(index)" }
            return command
        }

        for expected in expectations {
            let at = try #require(expected["at"] as? NSNumber).doubleValue
            let state = MatchEngine.reduce(rules: input.config, commands: commands.filter { $0.timestamp <= at })
            let snapshot = state.snapshot(at: at, warningBeforeEnd: input.warningBeforeEnd ?? 300,
                                          breakWarningBeforeEnd: input.breakWarningBeforeEnd ?? 300)
            let actual = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(snapshot)) as? [String: Any])
            for (key, value) in expected where key != "at" {
                let actualValue = actual[key] ?? NSNull()
                #expect((actualValue as AnyObject).isEqual(value),
                        "\(input.name) @\(at) \(key): 실제 \(actualValue), 기대 \(value)")
            }
        }
    }

    @Test func snapshotEncodesTeamPairsAsArraysAndOmitsNil() throws {
        let s = play([cmd(.startPeriod, at: 0), goal(.home, at: 10)])
        let data = try JSONEncoder().encode(s.snapshot(at: 20, warningBeforeEnd: 300, breakWarningBeforeEnd: 300))
        let json = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(json["score"] as? [Int] == [1, 0])
        #expect(json["phase"] as? String == "period")
        #expect(json["finishReason"] == nil)
    }
}
