import Foundation
import Testing
import RefereeCore
@testable import RefereeSession

struct TeamColorsTests {
    @Test func appliedOverridesAllFourAndDropsSameSecond() {
        let preset = MatchSetup(homeName: "FC 서울", awayName: "수원 FC", homeColor: .red, awayColor: .blue)
        let colors = TeamColors(home: .white, away: .black, home2: .white, away2: nil)
        let result = colors.applied(to: preset)
        #expect(result.homeColor == .white)
        #expect(result.awayColor == .black)
        #expect(result.homeColor2 == nil)   // 주색과 같아 정리
        #expect(result.awayColor2 == nil)
        #expect(result.homeName == "FC 서울")
        #expect(result.rules == .fifaStandard)
    }

    @Test func initFromSetupAndSettingSecond() {
        let setup = MatchSetup(homeColor: .red, awayColor: .blue, homeColor2: .yellow)
        let colors = TeamColors(setup: setup)
        #expect(colors == TeamColors(home: .red, away: .blue, home2: .yellow, away2: nil))
        #expect(colors.setting(.away, primary: .green).away == .green)
        #expect(colors.setting(.away, second: .white).away2 == .white)
    }

    @Test func settingSecondNoneClears() {
        let colors = TeamColors(home: .red, away: .blue, home2: .yellow, away2: .white)
        let result = colors.setting(.home, second: nil)
        #expect(result == TeamColors(home: .red, away: .blue, home2: nil, away2: .white))
    }

    @Test func wheelColors() {
        #expect(TeamColor.wheel(step: 0, level: 2).hex == "#FF0000")
        #expect(TeamColor.wheel(step: 12, level: 2).hex == "#00FF00")
        #expect(TeamColor.wheel(step: 24, level: 2).hex == "#0000FF")
        #expect(TeamColor.wheel(step: 36, level: 2) == TeamColor.wheel(step: 0, level: 2))
        #expect(TeamColor.wheel(step: 5, level: 0).hex == "#FFFFFF")
        #expect(TeamColor.wheel(step: 5, level: 4).hex == "#000000")
        #expect(TeamColor.wheel(step: 0, level: 1).hex == "#FF8080")
        #expect(TeamColor.wheel(step: 0, level: 3).hex == "#8C0000")
        #expect(TeamColor.wheelSteps == 36 && TeamColor.wheelLevels == 5)
    }

    /// 색상환 시작 칸: 지금 색에 가장 가까운 색상 (무채색은 0 = 빨강)
    @Test func wheelStepNearestHue() {
        #expect(TeamColor(hex: "#FF0000")?.wheelStep == 0)
        #expect(TeamColor(hex: "#00FF00")?.wheelStep == 12)
        #expect(TeamColor(hex: "#0000FF")?.wheelStep == 24)
        #expect(TeamColor.white.wheelStep == 0)
        #expect(TeamColor.black.wheelStep == 0)
        // 355° 근처는 36이 아니라 0으로 감긴다
        #expect(TeamColor(hex: "#FF0015")?.wheelStep == 0)
        for s in [0, 1, 5, 11, 17, 23, 29, 35] {
            for level in 1...3 {
                #expect(TeamColor.wheel(step: s, level: level).wheelStep == s)
            }
        }
    }

    @Test func jsonOmitsMissingSecond() throws {
        let colors = TeamColors(home: .red, away: .blue, home2: .white, away2: nil)
        let json = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(colors)) as? [String: Any])
        #expect(json["home"] as? String == "#FF3B30")
        #expect(json["home2"] as? String == "#FFFFFF")
        #expect(json["away2"] == nil)
    }
}
