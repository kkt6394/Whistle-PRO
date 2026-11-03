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
    /// 보낸 시각: 내용이 같아도 다시 전달되게 한다 (워치 앱 재설치 후 다시 보낼 때)
    public static let sentAtKey = "sentAt"

    public static let contextRequestKey = "contextRequest"
    public static let watchBuildKey = "watchBuild"

    /// 워치 → 폰 컨텍스트: 워치 앱 빌드 번호. 워치가 켜질 때 올려 두면 폰 앱이 꺼져 있어도 다음 실행 때 받는다.
    /// 폰과 워치는 한 아카이브로 올라가 빌드 번호(YYYYMMDDHHMM)가 같다 — 워치 자동 업데이트가 꺼져 있으면 어긋난다
    public static func watchBuild(_ build: String) -> [String: Any] {
        [watchBuildKey: build]
    }

    public static func watchBuild(from context: [String: Any]) -> String? {
        context[watchBuildKey] as? String
    }

    /// 워치 앱이 폰 앱보다 옛 빌드인가. 모르는 값(버전을 안 알리는 옛 워치 앱·숫자가 아닌 빌드)은 아니라고 본다
    public static func isWatchOutdated(watchBuild: String?, phoneBuild: String?) -> Bool {
        guard let watch = watchBuild.flatMap(Int.init), let phone = phoneBuild.flatMap(Int.init) else { return false }
        return watch < phone
    }

    /// 워치 → 폰: 프리셋·구독 상태를 보내 달라는 요청. 새로 설치된 워치 앱은 컨텍스트가 비어 있는데,
    /// 폰 앱이 그때 실행 중이 아니면 재설치 알림(sessionWatchStateDidChange)을 놓쳐 다시 보내지 않는다.
    public static func contextRequest() -> [String: Any] {
        [contextRequestKey: true]
    }

    public static func isContextRequest(_ info: [String: Any]) -> Bool {
        info[contextRequestKey] as? Bool == true
    }

    /// 받은 컨텍스트가 없다 (새로 설치된 워치 앱): 폰에 요청한다
    public static func needsContext(_ context: [String: Any]) -> Bool {
        context[presetsKey] == nil && context[entitlementKey] == nil
    }

    /// 폰이 읽을 수 없는 형식의 경기 (앱 버전 차이). 워치는 다시 보내지 않고 보관한다.
    public static func unsupported(_ matchID: UUID) -> [String: Any] {
        [matchIDKey: matchID.uuidString, unsupportedKey: true]
    }

    public static func isUnsupported(_ info: [String: Any]) -> Bool {
        info[unsupportedKey] as? Bool == true
    }

    /// 폰이 경기를 저장했다는 확인 (transferUserInfo), 파일 전송 메타데이터로도 쓴다
    /// 워치 outbox 파일 이름: 전송마다 다르게. 같은 이름이면 끝난 이전 전송의 정리가 새 전송의 파일을 지울 수 있었다 (백로그)
    public static func outboxFileName(for matchID: UUID) -> String {
        "\(matchID.uuidString)-\(UUID().uuidString).json"
    }

    public static func ack(_ matchID: UUID) -> [String: Any] {
        [matchIDKey: matchID.uuidString]
    }

    public static func matchID(from info: [String: Any]) -> UUID? {
        (info[matchIDKey] as? String).flatMap(UUID.init(uuidString:))
    }

    public static let receiptQueryKey = "receiptQuery"
    public static let receivedKey = "received"

    /// 워치 → 폰 (sendMessage, 답을 받는다): 보관함에 남은 경기 중 이미 받은 게 있는지 묻는다.
    /// 확인(ack)을 놓치면 워치 보관함에 경기가 계속 남았다 (경기 확인 누락 후속)
    public static func receiptQuery(_ matchIDs: [UUID]) -> [String: Any] {
        [receiptQueryKey: matchIDs.map(\.uuidString)]
    }

    /// 묻는 메시지가 아니면 nil
    public static func receiptQueryIDs(from message: [String: Any]) -> [UUID]? {
        guard let ids = message[receiptQueryKey] as? [String] else { return nil }
        return ids.compactMap(UUID.init(uuidString:))
    }

    /// 폰 → 워치: 물은 경기 중 폰이 이미 가진(또는 사용자가 지운) 경기
    public static func receiptReply(_ matchIDs: [UUID]) -> [String: Any] {
        [receivedKey: matchIDs.map(\.uuidString)]
    }

    /// 빈 답(이 질문을 모르는 이전 폰 앱)이면 빈 목록 → 아무것도 확인하지 않는다
    public static func receivedIDs(from reply: [String: Any]) -> [UUID] {
        ((reply[receivedKey] as? [String]) ?? []).compactMap(UUID.init(uuidString:))
    }

    /// 폰 → 워치 (updateApplicationContext). 컨텍스트는 통째로 바뀌므로 프리셋과 구독 상태를 항상 함께 보낸다.
    /// 프리셋이 한도를 넘으면 앞에서부터 들어가는 만큼만 넣는다 (구독 상태는 늘 가야 한다).
    public static func context(presets: [MatchPreset], entitlement: Entitlement,
                               sentAt: Double = Date().timeIntervalSince1970) throws -> [String: Any] {
        [presetsKey: try JSONEncoder().encode(presetsFittingContext(presets)),
         entitlementKey: try JSONEncoder().encode(entitlement), sentAtKey: sentAt]
    }

    public static func entitlement(from context: [String: Any]) -> Entitlement? {
        guard let data = context[entitlementKey] as? Data else { return nil }
        return try? JSONDecoder().decode(Entitlement.self, from: data)
    }

    /// updateApplicationContext가 받아 주는 크기 (WCSession 메시지 한도와 같은 64KB로 본다, 실기기 미확인).
    /// 넘으면 컨텍스트 전체(구독 상태 포함)가 가지 않는다.
    public static let contextLimit = 65_536
    /// 구독 상태·보낸 시각·plist 몫으로 남기는 크기
    static let contextReserve = 4096

    /// 한도 안에 드는 앞쪽 프리셋 (목록 순서). 첫 프리셋부터 넘치면 빈 목록.
    /// 명단이 꽉 찬(팀당 40명) 프리셋 하나가 3.3~7.4KB라 이름이 최대 길이면 9개쯤, 3글자 이름이면 18개쯤에서 넘친다.
    public static func presetsFittingContext(_ presets: [MatchPreset]) -> [MatchPreset] {
        let encoder = JSONEncoder()
        // JSON 배열 크기 = 괄호 2 + 항목들 + 항목 사이 쉼표
        var size = 2
        var fitting: [MatchPreset] = []
        for preset in presets {
            guard let data = try? encoder.encode(preset) else { break }
            size += data.count + (fitting.isEmpty ? 0 : 1)
            if size > contextLimit - contextReserve { break }
            fitting.append(preset)
        }
        return fitting
    }

    /// 프리셋을 모두 워치로 보낼 수 있는지 (넘치면 목록·편집기에 안내한다)
    public static func presetsFitContext(_ presets: [MatchPreset]) -> Bool {
        presetsFittingContext(presets).count == presets.count
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
    /// 예전 형식: 지운 경기 id 목록 파일 하나 (읽기만 한다)
    private var legacyDeletedURL: URL { directory.appendingPathComponent("deleted.json") }
    private static let deletedSuffix = ".deleted"

    /// 지운 경기 표시: 경기마다 빈 파일 <id>.deleted. 목록 파일 하나를 읽고 다시 쓰면,
    /// 그 파일이 깨졌을 때 다음 삭제가 예전에 지운 경기들을 모두 잊었다 (백로그).
    private func deletedMarkerURL(for id: UUID) -> URL {
        directory.appendingPathComponent(id.uuidString + Self.deletedSuffix)
    }

    private func isDeleted(_ id: UUID) -> Bool {
        isDeleted(id, legacy: legacyDeleted())
    }

    /// legacy: 예전 목록 파일(deleted.json)을 읽은 값. 여러 경기를 볼 때는 한 번만 읽어 넘긴다
    private func isDeleted(_ id: UUID, legacy: Set<UUID>) -> Bool {
        FileManager.default.fileExists(atPath: deletedMarkerURL(for: id).path) || legacy.contains(id)
    }

    /// 예전 형식으로 지운 경기 id (파일이 없거나 읽을 수 없으면 빈 집합)
    private func legacyDeleted() -> Set<UUID> {
        guard let data = try? Data(contentsOf: legacyDeletedURL),
              let legacy = try? JSONDecoder().decode(Set<UUID>.self, from: data) else { return [] }
        return legacy
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
        guard !isDeleted(bundle.id) else { return false }
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
                && file.lastPathComponent != Self.notesFileName && !file.lastPathComponent.hasSuffix(Self.summarySuffix) {
                let stem = String(file.lastPathComponent.dropLast(".json".count))
                guard summaries[stem]?.isCurrent != true else { continue }
                if let bundle = try? decoder.decode(MatchBundle.self, from: Data(contentsOf: file)) {
                    // 그사이 경기 수신(upsert)이 최신 요약을 썼으면 옛 기록으로 만든 요약으로 덮지 않는다 (M7a 리뷰)
                    if let fresh = try? decoder.decode(MatchSummary.self, from: Data(contentsOf: summaryURL(for: bundle.id))),
                       fresh.isCurrent {
                        summaries[stem] = fresh
                        continue
                    }
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
            .filter { $0.pathExtension == "json" && !$0.lastPathComponent.hasSuffix(Self.summarySuffix) && $0.lastPathComponent != "deleted.json" && $0.lastPathComponent != Self.notesFileName }
            .compactMap { try? decoder.decode(MatchBundle.self, from: Data(contentsOf: $0)) }
            .sorted { $0.record.createdAt > $1.record.createdAt }
    }

    /// 워치가 물은 경기 중 이미 받은 경기: 저장돼 있거나 사용자가 지운 경기 (지운 경기도 받을 때 확인을 보낸다)
    public func received(among ids: [UUID]) -> [UUID] {
        // 예전 목록 파일은 ID마다 다시 읽지 않고 한 번만 읽는다 (리뷰)
        let legacy = legacyDeleted()
        return ids.filter { FileManager.default.fileExists(atPath: url(for: $0).path) || isDeleted($0, legacy: legacy) }
    }

    public func bundle(id: UUID) throws -> MatchBundle? {
        let file = url(for: id)
        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
        return try JSONDecoder().decode(MatchBundle.self, from: Data(contentsOf: file))
    }

    public func delete(id: UUID) throws {
        // 지움 표시를 먼저 쓴다: 표시를 못 쓰면(디스크 부족 등) 기록도 지우지 않는다 — 기록만 지워지면 워치가 다시 보낼 때 되살아났다 (리뷰)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data().write(to: deletedMarkerURL(for: id), options: .atomic)
        for file in [url(for: id), summaryURL(for: id)] where FileManager.default.fileExists(atPath: file.path) {
            try FileManager.default.removeItem(at: file)
        }
        // 노트는 기록과 함께 지운다 (노트 파일이 없거나 깨졌으면 할 일 없음)
        if var notes = readNotes(), notes.removeValue(forKey: id) != nil {
            try writeNotes(notes)
        }
    }

    /// 지운 경기를 되돌린다 (목록의 [되돌리기], 안드로이드 `MatchLibrary.restore`와 같다).
    /// 지움 표시(<id>.deleted)를 지우고 다시 저장한다. 노트는 delete가 지우므로 note를 주면 함께 되살린다.
    /// 예전 형식 목록(deleted.json)은 읽기만 하므로 새로 지운 경기는 거기에 없다
    public func restore(_ bundle: MatchBundle, note: MatchNote? = nil) throws {
        let marker = deletedMarkerURL(for: bundle.id)
        if FileManager.default.fileExists(atPath: marker.path) {
            try FileManager.default.removeItem(at: marker)
        }
        try upsert(bundle)
        if let note { try saveNote(note, id: bundle.id) }
    }
}
