import Foundation

/// 워치 → 폰으로 보내는 경기 하나 (경기 기록 + 운동 데이터)
public struct MatchBundle: Codable, Equatable, Sendable, Identifiable {
    public var record: MatchRecord
    public var workout: WorkoutLog?

    public var id: UUID { record.id }

    public init(record: MatchRecord, workout: WorkoutLog?) {
        self.record = record
        self.workout = workout
    }
}

/// 폰에서 만들어 워치로 보내는 경기 설정
public struct MatchPreset: Codable, Equatable, Sendable, Identifiable {
    public var id: UUID
    public var name: String
    public var setup: MatchSetup

    public init(id: UUID, name: String, setup: MatchSetup) {
        self.id = id
        self.name = name
        self.setup = setup
    }

    /// 내장 프리셋 (수정·삭제 불가, 워치에 항상 있음)
    public static let fifaStandard = MatchPreset(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
        name: "FIFA 표준",
        setup: MatchSetup())
}

/// 프리셋 목록 파일. 없거나 읽을 수 없으면 빈 목록.
public struct PresetStore: Sendable {
    public let fileURL: URL

    public init(fileURL: URL) {
        self.fileURL = fileURL
    }

    public func load() -> [MatchPreset] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        return (try? JSONDecoder().decode([MatchPreset].self, from: data)) ?? []
    }

    public func save(_ presets: [MatchPreset]) throws {
        try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(presets).write(to: fileURL, options: .atomic)
    }
}

/// WatchConnectivity 메시지 형식
public enum SyncPayload {
    public static let matchIDKey = "matchID"
    public static let presetsKey = "presets"
    public static let entitlementKey = "entitlement"
    public static let unsupportedKey = "unsupported"

    /// 폰이 읽을 수 없는 형식의 경기 (앱 버전 차이). 워치는 다시 보내지 않고 보관한다.
    public static func unsupported(_ matchID: UUID) -> [String: Any] {
        [matchIDKey: matchID.uuidString, unsupportedKey: true]
    }

    public static func isUnsupported(_ info: [String: Any]) -> Bool {
        info[unsupportedKey] as? Bool == true
    }

    /// 폰이 경기를 저장했다는 확인 (transferUserInfo), 파일 전송 메타데이터로도 쓴다
    public static func ack(_ matchID: UUID) -> [String: Any] {
        [matchIDKey: matchID.uuidString]
    }

    public static func matchID(from info: [String: Any]) -> UUID? {
        (info[matchIDKey] as? String).flatMap(UUID.init(uuidString:))
    }

    /// 폰 → 워치 (updateApplicationContext). 컨텍스트는 통째로 바뀌므로 프리셋과 구독 상태를 항상 함께 보낸다.
    public static func context(presets: [MatchPreset], entitlement: Entitlement) throws -> [String: Any] {
        [presetsKey: try JSONEncoder().encode(presets), entitlementKey: try JSONEncoder().encode(entitlement)]
    }

    public static func entitlement(from context: [String: Any]) -> Entitlement? {
        guard let data = context[entitlementKey] as? Data else { return nil }
        return try? JSONDecoder().decode(Entitlement.self, from: data)
    }

    public static func presets(from context: [String: Any]) -> [MatchPreset]? {
        guard let data = context[presetsKey] as? Data else { return nil }
        return try? JSONDecoder().decode([MatchPreset].self, from: data)
    }
}

/// 폰의 경기 보관함. 경기마다 <id>.json(전체)과 <id>.summary.json(목록용 요약).
public struct MatchLibrary: Sendable {
    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    private static let summarySuffix = ".summary.json"
    private var deletedURL: URL { directory.appendingPathComponent("deleted.json") }

    private func deletedIDs() -> Set<UUID> {
        guard let data = try? Data(contentsOf: deletedURL) else { return [] }
        return (try? JSONDecoder().decode(Set<UUID>.self, from: data)) ?? []
    }

    private func url(for id: UUID) -> URL {
        directory.appendingPathComponent("\(id.uuidString).json")
    }

    private func summaryURL(for id: UUID) -> URL {
        directory.appendingPathComponent(id.uuidString + Self.summarySuffix)
    }

