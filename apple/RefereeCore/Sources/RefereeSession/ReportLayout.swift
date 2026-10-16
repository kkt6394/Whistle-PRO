import RefereeCore

/// 팀별 합계 (제출용 보고서 아래 칸)
public struct ReportTotals: Equatable, Sendable {
    /// 골 + 자책골 (자책골은 득점한 팀에 센다)
    public var goals: Int
    /// 꺼낸 옐로: 경고 누적 퇴장도 옐로를 한 장 더 꺼낸다
    public var yellows: Int
    /// 경고 누적 퇴장 + 레드 + 일시 퇴장
    public var dismissals: Int
    public var substitutions: Int
}

/// 두 열 보고서의 한 줄: 홈 칸과 원정 칸 (없으면 비운다)
public struct ReportColumnRow: Equatable, Sendable {
    public var home: ReportLine?
    public var away: ReportLine?
}

extension ReportLine {
    /// 이 줄이 속하는 열. `team`은 기록한 팀이라 자책골은 자책한 팀이다 → 득점한 팀(상대)의 열.
    var columnTeam: Team { kind == .ownGoal ? team.opponent : team }
}

extension ReportTotals {
    init(lines: [ReportLine]) {
        goals = lines.filter { $0.kind == .goal || $0.kind == .ownGoal }.count
        yellows = lines.filter { $0.kind == .yellow || $0.kind == .secondYellow }.count
        dismissals = lines.filter { $0.kind == .secondYellow || $0.kind == .red || $0.kind == .sinBin }.count
        substitutions = lines.filter { $0.kind == .substitution }.count
    }
}

extension MatchReport {
    /// `timeline`을 팀별로 나눠 i번째끼리 묶는다. 길이는 긴 쪽.
    public var columnRows: [ReportColumnRow] {
        let home = timeline.filter { $0.columnTeam == .home }
        let away = timeline.filter { $0.columnTeam == .away }
        return (0..<max(home.count, away.count)).map { i in
            ReportColumnRow(home: i < home.count ? home[i] : nil, away: i < away.count ? away[i] : nil)
        }
    }
}

/// SNS 카드의 이벤트 칩: 8개까지 보이고 넘으면 7개 + "+N"
public enum SNSCardChips {
    public static let maximum = 8

    public static func split(_ lines: [ReportLine]) -> (shown: [ReportLine], more: Int) {
        guard lines.count > maximum else { return (lines, 0) }
        return (Array(lines.prefix(maximum - 1)), lines.count - (maximum - 1))
    }
}
