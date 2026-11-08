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

/// SNS 카드(360×640pt) 자리 (critique 11-09, 사용자 시안 B). 경기장·히트맵은 로고·날짜 아래와 팀 줄 위 사이 칸에만 들어가
/// 경기장 크기·모양이 달라도 글자와 겹치지 않는다. 아래에는 팀 → 점수 → 골 한 줄 → 숫자 셋. 안드로이드 `SNSCardLayout`과 같은 값
public enum SNSCardLayout {
    public static let width = 360.0
    public static let height = 640.0
    /// 경기장 칸 위·아래 (pt). 위는 스토리가 덮는 84pt + 로고·날짜, 아래는 팀 줄 위
    public static let boxTop = 152.0
    public static let boxBottom = 415.0
    /// 골 한 줄에 넣는 최대 개수 (넘으면 앞 4개 + "+N")
    public static let maximumGoals = 5

    /// 골·자책골 줄만, 시간순. 많으면 앞 (최대-1)개와 나머지 개수
    public static func goals(_ lines: [ReportLine]) -> (shown: [ReportLine], more: Int) {
        let goals = lines.filter { $0.kind == .goal || $0.kind == .ownGoal }
        guard goals.count > maximumGoals else { return (goals, 0) }
        return (Array(goals.prefix(maximumGoals - 1)), goals.count - (maximumGoals - 1))
    }

    /// 위성 사진에서 경기장(+골라인 쪽 여백)이 들어갈 세로 띠와 가운데 (화면 높이 비율)
    public static var satelliteBand: Double { (boxBottom - boxTop) / height }
    public static var satelliteCenterY: Double { (boxTop + boxBottom) / 2 / height }

    /// 일반 지도 바탕: 히트맵 범위(남북·동서 m, 둘레 30%·최소 90 m)를 칸 안에 넣는 1pt당 m와,
    /// 히트맵 가운데가 칸 가운데에 오도록 지도 가운데를 남쪽으로 옮길 거리(m)
    public static func mapFrame(northSouthMeters: Double, eastWestMeters: Double) -> (metersPerPoint: Double, southShift: Double) {
        let northSouth = max(northSouthMeters * 1.3, 90)
        let eastWest = max(eastWestMeters * 1.3, 90)
        let metersPerPoint = max(eastWest / width, northSouth / (boxBottom - boxTop))
        return (metersPerPoint, (height / 2 - (boxTop + boxBottom) / 2) * metersPerPoint)
    }
}
