import Foundation
import RefereeCore

/// 제출용 보고서의 한 덩어리. rawValue는 저장 키라 바꾸지 않는다 (Kotlin ReportBlock.key와 같다)
public enum ReportBlock: String, Codable, CaseIterable, Sendable {
    case info, score, goals, substitutions, cautions, sendOffs, otherReport, periodTimes, format, totals, timeline, signatures

    /// 기본 순서 (2026-11-04 설계 표)
    public static let defaultOrder: [ReportBlock] = allCases
    /// 기본으로 꺼진 블록 (시간순 기록은 어느 협회 양식에도 없고 위 표와 겹친다)
    public static let defaultHidden: Set<ReportBlock> = [.timeline]
}

/// 보고서 구성: 블록 순서 + 꺼진 블록 + 팀 확인 서명.
/// `order`에는 모든 블록이 한 번씩 들어 있다 (빠진 블록은 기본 순서 자리에 끼워 넣는다).
public struct ReportSections: Codable, Equatable, Sendable {
    public private(set) var order: [ReportBlock]
    public var hidden: Set<ReportBlock>
    public var teamSignatures: Bool

    public init(order: [ReportBlock] = ReportBlock.defaultOrder, hidden: Set<ReportBlock> = ReportBlock.defaultHidden,
                teamSignatures: Bool = true) {
        self.order = Self.normalized(order)
        self.hidden = hidden
        self.teamSignatures = teamSignatures
    }

    /// 기본값 (시간순 기록만 끔)
    public static let standard = ReportSections()

    public func isOn(_ block: ReportBlock) -> Bool { !hidden.contains(block) }

    /// 켜진 블록만 순서대로
    public var visibleBlocks: [ReportBlock] { order.filter(isOn) }

    public mutating func set(_ block: ReportBlock, on: Bool) {
        if on { hidden.remove(block) } else { hidden.insert(block) }
    }

    public func setting(_ block: ReportBlock, on: Bool) -> ReportSections {
        var copy = self
        copy.set(block, on: on)
        return copy
    }

    /// SwiftUI `.onMove`와 같은 뜻 (`toOffset`은 옮기기 전 목록 기준 자리)
    public mutating func move(fromOffsets source: IndexSet, toOffset destination: Int) {
        let moving = source.sorted().map { order[$0] }
        let before = source.filter { $0 < destination }.count
        var rest = order.enumerated().filter { !source.contains($0.offset) }.map(\.element)
        rest.insert(contentsOf: moving, at: max(0, min(rest.count, destination - before)))
        order = rest
    }

    /// 한 칸씩 위(-1)·아래(+1)로 (접근성 "위로 옮기기"·"아래로 옮기기"). 끝을 넘으면 그대로
    public mutating func move(_ block: ReportBlock, by delta: Int) {
        guard let index = order.firstIndex(of: block) else { return }
        let target = index + delta
        guard order.indices.contains(target) else { return }
        order.remove(at: index)
        order.insert(block, at: target)
    }

    /// 모르는 값·겹친 값을 빼고, 빠진 블록은 기본 순서에서 바로 앞 블록 뒤에 (없으면 맨 앞에) 끼워 넣는다
    static func normalized(_ blocks: [ReportBlock]) -> [ReportBlock] {
        var result: [ReportBlock] = []
        for block in blocks where !result.contains(block) { result.append(block) }
        for (i, block) in ReportBlock.defaultOrder.enumerated() where !result.contains(block) {
            let previous = ReportBlock.defaultOrder[..<i].last(where: result.contains)
            let at = previous.flatMap { result.firstIndex(of: $0) }.map { $0 + 1 } ?? 0
            result.insert(block, at: at)
        }
        return result
    }

    /// 옛 모양(2026-11-03)의 켬/끔 키. 옛 앱(워치·폰)이 읽을 수 있게 저장할 때도 같이 적는다
    private static let legacyKeys: [(key: CodingKeys, block: ReportBlock)] = [
        (.substitutions, .substitutions), (.totals, .totals), (.periodTimes, .periodTimes),
        (.format, .format), (.timeline, .timeline), (.signatures, .signatures),
    ]

