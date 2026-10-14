import Foundation

public protocol MatchLogStore: Sendable {
    /// 진행 중인 경기. 없으면 nil.
    func loadActive() throws -> MatchRecord?
    func saveActive(_ record: MatchRecord) throws
    /// 경기(와 같은 경기의 운동 기록)를 보관함으로 옮기고 진행 중 기록을 지운다.
    func finishActive(_ record: MatchRecord) throws
    /// 보관된 경기 (오래된 순)
    func archived() throws -> [MatchRecord]

    func saveActiveWorkout(_ log: WorkoutLog) throws
    func loadActiveWorkout() throws -> WorkoutLog?
    func archivedWorkout(for matchID: UUID) throws -> WorkoutLog?
    /// 폰이 받았다고 확인한 경기를 워치 보관함에서 지운다.
    func deleteArchived(_ matchID: UUID) throws
}

/// 파일 저장소. directory/active.json·workout-active.json 에 진행 중 기록,
/// directory/finished/<id>.json·<id>-workout.json 에 폰으로 보낼 끝난 기록,
/// directory/history/<id>.json 에 워치에서 다시 볼 최근 경기(운동 기록 없이)를 둔다.
public struct FileMatchLogStore: MatchLogStore {
    public let directory: URL
    /// 워치 "지난 경기"에 남기는 경기 수
    public static let historyLimit = 10

    public init(directory: URL) {
        self.directory = directory
    }

    private var activeURL: URL { directory.appendingPathComponent("active.json") }
    private var activeWorkoutURL: URL { directory.appendingPathComponent("workout-active.json") }
    private var archiveDirectory: URL { directory.appendingPathComponent("finished", isDirectory: true) }
    private var historyDirectory: URL { directory.appendingPathComponent("history", isDirectory: true) }

    private func archivedWorkoutURL(_ id: UUID) -> URL {
        archiveDirectory.appendingPathComponent("\(id.uuidString)-workout.json")
    }

    /// 읽을 수 없는 파일은 지우지 않고 active-corrupt-<uuid>.json 으로 옮긴 뒤 nil을 돌려준다.
    public func loadActive() throws -> MatchRecord? {
        guard FileManager.default.fileExists(atPath: activeURL.path) else { return nil }
        let data = try Data(contentsOf: activeURL)
        do {
            return try JSONDecoder().decode(MatchRecord.self, from: data)
        } catch {
            let quarantine = directory.appendingPathComponent("active-corrupt-\(UUID().uuidString).json")
            do {
                try FileManager.default.moveItem(at: activeURL, to: quarantine)
            } catch {
                // 이동이 안 되면 복사로라도 보관한 뒤 원본을 치운다 (다음 경기가 덮어쓰기 전에)
                try FileManager.default.copyItem(at: activeURL, to: quarantine)
                try? FileManager.default.removeItem(at: activeURL)
            }
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
        if let log = try? loadActiveWorkout(), log.matchID == record.id {
            try JSONEncoder().encode(log).write(to: archivedWorkoutURL(record.id), options: .atomic)
        }
        // 지난 경기 사본은 보조 기능이라 실패해도 경기 보관을 막지 않는다
        try? addToHistory(record)
        for active in [activeURL, activeWorkoutURL] where FileManager.default.fileExists(atPath: active.path) {
            try FileManager.default.removeItem(at: active)
        }
    }

    /// 워치에서 다시 볼 최근 경기 (최근 순). 폰이 받았다고 확인해도 남는다.
    public func history() throws -> [MatchRecord] {
        try records(in: historyDirectory).sorted { $0.createdAt > $1.createdAt }
    }

    private func addToHistory(_ record: MatchRecord) throws {
        try FileManager.default.createDirectory(at: historyDirectory, withIntermediateDirectories: true)
        let url = historyDirectory.appendingPathComponent("\(record.id.uuidString).json")
        try JSONEncoder().encode(record).write(to: url, options: .atomic)
        for old in try history().dropFirst(Self.historyLimit) {
            try? FileManager.default.removeItem(at: historyDirectory.appendingPathComponent("\(old.id.uuidString).json"))
        }
    }

    private func records(in folder: URL) throws -> [MatchRecord] {
        guard FileManager.default.fileExists(atPath: folder.path) else { return [] }
        let files = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
        let decoder = JSONDecoder()
        return files
            .filter { $0.pathExtension == "json" && !$0.lastPathComponent.hasSuffix("-workout.json") }
            .compactMap { try? decoder.decode(MatchRecord.self, from: Data(contentsOf: $0)) }
    }

    public func archived() throws -> [MatchRecord] {
        try records(in: archiveDirectory).sorted { $0.createdAt < $1.createdAt }
    }

    public func saveActiveWorkout(_ log: WorkoutLog) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(log).write(to: activeWorkoutURL, options: .atomic)
    }

    /// 읽을 수 없으면 nil (운동 기록은 경기 기록보다 덜 중요해서 격리하지 않는다)
    public func loadActiveWorkout() throws -> WorkoutLog? {
        guard FileManager.default.fileExists(atPath: activeWorkoutURL.path) else { return nil }
        return try? JSONDecoder().decode(WorkoutLog.self, from: Data(contentsOf: activeWorkoutURL))
    }

    public func archivedWorkout(for matchID: UUID) throws -> WorkoutLog? {
        let url = archivedWorkoutURL(matchID)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try JSONDecoder().decode(WorkoutLog.self, from: Data(contentsOf: url))
    }

    public func deleteArchived(_ matchID: UUID) throws {
        let files = [archiveDirectory.appendingPathComponent("\(matchID.uuidString).json"), archivedWorkoutURL(matchID)]
        for file in files where FileManager.default.fileExists(atPath: file.path) {
            try FileManager.default.removeItem(at: file)
        }
    }
}
