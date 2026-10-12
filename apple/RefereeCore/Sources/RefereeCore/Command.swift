import Foundation

public enum CardKind: String, Codable, Sendable {
    case yellow, red, sinBin
}

/// 경기 이벤트 내용. 등번호는 모두 선택 입력이다.
public enum EventPayload: Equatable, Sendable {
    case goal(team: Team, number: Int?, ownGoal: Bool)
    case card(team: Team, number: Int?, kind: CardKind)
    case substitution(team: Team, numberOut: Int?, numberIn: Int?)
    case penaltyKick(team: Team, number: Int?, scored: Bool)

    /// 자책골이면 자책한 선수의 팀이다.
    public var team: Team {
        switch self {
        case .goal(let team, _, _), .card(let team, _, _),
             .substitution(let team, _, _), .penaltyKick(let team, _, _):
            team
        }
    }

    var typeName: String {
        switch self {
        case .goal: "goal"
        case .card: "card"
        case .substitution: "substitution"
        case .penaltyKick: "penaltyKick"
        }
    }

    /// "goal" → "recordGoal"
    var recordCommandType: String {
        "record" + typeName.prefix(1).uppercased() + typeName.dropFirst()
    }

    /// "recordGoal" → "goal". record로 시작하지 않으면 nil.
    static func typeName(forRecordCommand type: String) -> String? {
        guard type.hasPrefix("record") else { return nil }
        let rest = type.dropFirst("record".count)
        guard let first = rest.first else { return nil }
        return first.lowercased() + rest.dropFirst()
    }
}

public enum CommandKind: Equatable, Sendable {
    case setKickoffTeam(Team)
    case startPeriod
    case endPeriod
    case adjustPeriodLength(Int)
    case toggleStoppage
    case setShootoutFirstTeam(Team)
    case recordEvent(EventPayload)
    case editEvent(id: String, EventPayload)
    case deleteEvent(id: String)
    case undo
    case abandonMatch(reason: String)
}

public struct Command: Equatable, Sendable {
    public var id: String
    /// Unix epoch 초
    public var timestamp: Double
    public var kind: CommandKind

    public init(id: String = UUID().uuidString, timestamp: Double, kind: CommandKind) {
        self.id = id
        self.timestamp = timestamp
        self.kind = kind
    }
}

extension EventPayload: Codable {
    private enum TypeKey: String, CodingKey { case type }
    private enum FieldKeys: String, CodingKey { case team, number, ownGoal, kind, numberOut, numberIn, scored }

    public init(from decoder: Decoder) throws {
        let type = try decoder.container(keyedBy: TypeKey.self).decode(String.self, forKey: .type)
        try self.init(type: type, fieldsFrom: decoder)
    }

    /// 같은 디코더에서 type 외의 필드를 읽는다. Command의 평평한 JSON에서도 재사용한다.
    init(type: String, fieldsFrom decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: FieldKeys.self)
        let team = try c.decode(Team.self, forKey: .team)
        let number = try c.decodeIfPresent(Int.self, forKey: .number)
        switch type {
        case "goal":
            self = .goal(team: team, number: number, ownGoal: try c.decodeIfPresent(Bool.self, forKey: .ownGoal) ?? false)
        case "card":
            self = .card(team: team, number: number, kind: try c.decode(CardKind.self, forKey: .kind))
        case "substitution":
            self = .substitution(
                team: team,
                numberOut: try c.decodeIfPresent(Int.self, forKey: .numberOut),
                numberIn: try c.decodeIfPresent(Int.self, forKey: .numberIn))
        case "penaltyKick":
            self = .penaltyKick(team: team, number: number, scored: try c.decode(Bool.self, forKey: .scored))
        default:
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "알 수 없는 이벤트: \(type)"))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var t = encoder.container(keyedBy: TypeKey.self)
        try t.encode(typeName, forKey: .type)
        try encodeFields(to: encoder)
    }

    func encodeFields(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: FieldKeys.self)
        try c.encode(team, forKey: .team)
        switch self {
        case .goal(_, let number, let ownGoal):
            try c.encodeIfPresent(number, forKey: .number)
            try c.encode(ownGoal, forKey: .ownGoal)
        case .card(_, let number, let kind):
            try c.encodeIfPresent(number, forKey: .number)
            try c.encode(kind, forKey: .kind)
        case .substitution(_, let numberOut, let numberIn):
            try c.encodeIfPresent(numberOut, forKey: .numberOut)
            try c.encodeIfPresent(numberIn, forKey: .numberIn)
        case .penaltyKick(_, let number, let scored):
            try c.encodeIfPresent(number, forKey: .number)
            try c.encode(scored, forKey: .scored)
        }
    }
}

extension Command: Codable {
    private enum CodingKeys: String, CodingKey { case id, t, type, team, seconds, reason, eventId, event }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        timestamp = try c.decode(Double.self, forKey: .t)
        let type = try c.decode(String.self, forKey: .type)
        switch type {
        case "setKickoffTeam": kind = .setKickoffTeam(try c.decode(Team.self, forKey: .team))
        case "startPeriod": kind = .startPeriod
        case "endPeriod": kind = .endPeriod
        case "adjustPeriodLength": kind = .adjustPeriodLength(try c.decode(Int.self, forKey: .seconds))
        case "toggleStoppage": kind = .toggleStoppage
        case "setShootoutFirstTeam": kind = .setShootoutFirstTeam(try c.decode(Team.self, forKey: .team))
        case "editEvent":
            kind = .editEvent(id: try c.decode(String.self, forKey: .eventId), try c.decode(EventPayload.self, forKey: .event))
        case "deleteEvent": kind = .deleteEvent(id: try c.decode(String.self, forKey: .eventId))
        case "undo": kind = .undo
        case "abandonMatch": kind = .abandonMatch(reason: try c.decodeIfPresent(String.self, forKey: .reason) ?? "")
        default:
            guard let eventType = EventPayload.typeName(forRecordCommand: type) else {
                throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "알 수 없는 명령: \(type)")
            }
            kind = .recordEvent(try EventPayload(type: eventType, fieldsFrom: decoder))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(timestamp, forKey: .t)
        switch kind {
        case .setKickoffTeam(let team):
            try c.encode("setKickoffTeam", forKey: .type)
            try c.encode(team, forKey: .team)
        case .startPeriod:
            try c.encode("startPeriod", forKey: .type)
        case .endPeriod:
            try c.encode("endPeriod", forKey: .type)
        case .adjustPeriodLength(let seconds):
            try c.encode("adjustPeriodLength", forKey: .type)
            try c.encode(seconds, forKey: .seconds)
        case .toggleStoppage:
            try c.encode("toggleStoppage", forKey: .type)
        case .setShootoutFirstTeam(let team):
            try c.encode("setShootoutFirstTeam", forKey: .type)
            try c.encode(team, forKey: .team)
        case .recordEvent(let payload):
            try c.encode(payload.recordCommandType, forKey: .type)
            try payload.encodeFields(to: encoder)
        case .editEvent(let eventId, let payload):
            try c.encode("editEvent", forKey: .type)
            try c.encode(eventId, forKey: .eventId)
            try c.encode(payload, forKey: .event)
        case .deleteEvent(let eventId):
            try c.encode("deleteEvent", forKey: .type)
            try c.encode(eventId, forKey: .eventId)
        case .undo:
            try c.encode("undo", forKey: .type)
        case .abandonMatch(let reason):
            try c.encode("abandonMatch", forKey: .type)
            try c.encode(reason, forKey: .reason)
        }
    }
}
