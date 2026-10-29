import Foundation
import RefereeCore

/// 한 경기의 두 팀 색 넷 (주색·보조 색). 경기 설정·노트 위에 덮어쓰는 값이다.
/// JSON: {"home":"#RRGGBB","away":"#RRGGBB","home2":"#RRGGBB","away2":"#RRGGBB"}, 없는 보조 색은 키를 생략한다.
public struct TeamColors: Codable, Equatable, Sendable {
    public var home: TeamColor
    public var away: TeamColor
    public var home2: TeamColor?
    public var away2: TeamColor?

    public init(home: TeamColor, away: TeamColor, home2: TeamColor? = nil, away2: TeamColor? = nil) {
        self.home = home
        self.away = away
        self.home2 = home2
        self.away2 = away2
    }

    public init(setup: MatchSetup) {
        self.init(home: setup.homeColor, away: setup.awayColor, home2: setup.homeColor2, away2: setup.awayColor2)
    }

    /// 설정에 네 색을 넣는다. 보조 색이 주색과 같으면 정리한다
    public func applied(to setup: MatchSetup) -> MatchSetup {
        var s = setup
        s.homeColor = home
        s.awayColor = away
        s.homeColor2 = home2
        s.awayColor2 = away2
        return s.normalizedColors()
    }

    public func setting(_ team: Team, primary: TeamColor) -> TeamColors {
        var c = self
        if team == .home { c.home = primary } else { c.away = primary }
        return c
    }

    public func setting(_ team: Team, second: TeamColor?) -> TeamColors {
        var c = self
        if team == .home { c.home2 = second } else { c.away2 = second }
        return c
    }
}

extension TeamColor {
    /// 색상환 단계 수 (10°씩)와 밝기 단계 수
    public static let wheelSteps = 36
    public static let wheelLevels = 5

    /// 색상환 한 칸. step 0 = 빨강 0°, 10°씩 (범위 밖은 나머지로 감는다).
    /// level 0 흰색 · 1 순색과 흰색 반반 · 2 순색 · 3 순색×0.55 · 4 검정
    public static func wheel(step: Int, level: Int) -> TeamColor {
        let n = ((step % wheelSteps) + wheelSteps) % wheelSteps
        let h = Double(n) * 10 / 60   // 0..<6
        let sector = Int(h)
        let f = h - Double(sector)
        let rgb: (Double, Double, Double)
        switch sector {
        case 0: rgb = (1, f, 0)
        case 1: rgb = (1 - f, 1, 0)
        case 2: rgb = (0, 1, f)
        case 3: rgb = (0, 1 - f, 1)
        case 4: rgb = (f, 0, 1)
        default: rgb = (1, 0, 1 - f)
        }
        func shade(_ v: Double) -> Double {
            switch level {
            case ...0: 1
            case 1: (v + 1) / 2
            case 2: v
            case 3: v * 0.55
            default: 0
            }
        }
        return TeamColor(red: shade(rgb.0), green: shade(rgb.1), blue: shade(rgb.2))
    }
}
