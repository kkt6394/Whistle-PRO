import Foundation

/// 경기마다 폰에서 적는 메모 (대회명·장소). 폰 전용이라 워치로는 보내지 않는다.
public struct MatchNote: Codable, Equatable, Sendable {
    public var competition: String?
    public var venue: String?

    public init(competition: String? = nil, venue: String? = nil) {
        self.competition = competition
        self.venue = venue
    }

    public var isEmpty: Bool { competition == nil && venue == nil }
}

extension MatchLibrary {
    static let notesFileName = "notes.json"

    private var notesURL: URL { directory.appendingPathComponent(Self.notesFileName) }

    /// notes.json 전체. 파일이 없거나 깨졌으면 nil
    func readNotes() -> [UUID: MatchNote]? {
        guard let data = try? Data(contentsOf: notesURL) else { return nil }
        return try? JSONDecoder().decode([UUID: MatchNote].self, from: data)
    }

    func writeNotes(_ notes: [UUID: MatchNote]) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try JSONEncoder().encode(notes).write(to: notesURL, options: .atomic)
    }

    public func note(id: UUID) -> MatchNote? {
        readNotes()?[id]
    }

    /// 빈 노트면 항목을 지운다. 파일이 깨졌으면 새 사전으로 시작한다
    public func saveNote(_ note: MatchNote, id: UUID) throws {
        var notes = readNotes() ?? [:]
        if note.isEmpty { notes[id] = nil } else { notes[id] = note }
        try writeNotes(notes)
    }
}
