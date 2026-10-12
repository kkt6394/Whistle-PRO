import Foundation

/// 경기 규칙 설정. 기본값은 IFAB 경기규칙(FIFA 표준)이다. 시간 단위는 초.
public struct RuleSet: Equatable, Sendable {
    public var periodCount: Int
    public var periodLength: Int
    public var halfTimeLength: Int
    public var quarterBreakLength: Int
    public var extraTime: Bool
    public var extraTimePeriodLength: Int
    public var extraTimeBreakLength: Int
    public var penalties: Bool
    public var penaltyKicksPerTeam: Int
    public var playersPerTeam: Int
    /// nil이면 최소 인원 검사를 하지 않는다.
    public var minimumPlayers: Int?
    /// nil이면 교체 무제한.
    public var substitutionLimit: Int?
    public var returnSubstitutions: Bool
    public var extraTimeExtraSubs: Int
    public var secondYellowSendOff: Bool
    public var sinBin: Bool
    public var sinBinLength: Int
    public var alternateKickoff: Bool

    public init(
        periodCount: Int = 2,
        periodLength: Int = 2700,
        halfTimeLength: Int = 900,
        quarterBreakLength: Int = 0,
        extraTime: Bool = false,
        extraTimePeriodLength: Int = 900,
        extraTimeBreakLength: Int = 60,
        penalties: Bool = false,
        penaltyKicksPerTeam: Int = 5,
        playersPerTeam: Int = 11,
        minimumPlayers: Int? = 7,
        substitutionLimit: Int? = 5,
        returnSubstitutions: Bool = false,
        extraTimeExtraSubs: Int = 0,
        secondYellowSendOff: Bool = true,
        sinBin: Bool = false,
        sinBinLength: Int = 600,
        alternateKickoff: Bool = true
    ) {
        self.periodCount = periodCount
        self.periodLength = periodLength
        self.halfTimeLength = halfTimeLength
        self.quarterBreakLength = quarterBreakLength
        self.extraTime = extraTime
        self.extraTimePeriodLength = extraTimePeriodLength
        self.extraTimeBreakLength = extraTimeBreakLength
        self.penalties = penalties
        self.penaltyKicksPerTeam = penaltyKicksPerTeam
        self.playersPerTeam = playersPerTeam
        self.minimumPlayers = minimumPlayers
        self.substitutionLimit = substitutionLimit
        self.returnSubstitutions = returnSubstitutions
        self.extraTimeExtraSubs = extraTimeExtraSubs
        self.secondYellowSendOff = secondYellowSendOff
        self.sinBin = sinBin
        self.sinBinLength = sinBinLength
        self.alternateKickoff = alternateKickoff
    }

    /// 내장 프리셋 "FIFA 표준" (수정·삭제 불가)
    public static let fifaStandard = RuleSet()
}

extension RuleSet {
    public enum Field: String, CaseIterable, CodingKey, Sendable {
        case periodCount, periodLength, halfTimeLength, quarterBreakLength
        case extraTime, extraTimePeriodLength, extraTimeBreakLength
        case penalties, penaltyKicksPerTeam
        case playersPerTeam, minimumPlayers
        case substitutionLimit, returnSubstitutions, extraTimeExtraSubs
        case secondYellowSendOff, sinBin, sinBinLength, alternateKickoff
    }

    /// 비교·표시용 문자열 값. 보고서의 "적용 규칙" 섹션에서도 쓴다.
    public func value(of field: Field) -> String {
        switch field {
        case .periodCount: "\(periodCount)"
        case .periodLength: "\(periodLength)"
        case .halfTimeLength: "\(halfTimeLength)"
        case .quarterBreakLength: "\(quarterBreakLength)"
        case .extraTime: "\(extraTime)"
        case .extraTimePeriodLength: "\(extraTimePeriodLength)"
        case .extraTimeBreakLength: "\(extraTimeBreakLength)"
        case .penalties: "\(penalties)"
        case .penaltyKicksPerTeam: "\(penaltyKicksPerTeam)"
        case .playersPerTeam: "\(playersPerTeam)"
        case .minimumPlayers: minimumPlayers.map(String.init) ?? "off"
        case .substitutionLimit: substitutionLimit.map(String.init) ?? "unlimited"
        case .returnSubstitutions: "\(returnSubstitutions)"
        case .extraTimeExtraSubs: "\(extraTimeExtraSubs)"
        case .secondYellowSendOff: "\(secondYellowSendOff)"
        case .sinBin: "\(sinBin)"
        case .sinBinLength: "\(sinBinLength)"
        case .alternateKickoff: "\(alternateKickoff)"
        }
    }

    /// 기준(기본: FIFA 표준)과 값이 다른 항목. Field 선언 순서를 따른다.
    public func deviations(from base: RuleSet = .fifaStandard) -> [Field] {
        Field.allCases.filter { value(of: $0) != base.value(of: $0) }
    }
}

public struct RuleSetError: Error, Equatable, Sendable {
    public let field: RuleSet.Field
}

