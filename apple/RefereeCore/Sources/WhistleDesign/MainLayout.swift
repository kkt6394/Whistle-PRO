import CoreGraphics

/// 워치 메인·휴식 화면 배치 (B1 스펙 §3). 화면 모양 테두리 안에 위에서부터
/// 상단 띠(시스템 시각 왼쪽) · 시계 줄 · 팀 칸(홈 | 원정) · 버튼 줄을 겹치지 않게 놓는다.
/// 하한(버튼 44 · 시계 44 · 점수 30 · 일시 퇴장 13)을 먼저 지키고, 남는 높이는
/// 시계 → 점수 → 일시 퇴장 줄 수 → 버튼 순으로 나눈다.
public struct MainLayout: Equatable, Sendable {
    public static let minButtonHeight: CGFloat = 44
    public static let minClockFont: CGFloat = 44
    public static let minScoreFont: CGFloat = 30
    public static let minSinBinFont: CGFloat = 13
    /// 화면 폭 중 시스템 시각이 차지하는 오른쪽 몫 (시뮬레이터에서 잼, `apple/scripts/watch_metrics.swift`)
    public static let systemTimeShare: CGFloat = 0.35
    /// 두 팀 칸 사이 (가운데에 1pt 선)
    public static let dividerGap: CGFloat = 6

    /// 화면 끝에서 테두리 선 바깥까지
    static let edge: CGFloat = 1.5
    /// 테두리 선 안쪽에서 내용까지
    static let padding: CGFloat = 3
    static let gap: CGFloat = 3
    /// 상단 띠 높이 (위쪽 안전 영역이 없을 때)
    static let headerBand: CGFloat = 16
    /// 시계 글자 / 줄 높이, 점수 글자 / 줄 높이, 일시 퇴장 줄 높이 / 글자
    static let clockFill: CGFloat = 0.95
    static let scoreFill: CGFloat = 0.8
    static let sinBinLeading: CGFloat = 1.25
    /// "38:12" 폭 ≈ 2.8em, 점수 두 자리 + 점 ≈ 2em
    static let clockEm: CGFloat = 2.9
    static let scoreEm: CGFloat = 2.0
    static let maxClockFont: CGFloat = 56
    static let maxScoreFont: CGFloat = 40
    static let maxSinBinLines = 3
    static let maxButtonHeight: CGFloat = 52

    public var lineWidth: CGFloat
    public var border: BorderTrack
    public var content: CGRect
    public var contentRadius: CGFloat
    public var header: CGRect
    public var clock: CGRect
    public var teams: CGRect
    public var home: CGRect
    public var away: CGRect
    public var buttons: CGRect
    public var headerFontSize: CGFloat
    public var clockFontSize: CGFloat
    public var scoreFontSize: CGFloat
    public var sinBinFontSize: CGFloat
    public var scoreRowHeight: CGFloat
    public var sinBinRowHeight: CGFloat
    public var sinBinLines: Int

    public var buttonHeight: CGFloat { buttons.height }

    public init(size: CGSize, topInset: CGFloat) {
        let width = max(0, size.width)
        let height = max(0, size.height)
        let screenRadius = ScreenCorner.radius(for: CGSize(width: width, height: height))
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)

        lineWidth = min(max(width * 0.03, 4), 6.5)
        let trackInset = Self.edge + lineWidth / 2
        border = BorderTrack(rect: Self.inset(bounds, by: trackInset), cornerRadius: screenRadius - trackInset)
        let contentInset = Self.edge + lineWidth + Self.padding
        content = Self.inset(bounds, by: contentInset)
        contentRadius = min(max(0, screenRadius - contentInset), min(content.width, content.height) / 2)

        // 상단 띠: 위쪽 안전 영역이 있으면 그 높이 안에서 시스템 시각 왼쪽, 없으면 맨 위 띠
        let headerTop = content.minY
        let headerBottom = max(topInset, headerTop + Self.headerBand)
        let headerRight = topInset > 0 ? width * (1 - Self.systemTimeShare) : content.maxX
        header = Self.row(in: content, radius: contentRadius, minY: headerTop, height: headerBottom - headerTop,
                          maxX: headerRight)
        headerFontSize = header.height * 0.75

