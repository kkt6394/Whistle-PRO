import Testing
import RefereeCore
@testable import RefereeSession
@testable import WhistleDesign

struct KickMarksTests {
    @Test func beforeFirstKickAllPending() {
        let rows = KickMarks.rows(home: [], away: [], perTeam: 5, limit: 7)
        #expect(rows.home == Array(repeating: .pending, count: 5))
        #expect(rows.away == Array(repeating: .pending, count: 5))
    }

    /// 서든데스: 두 팀 줄은 같은 킥 순번에서 잘라 같은 열이 같은 순번이다 (백로그: 팀마다 따로 잘라 어긋났다)
    @Test func rowsShareStartInSuddenDeath() {
        let home = [true, true, false, true, true, true, true, true]   // 8번째까지
        let away = [true, false, true, true, true, true, true]         // 7번째까지
        let rows = KickMarks.rows(home: home, away: away, perTeam: 5, limit: 7)
        // 8번까지 찼으니 2~8번째 킥을 보인다. 원정은 8번째가 아직 남음
        #expect(rows.home == [.scored, .missed, .scored, .scored, .scored, .scored, .scored])
        #expect(rows.away == [.missed, .scored, .scored, .scored, .scored, .scored, .pending])
    }

    @Test func rowsPadToRegulationKicks() {
        let rows = KickMarks.rows(home: [true, false], away: [true], perTeam: 5, limit: 7)
        #expect(rows.home == [.scored, .missed, .pending, .pending, .pending])
        #expect(rows.away == [.scored, .pending, .pending, .pending, .pending])
    }

    @Test func rowsWithZeroOrNegativeLimitAreEmpty() {
        for limit in [0, -1] {
            let rows = KickMarks.rows(home: [true], away: [], perTeam: 5, limit: limit)
            #expect(rows.home.isEmpty && rows.away.isEmpty)
        }
    }

    @Test func hiddenCountOnlyWhenLongerThanLimit() {
        #expect(KickMarks.hiddenCount(home: [true, false], away: [true], perTeam: 5, limit: 5) == 0)
        let eight = Array(repeating: true, count: 8)
        #expect(KickMarks.hiddenCount(home: eight, away: Array(eight.dropLast()), perTeam: 5, limit: 6) == 2)
    }

    /// 40mm(폭 134, 점 14)는 6칸, 49mm(폭 177)는 상한 7칸, 아주 좁아도 1칸
    @Test func fittingFollowsWidth() {
        #expect(KickMarks.fitting(width: 134, mark: 14, cap: 7) == 6)
        #expect(KickMarks.fitting(width: 177, mark: 14, cap: 7) == 7)
        #expect(KickMarks.fitting(width: 10, mark: 14, cap: 7) == 1)
    }
}
