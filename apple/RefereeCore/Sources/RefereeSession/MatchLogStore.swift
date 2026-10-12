import Foundation

public protocol MatchLogStore: Sendable {
    /// 진행 중인 경기. 없으면 nil.
    func loadActive() throws -> MatchRecord?
    func saveActive(_ record: MatchRecord) throws
    /// 경기를 보관함으로 옮기고 진행 중 기록을 지운다.
    func finishActive(_ record: MatchRecord) throws
    /// 보관된 경기 (오래된 순)
    func archived() throws -> [MatchRecord]
}

/// 파일 저장소. directory/active.json 에 진행 중 경기, directory/finished/<id>.json 에 끝난 경기를 둔다.
public struct FileMatchLogStore: MatchLogStore {
    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    private var activeURL: URL { directory.appendingPathComponent("active.json") }
    private var archiveDirectory: URL { directory.appendingPathComponent("finished", isDirectory: true) }

    /// 읽을 수 없는 파일은 지우지 않고 active-corrupt-<uuid>.json 으로 옮긴 뒤 nil을 돌려준다.
    public func loadActive() throws -> MatchRecord? {
        guard FileManager.default.fileExists(atPath: activeURL.path) else { return nil }
        let data = try Data(contentsOf: activeURL)
        do {
            return try JSONDecoder().decode(MatchRecord.self, from: data)
        } catch {
            let quarantine = directory.appendingPathComponent("active-corrupt-\(UUID().uuidString).json")
            try FileManager.default.moveItem(at: activeURL, to: quarantine)
            return nil
        }
    }

    public func saveActive(_ record: MatchRecord) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(record).write(to: activeURL, options: .atomic)
    }

    public func finishActive(_ record: MatchRecord) throws {
        try FileManager.default.createDirectory(at: archiveDirectory, withIntermediateDirectories: true)
        let url = archiveDirectory.appendingPathComponent("\(record.id.uuidString).json")
        try JSONEncoder().encode(record).write(to: url, options: .atomic)
        if FileManager.default.fileExists(atPath: activeURL.path) {
            try FileManager.default.removeItem(at: activeURL)
        }
    }

    public func archived() throws -> [MatchRecord] {
        guard FileManager.default.fileExists(atPath: archiveDirectory.path) else { return [] }
        let files = try FileManager.default.contentsOfDirectory(at: archiveDirectory, includingPropertiesForKeys: nil)
        let decoder = JSONDecoder()
        return files
            .filter { $0.pathExtension == "json" }
            .compactMap { try? decoder.decode(MatchRecord.self, from: Data(contentsOf: $0)) }
            .sorted { $0.createdAt < $1.createdAt }
    }
}