        // 하한부터 채우고 남는 높이를 나눈다
        let top = header.maxY + Self.gap
        let available = max(0, content.maxY - top - Self.gap * 2)
        let clockRowMin = Self.minClockFont / Self.clockFill
        let scoreRowMin = Self.minScoreFont / Self.scoreFill
        let sinBinRowMin = Self.minSinBinFont * Self.sinBinLeading
        let minimum = Self.minButtonHeight + clockRowMin + scoreRowMin + sinBinRowMin
        // 하한도 안 들어가면(아주 작은 화면) 모두 같은 비율로 줄인다
        let shrink = minimum > 0 ? min(1, available / minimum) : 1
        var spare = max(0, available - minimum)
        func take(_ want: CGFloat) -> CGFloat {
            let given = min(max(0, want), spare)
            spare -= given
            return given
        }

        let cellWidth = max(0, (content.width - Self.dividerGap) / 2)
        let clockCap = max(Self.minClockFont, min(Self.maxClockFont, content.width / Self.clockEm))
        clockFontSize = (Self.minClockFont + take((clockCap - Self.minClockFont) / Self.clockFill) * Self.clockFill) * shrink
        let scoreCap = max(Self.minScoreFont, min(Self.maxScoreFont, cellWidth / Self.scoreEm))
        scoreFontSize = (Self.minScoreFont + take((scoreCap - Self.minScoreFont) / Self.scoreFill) * Self.scoreFill) * shrink
        var lines = 1
        while lines < Self.maxSinBinLines, spare >= sinBinRowMin {
            spare -= sinBinRowMin
            lines += 1
        }
        sinBinLines = lines
        sinBinFontSize = Self.minSinBinFont * shrink
        let buttonHeight = (Self.minButtonHeight + take(Self.maxButtonHeight - Self.minButtonHeight)) * shrink

        scoreRowHeight = scoreFontSize / Self.scoreFill
        sinBinRowHeight = sinBinFontSize * Self.sinBinLeading
        let clockRowHeight = max(clockFontSize, Self.minClockFont * shrink) / Self.clockFill

        clock = Self.row(in: content, radius: contentRadius, minY: top, height: clockRowHeight, maxX: content.maxX)
        // 시계 줄이 위쪽 둥근 모서리에 걸리면 폭이 줄어든다. 그 폭에 다섯 글자가 줄지 않고 들어가게 한다
        clockFontSize = min(clockFontSize, clock.width / Self.clockEm)
        // 버튼 줄은 맨 아래. 양 끝이 캡슐이라 모서리 반지름이 캡슐 반지름보다 클 때만 안으로 들인다
        let buttonInset = max(0, contentRadius - buttonHeight / 2)
        buttons = CGRect(x: content.minX + buttonInset, y: content.maxY - buttonHeight,
                         width: max(0, content.width - buttonInset * 2), height: buttonHeight)
        // 팀 칸은 시계 줄과 버튼 줄 사이를 모두 쓴다 (남은 높이는 칸이 받는다)
        let teamsTop = clock.maxY + Self.gap
        teams = Self.row(in: content, radius: contentRadius, minY: teamsTop,
                         height: max(0, buttons.minY - Self.gap - teamsTop), maxX: content.maxX)
        let half = max(0, (teams.width - Self.dividerGap) / 2)
        home = CGRect(x: teams.minX, y: teams.minY, width: half, height: teams.height)
        away = CGRect(x: teams.maxX - half, y: teams.minY, width: half, height: teams.height)
    }

    private static func inset(_ rect: CGRect, by amount: CGFloat) -> CGRect {
        let dx = min(amount, rect.width / 2), dy = min(amount, rect.height / 2)
        return rect.insetBy(dx: dx, dy: dy)
    }

    /// 둥근 사각형(area, radius) 안에서 minY부터 height만큼의 줄. 위·아래 모서리에 걸리면 그만큼 좌우를 들인다.
    private static func row(in area: CGRect, radius: CGFloat, minY: CGFloat, height: CGFloat, maxX: CGFloat) -> CGRect {
        let height = max(0, height)
        let inset = max(cornerInset(area, radius, y: minY), cornerInset(area, radius, y: minY + height))
        let left = area.minX + inset
        let right = min(maxX, area.maxX - inset)
        return CGRect(x: left, y: minY, width: max(0, right - left), height: height)
    }

    /// 높이 y에서 둥근 모서리 때문에 좌우로 들여야 하는 폭
    private static func cornerInset(_ area: CGRect, _ r: CGFloat, y: CGFloat) -> CGFloat {
        let depth: CGFloat
        if y < area.minY + r {
            depth = area.minY + r - y
        } else if y > area.maxY - r {
            depth = y - (area.maxY - r)
        } else {
            return 0
        }
        return r - max(0, r * r - depth * depth).squareRoot()
    }
}
