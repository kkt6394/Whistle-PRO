import Foundation
import RefereeCore

/// 제출용 보고서에 넣을 것 (끄면 그 부분을 뺀다). 키가 없으면 켜진 것으로 읽는다.
public struct ReportSections: Codable, Equatable, Sendable {
    public var substitutions: Bool
    public var totals: Bool
    public var periodTimes: Bool
    public var format: Bool
    public var timeline: Bool
    public var signatures: Bool
    public var teamSignatures: Bool

    public init(substitutions: Bool = true, totals: Bool = true, periodTimes: Bool = true, format: Bool = true,
                timeline: Bool = true, signatures: Bool = true, teamSignatures: Bool = true) {
        self.substitutions = substitutions
        self.totals = totals
        self.periodTimes = periodTimes
        self.format = format
        self.timeline = timeline
        self.signatures = signatures
        self.teamSignatures = teamSignatures
    }

    /// 전부 넣는 기본값
    public static let standard = ReportSections()

    private enum CodingKeys: String, CodingKey {
        case substitutions, totals, periodTimes, format, timeline, signatures, teamSignatures
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            substitutions: try c.decodeIfPresent(Bool.self, forKey: .substitutions) ?? true,
            totals: try c.decodeIfPresent(Bool.self, forKey: .totals) ?? true,
            periodTimes: try c.decodeIfPresent(Bool.self, forKey: .periodTimes) ?? true,
            format: try c.decodeIfPresent(Bool.self, forKey: .format) ?? true,
            timeline: try c.decodeIfPresent(Bool.self, forKey: .timeline) ?? true,
            signatures: try c.decodeIfPresent(Bool.self, forKey: .signatures) ?? true,
            teamSignatures: try c.decodeIfPresent(Bool.self, forKey: .teamSignatures) ?? true)
    }
}

/// 카드 사유 (IFAB 경기 규칙의 경고·퇴장 사유). 표시 문구는 앱이 rawValue로 찾는다.
public enum CardReason: String, Codable, CaseIterable, Sendable {
    case cautionUnsporting = "caution.unsporting"
    case cautionDissent = "caution.dissent"
    case cautionPersistent = "caution.persistent"
    case cautionDelay = "caution.delay"
    case cautionDistance = "caution.distance"
    case cautionEntering = "caution.entering"
    case cautionLeaving = "caution.leaving"
    case sendOffSeriousFoul = "sendOff.seriousFoul"
    case sendOffViolent = "sendOff.violent"
    case sendOffSpitting = "sendOff.spitting"
    case sendOffHandball = "sendOff.handball"
    case sendOffDogso = "sendOff.dogso"
    case sendOffOffensive = "sendOff.offensive"
    case sendOffSecondCaution = "sendOff.secondCaution"

    public static let cautions: [CardReason] = [
        .cautionUnsporting, .cautionDissent, .cautionPersistent, .cautionDelay,
        .cautionDistance, .cautionEntering, .cautionLeaving,
    ]
    public static let sendOffs: [CardReason] = [
        .sendOffSeriousFoul, .sendOffViolent, .sendOffSpitting, .sendOffHandball,
        .sendOffDogso, .sendOffOffensive, .sendOffSecondCaution,
    ]
}

/// 보고서 머리에서 비어 있는 항목 (앱이 "빠진 정보" 안내를 만든다)
public enum ReportMissing: Equatable, Sendable {
    case referee, competition, venue
}

/// 시간순 줄 목록의 한 줄
public enum TimelineRow: Equatable, Sendable {
    case event(ReportLine)
    /// 끝난 기간: 라벨과 그 기간 끝의 누적 점수
    case periodEnd(label: String, score: TeamPair<Int>)
    case shootout(TeamPair<Int>)
}

/// 경기 결과
public enum ReportResult: Equatable, Sendable {
    case win(Team, shootout: TeamPair<Int>?)
    case draw
    case unfinished(ReportOutcome)
}
