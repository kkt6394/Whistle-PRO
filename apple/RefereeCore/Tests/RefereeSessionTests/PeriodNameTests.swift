import Testing
import RefereeCore
@testable import RefereeSession

/// 기간 코드(1H·Q3·ET1)를 화면 이름으로 바꾸기 위한 구분. 한국어는 전반·후반·3쿼터·연장 전반으로 보인다.
struct PeriodNameTests {
    @Test func parsesCodesFromPeriodRef() {
        #expect(PeriodName(label: PeriodRef(.regular, 1).label(periodCount: 2)) == .half(1))
        #expect(PeriodName(label: PeriodRef(.regular, 2).label(periodCount: 2)) == .half(2))
        #expect(PeriodName(label: PeriodRef(.regular, 3).label(periodCount: 4)) == .quarter(3))
        #expect(PeriodName(label: PeriodRef(.extra, 1).label(periodCount: 2)) == .extra(1))
        #expect(PeriodName(label: PeriodRef(.extra, 2).label(periodCount: 4)) == .extra(2))
    }

    @Test func unknownLabelIsNil() {
        #expect(PeriodName(label: "") == nil)
        #expect(PeriodName(label: "H") == nil)
        #expect(PeriodName(label: "ETx") == nil)
        // 기간 이름 리뷰: 빠져 있던 잘못된 코드
        #expect(PeriodName(label: "1Q") == nil)
        #expect(PeriodName(label: "Q") == nil)
        #expect(PeriodName(label: "ET") == nil)
    }

    @Test func fourthQuarter() {
        #expect(PeriodName(label: PeriodRef(.regular, 4).label(periodCount: 4)) == .quarter(4))
    }
}