    /// 같은 경기가 다시 오면 덮어쓴다 (중복 전송에 안전). 요약도 함께 쓴다.
    /// 사용자가 지운 경기는 다시 저장하지 않고 false를 돌려준다 (확인은 그대로 보낸다).
    @discardableResult
    public func upsert(_ bundle: MatchBundle) throws -> Bool {
        guard !deletedIDs().contains(bundle.id) else { return false }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(bundle).write(to: url(for: bundle.id), options: .atomic)
        try JSONEncoder().encode(MatchSummary(bundle: bundle)).write(to: summaryURL(for: bundle.id), options: .atomic)
        return true
    }

    /// 목록용 요약 (최신순).
    /// - rebuild가 true면 요약이 없거나 이전 형식(통계 값 없음)인 경기는 전체 기록을 읽어 요약을 다시 만들어 둔다.
    ///   경기마다 전체 기록을 디코딩하므로 메인 스레드에서 부르지 않는다.
    /// - false면 저장된 요약만 읽는다 (이전 형식도 그대로, 요약이 없는 경기는 빠진다). 화면을 바로 그릴 때 쓴다.
    public func summaries(rebuild: Bool = true) throws -> [MatchSummary] {
        guard FileManager.default.fileExists(atPath: directory.path) else { return [] }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        let decoder = JSONDecoder()
        var summaries: [String: MatchSummary] = [:]
        for file in files where file.lastPathComponent.hasSuffix(Self.summarySuffix) {
            if let summary = try? decoder.decode(MatchSummary.self, from: Data(contentsOf: file)) {
                summaries[String(file.lastPathComponent.dropLast(Self.summarySuffix.count))] = summary
            }
        }
        if rebuild {
            for file in files where file.pathExtension == "json" && file.lastPathComponent != "deleted.json"
                && !file.lastPathComponent.hasSuffix(Self.summarySuffix) {
                let stem = String(file.lastPathComponent.dropLast(".json".count))
                guard summaries[stem]?.isCurrent != true else { continue }
                if let bundle = try? decoder.decode(MatchBundle.self, from: Data(contentsOf: file)) {
                    let summary = MatchSummary(bundle: bundle)
                    try? JSONEncoder().encode(summary).write(to: summaryURL(for: bundle.id), options: .atomic)
                    summaries[stem] = summary
                } else if var old = summaries[stem] {
                    // 전체 기록을 읽을 수 없으면 이전 요약을 쓰고, 다음부터 다시 시도하지 않게 최신으로 표시한다 (통계 값은 없음)
                    old.summaryVersion = MatchSummary.currentVersion
                    try? JSONEncoder().encode(old).write(to: summaryURL(for: old.id), options: .atomic)
                    summaries[stem] = old
                }
            }
        }
        // 시각이 같으면 id 순서로 (사전 순서가 매번 달라 목록이 흔들리지 않게)
        return summaries.values.sorted { ($0.createdAt, $0.id.uuidString) > ($1.createdAt, $1.id.uuidString) }
    }

    /// 최신 경기가 먼저. 읽을 수 없는 파일은 건너뛴다.
    public func all() throws -> [MatchBundle] {
        guard FileManager.default.fileExists(atPath: directory.path) else { return [] }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        let decoder = JSONDecoder()
        return files
            .filter { $0.pathExtension == "json" && !$0.lastPathComponent.hasSuffix(Self.summarySuffix) && $0.lastPathComponent != "deleted.json" }
            .compactMap { try? decoder.decode(MatchBundle.self, from: Data(contentsOf: $0)) }
            .sorted { $0.record.createdAt > $1.record.createdAt }
    }

    public func bundle(id: UUID) throws -> MatchBundle? {
        let file = url(for: id)
        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
        return try JSONDecoder().decode(MatchBundle.self, from: Data(contentsOf: file))
    }

    public func delete(id: UUID) throws {
        for file in [url(for: id), summaryURL(for: id)] where FileManager.default.fileExists(atPath: file.path) {
            try FileManager.default.removeItem(at: file)
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var deleted = deletedIDs()
        deleted.insert(id)
        try JSONEncoder().encode(deleted).write(to: deletedURL, options: .atomic)
    }
}