    private enum CodingKeys: String, CodingKey {
        case order, hidden, teamSignatures
        case substitutions, totals, periodTimes, format, timeline, signatures
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let teamSignatures = try c.decodeIfPresent(Bool.self, forKey: .teamSignatures) ?? true
        if c.contains(.order) || c.contains(.hidden) {
            // 모르는 블록 키는 버린다
            let keys = try c.decodeIfPresent([String].self, forKey: .order) ?? []
            let listed = keys.compactMap(ReportBlock.init(rawValue:))
            var hidden = Set((try c.decodeIfPresent([String].self, forKey: .hidden) ?? []).compactMap(ReportBlock.init(rawValue:)))
            // 목록에 없던 (새로 생긴) 블록은 기본 켬/끔
            for block in ReportBlock.allCases where !listed.contains(block) && ReportBlock.defaultHidden.contains(block) {
                hidden.insert(block)
            }
            self.init(order: listed, hidden: hidden, teamSignatures: teamSignatures)
        } else {
            // 옛 모양: 불 값을 그대로 옮기고 나머지 블록은 기본값, 순서는 기본 순서
            var hidden = ReportBlock.defaultHidden
            for (key, block) in Self.legacyKeys {
                guard let on = try c.decodeIfPresent(Bool.self, forKey: key) else { continue }
                if on { hidden.remove(block) } else { hidden.insert(block) }
            }
            self.init(hidden: hidden, teamSignatures: teamSignatures)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(order.map(\.rawValue), forKey: .order)
        try c.encode(ReportBlock.defaultOrder.filter(hidden.contains).map(\.rawValue), forKey: .hidden)
        try c.encode(teamSignatures, forKey: .teamSignatures)
        for (key, block) in Self.legacyKeys { try c.encode(isOn(block), forKey: key) }
    }
}

/// 카드 사유 (IFAB 경기규칙 2026/27 제12조 12.4, 대한축구협회 공식 번역). 표시 문구는 앱이 rawValue로 찾는다 (CardReasonText).
/// 사유를 더할 때: 여기 case와 `cautions`·`sendOffs` 목록, 앱의 CardReasonText 두 곳, apple/i18n 문구만 고치면 된다
/// (단위 테스트는 allCases를 돌며 확인한다).
public enum CardReason: String, Codable, CaseIterable, Sendable {
    case cautionDelay = "caution.delay"
    case cautionDissent = "caution.dissent"
    case cautionEntering = "caution.entering"
    case cautionDistance = "caution.distance"
    case cautionPersistent = "caution.persistent"
    case cautionUnsporting = "caution.unsporting"
    case cautionReviewArea = "caution.reviewArea"
    case cautionReviewSignal = "caution.reviewSignal"
    case sendOffHandball = "sendOff.handball"
    case sendOffHandballNonDeliberate = "sendOff.handballNonDeliberate"
    case sendOffDogso = "sendOff.dogso"
    case sendOffSeriousFoul = "sendOff.seriousFoul"
    case sendOffSpitting = "sendOff.spitting"
    case sendOffViolent = "sendOff.violent"
    case sendOffOffensive = "sendOff.offensive"
    case sendOffSecondCaution = "sendOff.secondCaution"
    case sendOffVor = "sendOff.vor"

    /// 예전에 따로 있던 값 → 지금 값 ("허락 없이 떠남"은 입장·재입장과 한 사유로 합쳤다)
    private static let legacy: [String: CardReason] = ["caution.leaving": .cautionEntering]

    /// 저장된 값 읽기 (옛 값 포함). 모르는 값이면 nil
    public init?(storedValue: String) {
        guard let reason = CardReason(rawValue: storedValue) ?? Self.legacy[storedValue] else { return nil }
        self = reason
    }

    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer().decode(String.self)
        guard let reason = CardReason(storedValue: value) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "모르는 카드 사유 \(value)"))
        }
        self = reason
    }

    /// 경고 사유 (IFAB 원문 순서 = 사유 시트 순서)
    public static let cautions: [CardReason] = [
        .cautionDelay, .cautionDissent, .cautionEntering, .cautionDistance,
        .cautionPersistent, .cautionUnsporting, .cautionReviewArea, .cautionReviewSignal,
    ]
    /// 퇴장 사유 (IFAB 원문 순서)
    public static let sendOffs: [CardReason] = [
        .sendOffHandball, .sendOffHandballNonDeliberate, .sendOffDogso, .sendOffSeriousFoul,
        .sendOffSpitting, .sendOffViolent, .sendOffOffensive, .sendOffSecondCaution, .sendOffVor,
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