extension RuleSet {
    /// 스펙 §3 RuleSet 표의 설정 범위를 검사한다.
    public func validate() throws(RuleSetError) {
        func check(_ ok: Bool, _ field: Field) throws(RuleSetError) {
            if !ok { throw RuleSetError(field: field) }
        }
        try check([2, 4].contains(periodCount), .periodCount)
        try check((60...5400).contains(periodLength), .periodLength)
        try check((0...3600).contains(halfTimeLength), .halfTimeLength)
        try check((0...1800).contains(quarterBreakLength), .quarterBreakLength)
        try check((60...1800).contains(extraTimePeriodLength), .extraTimePeriodLength)
        try check((0...900).contains(extraTimeBreakLength), .extraTimeBreakLength)
        try check((1...10).contains(penaltyKicksPerTeam), .penaltyKicksPerTeam)
        try check((3...11).contains(playersPerTeam), .playersPerTeam)
        if let minimumPlayers {
            try check((1...playersPerTeam).contains(minimumPlayers), .minimumPlayers)
        }
        if let substitutionLimit {
            try check((0...20).contains(substitutionLimit), .substitutionLimit)
        }
        try check((0...3).contains(extraTimeExtraSubs), .extraTimeExtraSubs)
        try check((60...1200).contains(sinBinLength), .sinBinLength)
    }
}

extension RuleSet: Codable {
    /// 없는 키는 FIFA 기본값을 쓴다. minimumPlayers·substitutionLimit은 명시적 null이면 끔(nil).
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Field.self)
        let d = RuleSet.fifaStandard
        func int(_ f: Field, _ fallback: Int) throws -> Int {
            try c.decodeIfPresent(Int.self, forKey: f) ?? fallback
        }
        func bool(_ f: Field, _ fallback: Bool) throws -> Bool {
            try c.decodeIfPresent(Bool.self, forKey: f) ?? fallback
        }
        func optionalInt(_ f: Field, _ fallback: Int?) throws -> Int? {
            guard c.contains(f) else { return fallback }
            return try c.decodeNil(forKey: f) ? nil : c.decode(Int.self, forKey: f)
        }
        self.init(
            periodCount: try int(.periodCount, d.periodCount),
            periodLength: try int(.periodLength, d.periodLength),
            halfTimeLength: try int(.halfTimeLength, d.halfTimeLength),
            quarterBreakLength: try int(.quarterBreakLength, d.quarterBreakLength),
            extraTime: try bool(.extraTime, d.extraTime),
            extraTimePeriodLength: try int(.extraTimePeriodLength, d.extraTimePeriodLength),
            extraTimeBreakLength: try int(.extraTimeBreakLength, d.extraTimeBreakLength),
            penalties: try bool(.penalties, d.penalties),
            penaltyKicksPerTeam: try int(.penaltyKicksPerTeam, d.penaltyKicksPerTeam),
            playersPerTeam: try int(.playersPerTeam, d.playersPerTeam),
            minimumPlayers: try optionalInt(.minimumPlayers, d.minimumPlayers),
            substitutionLimit: try optionalInt(.substitutionLimit, d.substitutionLimit),
            returnSubstitutions: try bool(.returnSubstitutions, d.returnSubstitutions),
            extraTimeExtraSubs: try int(.extraTimeExtraSubs, d.extraTimeExtraSubs),
            secondYellowSendOff: try bool(.secondYellowSendOff, d.secondYellowSendOff),
            sinBin: try bool(.sinBin, d.sinBin),
            sinBinLength: try int(.sinBinLength, d.sinBinLength),
            alternateKickoff: try bool(.alternateKickoff, d.alternateKickoff)
        )
    }

    /// 선택 항목은 nil도 null로 명시해서, 다시 디코딩할 때 기본값으로 되돌아가지 않게 한다.
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Field.self)
        try c.encode(periodCount, forKey: .periodCount)
        try c.encode(periodLength, forKey: .periodLength)
        try c.encode(halfTimeLength, forKey: .halfTimeLength)
        try c.encode(quarterBreakLength, forKey: .quarterBreakLength)
        try c.encode(extraTime, forKey: .extraTime)
        try c.encode(extraTimePeriodLength, forKey: .extraTimePeriodLength)
        try c.encode(extraTimeBreakLength, forKey: .extraTimeBreakLength)
        try c.encode(penalties, forKey: .penalties)
        try c.encode(penaltyKicksPerTeam, forKey: .penaltyKicksPerTeam)
        try c.encode(playersPerTeam, forKey: .playersPerTeam)
        try c.encode(minimumPlayers, forKey: .minimumPlayers)
        try c.encode(substitutionLimit, forKey: .substitutionLimit)
        try c.encode(returnSubstitutions, forKey: .returnSubstitutions)
        try c.encode(extraTimeExtraSubs, forKey: .extraTimeExtraSubs)
        try c.encode(secondYellowSendOff, forKey: .secondYellowSendOff)
        try c.encode(sinBin, forKey: .sinBin)
        try c.encode(sinBinLength, forKey: .sinBinLength)
        try c.encode(alternateKickoff, forKey: .alternateKickoff)
    }
}
