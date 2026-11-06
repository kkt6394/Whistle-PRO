public enum AlertKind: String, Sendable {
    case warningBeforeEnd, periodEnd, breakEndingSoon, breakEnd, sinBinEnd
    /// 두 번째 일시 퇴장이 끝남 (그 선수는 출전 금지, 교체로 채울 수 있음)
    case secondSinBinEnd
}

public struct MatchAlert: Equatable, Sendable {
    public var kind: AlertKind
    public var at: Double
}

extension MatchState {
    /// now 이후에 울려야 할 알림(시각순). 진동은 앱이 실행한다.
    /// warningBeforeEnd: 종료 N초 전 예고 (0이면 끔, 기간 길이 이상이면 생략)
    /// breakWarningBeforeEnd: 휴식 종료 N초 전 예고 (0이면 끔, 휴식 길이 이상이면 생략)
    public func alerts(after now: Double, warningBeforeEnd: Int, breakWarningBeforeEnd: Int) -> [MatchAlert] {
        var result: [MatchAlert] = []
        switch phase {
        case .period:
            guard let p = periods.last else { break }
            let end = p.startedAt + Double(p.nominalLength)
            if warningBeforeEnd > 0 && warningBeforeEnd < p.nominalLength {
                result.append(MatchAlert(kind: .warningBeforeEnd, at: end - Double(warningBeforeEnd)))
            }
            result.append(MatchAlert(kind: .periodEnd, at: end))
            result += activeSinBins(at: now).map {
                MatchAlert(kind: $0.isSecond ? .secondSinBinEnd : .sinBinEnd, at: now + $0.remainingSeconds)
            }
        case .interval(let after):
            guard let end = periods.last?.endedAt else { break }
            let length = intervalLength(after: after)
            guard length > 0 else { break }
            if breakWarningBeforeEnd > 0 && breakWarningBeforeEnd < length {
                result.append(MatchAlert(kind: .breakEndingSoon, at: end + Double(length - breakWarningBeforeEnd)))
            }
            result.append(MatchAlert(kind: .breakEnd, at: end + Double(length)))
        case .notStarted, .preExtraTime, .penalties, .finished:
            break
        }
        return result.filter { $0.at > now }.sorted { $0.at < $1.at }
    }

    public func nextAlert(after now: Double, warningBeforeEnd: Int, breakWarningBeforeEnd: Int) -> MatchAlert? {
        alerts(after: now, warningBeforeEnd: warningBeforeEnd, breakWarningBeforeEnd: breakWarningBeforeEnd).first
    }
}
