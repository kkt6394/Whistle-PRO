import CoreGraphics
import Testing
@testable import WhistleDesign

struct FillLayoutTests {
    @Test func heroTakesSpare() {
        let r = FillLayout.layout(available: 200, spacing: 4, items: [FillItem(20), FillItem(44, .hero)], heroCap: nil)
        #expect(r.heights == [20, 176])
        #expect(r.fits && r.spacing == 4)
    }

    /// 주인공은 상한에서 멈추고, 남는 높이는 두 번째 요소, 그다음 간격이 받는다 (스펙 §2.4)
    @Test func capThenSecondThenSpacing() {
        let r = FillLayout.layout(available: 200, spacing: 4, items: [FillItem(40, .second), FillItem(44, .hero)], heroCap: 60)
        #expect(r.heights == [136, 60])
        let noSecond = FillLayout.layout(available: 200, spacing: 4, items: [FillItem(20), FillItem(44, .hero)], heroCap: 60)
        #expect(noSecond.heights == [20, 60])
        #expect(abs(noSecond.spacing - 120) < 0.001)  // 4 + (200 - 20 - 60 - 4)
    }

    @Test func heroesShareEqually() {
        let r = FillLayout.layout(available: 120, spacing: 0, items: [FillItem(20), FillItem(44, .hero), FillItem(44, .hero)], heroCap: nil)
        #expect(r.heights == [20, 50, 50])
    }

    /// 모자라면 정한 순서대로 숨긴다. 들어가면 더 숨기지 않는다 (스펙 §2.5)
    @Test func hidesInOrderUntilFits() {
        let items = [FillItem(20, .hideable(order: 1)), FillItem(14, .hideable(order: 2)), FillItem(40), FillItem(44, .hero)]
        let r = FillLayout.layout(available: 100, spacing: 0, items: items)
        #expect(r.hidden == [true, false, false, false])
        #expect(r.heights == [0, 14, 40, 46])
        #expect(r.fits)
    }

    @Test func doesNotHideWhenItFits() {
        let r = FillLayout.layout(available: 300, spacing: 4, items: [FillItem(20, .hideable(order: 1)), FillItem(44, .hero)])
        #expect(r.hidden == [false, false])
    }

    /// 다 숨겨도 안 들어가면 최소 높이 그대로, fits = false (화면이 스크롤로 바꾼다)
    @Test func reportsWhenItCannotFit() {
        let r = FillLayout.layout(available: 100, spacing: 4, items: [FillItem(20, .hideable(order: 1)), FillItem(60), FillItem(44, .hero)])
        #expect(!r.fits)
        #expect(r.hidden == [true, false, false])
        #expect(r.heights == [0, 60, 44])
    }

    /// 홈: 알림이 늘면 [새 경기]가 최소까지 줄어 양보한다
    @Test func heroShrinksToMinimumBeforeHiding() {
        let r = FillLayout.layout(available: 140, spacing: 6, items: [FillItem(44, .hero), FillItem(40), FillItem(44)], heroCap: nil)
        #expect(r.heights == [44, 40, 44])
        #expect(r.fits)
    }

    @Test func emptyAndNegativeAreSafe() {
        #expect(FillLayout.layout(available: 100, spacing: 4, items: []).heights == [])
        let r = FillLayout.layout(available: -10, spacing: 4, items: [FillItem(44, .hero)])
        #expect(r.heights == [44] && !r.fits)
    }

    /// 조건부로 빠진 항목(높이 0, 고정)은 그리지 않으므로 간격도 세지 않는다 (최종 리뷰: 유령 간격으로 넘친다고 오판)
    @Test func zeroHeightFixedItemsTakeNoSpacing() {
        let r = FillLayout.layout(available: 100, spacing: 10, items: [FillItem(40, .hero), FillItem(0), FillItem(0), FillItem(40)], heroCap: nil)
        #expect(r.fits)
        #expect(r.heights == [50, 0, 0, 40])
        #expect(r.spacing == 10)
    }

    /// 주인공 최소 높이가 달라 하나가 먼저 상한에 닿으면, 남은 몫은 다른 주인공이 받는다 (B2 리뷰)
    @Test func heroLeftoverGoesToOtherHeroes() {
        let r = FillLayout.layout(available: 120 + 0, spacing: 0,
                                  items: [FillItem(50, .hero), FillItem(30, .hero)], heroCap: 60)
        #expect(r.heights == [60, 60])
        #expect(r.spacing == 0)
    }
}
