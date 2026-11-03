import Foundation

/// 경기마다 폰에서 적는 메모 (대회명·장소·팀 색). 폰 전용이라 워치로는 보내지 않는다.
public struct MatchNote: Codable, Equatable, Sendable {
    public var competition: String?
    public var venue: String?
    /// 이 경기에서 바꾼 팀 색 (없으면 기록의 색). 예전 notes.json에는 이 키가 없다
    public var colors: TeamColors?
    /// 이 경기만 보고서에 넣을 것 (없으면 설정의 기본값)
    public var reportSections: ReportSections?
    /// 이벤트 ID → 카드 사유. 비면 JSON에서 키를 생략한다
    public var cardReasons: [String: CardReason]

    public init(competition: String? = nil, venue: String? = nil, colors: TeamColors? = nil,
                reportSections: ReportSections? = nil, cardReasons: [String: CardReason] = [:]) {
        self.competition = competition
        self.venue = venue
        self.colors = colors
        self.reportSections = reportSections
        self.cardReasons = cardReasons
    }

    public var isEmpty: Bool {
        competition == nil && venue == nil && colors == nil && reportSections == nil && cardReasons.isEmpty
    }

    private enum CodingKeys: String, CodingKey {
        case competition, venue, colors, reportSections, cardReasons
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            competition: try c.decodeIfPresent(String.self, forKey: .competition),
            venue: try c.decodeIfPresent(String.self, forKey: .venue),
            colors: try c.decodeIfPresent(TeamColors.self, forKey: .colors),
            reportSections: try c.decodeIfPresent(ReportSections.self, forKey: .reportSections),
            cardReasons: try c.decodeIfPresent([String: CardReason].self, forKey: .cardReasons) ?? [:])
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(competition, forKey: .competition)
        try c.encodeIfPresent(venue, forKey: .venue)
        try c.encodeIfPresent(colors, forKey: .colors)
        try c.encodeIfPresent(reportSections, forKey: .reportSections)
        if !cardReasons.isEmpty { try c.encode(cardReasons, forKey: .cardReasons) }
    }

    /// 경기 정보 편집기에서 고른 값으로 만든 노트. 고른 네 색이 기록의 색과 같으면 덮어쓰지 않는다 (colors nil).
    /// 두 번째 색이 주 색과 같으면 없는 것으로 정리한 뒤 비교한다
    /// `keeping`을 주면 그 노트의 카드 사유·넣을 것은 그대로 가져온다
    public static func edited(competition: String?, venue: String?, colors: TeamColors, recorded: TeamColors,
                              keeping existing: MatchNote? = nil) -> MatchNote {
        let picked = colors.normalized
        return MatchNote(competition: competition, venue: venue,
                         colors: picked.hexes == recorded.normalized.hexes ? nil : picked,
                         reportSections: existing?.reportSections, cardReasons: existing?.cardReasons ?? [:])
    }
}

extension TeamColors {
    /// 두 번째 색이 주 색과 같으면 없앤 네 색 (MatchSetup 정리 규칙)
    var normalized: TeamColors { TeamColors(setup: applied(to: MatchSetup())) }

    /// 비교용 네 hex (색 고르기 창의 값은 소수점이 조금 달라도 같은 색이다)
    var hexes: [String?] { [home.hex, away.hex, home2?.hex, away2?.hex] }
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

    /// 모든 경기의 노트 (목록 요약에 팀 색을 합칠 때). 파일이 없거나 깨졌으면 빈 사전
    public func notes() -> [UUID: MatchNote] {
        readNotes() ?? [:]
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
