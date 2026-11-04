import Foundation

/// 경기마다 폰에서 적는 메모 (대회명·장소·팀 색·보고서 정보). 폰 전용이라 워치로는 보내지 않는다.
public struct MatchNote: Codable, Equatable, Sendable {
    public var competition: String?
    public var venue: String?
    /// 이 경기에서 바꾼 팀 색 (없으면 기록의 색). 예전 notes.json에는 이 키가 없다
    public var colors: TeamColors?
    /// 이 경기만 보고서 구성 (없으면 설정의 기본값)
    public var reportSections: ReportSections?
    /// 이벤트 ID → 카드 사유. 비면 JSON에서 키를 생략한다
    public var cardReasons: [String: CardReason]
    /// 기타 보고 사항 (여러 줄). 아래 다섯 칸은 공백을 다듬어 비면 nil로 둔다
    public var otherReport: String? { didSet { otherReport = Self.clean(otherReport) } }
    public var matchNumber: String? { didSet { matchNumber = Self.clean(matchNumber) } }
    public var assistant1: String? { didSet { assistant1 = Self.clean(assistant1) } }
    public var assistant2: String? { didSet { assistant2 = Self.clean(assistant2) } }
    public var fourthOfficial: String? { didSet { fourthOfficial = Self.clean(fourthOfficial) } }

    public init(competition: String? = nil, venue: String? = nil, colors: TeamColors? = nil,
                reportSections: ReportSections? = nil, cardReasons: [String: CardReason] = [:],
                otherReport: String? = nil, matchNumber: String? = nil, assistant1: String? = nil,
                assistant2: String? = nil, fourthOfficial: String? = nil) {
        self.competition = competition
        self.venue = venue
        self.colors = colors
        self.reportSections = reportSections
        self.cardReasons = cardReasons
        self.otherReport = Self.clean(otherReport)
        self.matchNumber = Self.clean(matchNumber)
        self.assistant1 = Self.clean(assistant1)
        self.assistant2 = Self.clean(assistant2)
        self.fourthOfficial = Self.clean(fourthOfficial)
    }

    /// 앞뒤 공백을 다듬고, 비면 nil (글 안의 줄바꿈은 그대로)
    static func clean(_ text: String?) -> String? {
        guard let value = text?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        return value
    }

    public var isEmpty: Bool {
        competition == nil && venue == nil && colors == nil && reportSections == nil && cardReasons.isEmpty
            && otherReport == nil && matchNumber == nil && assistant1 == nil && assistant2 == nil && fourthOfficial == nil
    }

    private enum CodingKeys: String, CodingKey {
        case competition, venue, colors, reportSections, cardReasons
        case otherReport, matchNumber, assistant1, assistant2, fourthOfficial
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            competition: try c.decodeIfPresent(String.self, forKey: .competition),
            venue: try c.decodeIfPresent(String.self, forKey: .venue),
            colors: try c.decodeIfPresent(TeamColors.self, forKey: .colors),
            reportSections: try c.decodeIfPresent(ReportSections.self, forKey: .reportSections),
            // 모르는 사유 값은 그 항목만 버린다 (Kotlin MatchNote.fromJson과 같다). 옛 값은 지금 값으로 읽는다
            cardReasons: (try c.decodeIfPresent([String: String].self, forKey: .cardReasons) ?? [:])
                .compactMapValues { CardReason(storedValue: $0) },
            otherReport: try c.decodeIfPresent(String.self, forKey: .otherReport),
            matchNumber: try c.decodeIfPresent(String.self, forKey: .matchNumber),
            assistant1: try c.decodeIfPresent(String.self, forKey: .assistant1),
            assistant2: try c.decodeIfPresent(String.self, forKey: .assistant2),
            fourthOfficial: try c.decodeIfPresent(String.self, forKey: .fourthOfficial))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(competition, forKey: .competition)
        try c.encodeIfPresent(venue, forKey: .venue)
        try c.encodeIfPresent(colors, forKey: .colors)
        try c.encodeIfPresent(reportSections, forKey: .reportSections)
        if !cardReasons.isEmpty { try c.encode(cardReasons, forKey: .cardReasons) }
        try c.encodeIfPresent(otherReport, forKey: .otherReport)
        try c.encodeIfPresent(matchNumber, forKey: .matchNumber)
        try c.encodeIfPresent(assistant1, forKey: .assistant1)
        try c.encodeIfPresent(assistant2, forKey: .assistant2)
        try c.encodeIfPresent(fourthOfficial, forKey: .fourthOfficial)
    }

    /// 경기 정보 편집기에서 고른 값으로 만든 노트. 고른 네 색이 기록의 색과 같으면 덮어쓰지 않는다 (colors nil).
    /// 두 번째 색이 주 색과 같으면 없는 것으로 정리한 뒤 비교한다
    /// `keeping`을 주면 그 노트의 카드 사유·보고서 구성·기타 보고 사항·경기번호·심판진은 그대로 가져온다
    public static func edited(competition: String?, venue: String?, colors: TeamColors, recorded: TeamColors,
                              keeping existing: MatchNote? = nil) -> MatchNote {
        edited(competition: competition, venue: venue, colors: colors, recorded: recorded,
               matchNumber: existing?.matchNumber, assistant1: existing?.assistant1,
               assistant2: existing?.assistant2, fourthOfficial: existing?.fourthOfficial, keeping: existing)
    }

    /// 경기번호·심판진까지 편집기에서 고친 노트 (카드 사유·보고서 구성·기타 보고 사항은 `keeping`에서 가져온다)
    public static func edited(competition: String?, venue: String?, colors: TeamColors, recorded: TeamColors,
                              matchNumber: String?, assistant1: String?, assistant2: String?, fourthOfficial: String?,
                              keeping existing: MatchNote? = nil) -> MatchNote {
        let picked = colors.normalized
        return MatchNote(competition: competition, venue: venue,
                         colors: picked.hexes == recorded.normalized.hexes ? nil : picked,
                         reportSections: existing?.reportSections, cardReasons: existing?.cardReasons ?? [:],
                         otherReport: existing?.otherReport, matchNumber: matchNumber, assistant1: assistant1,
                         assistant2: assistant2, fourthOfficial: fourthOfficial)
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
