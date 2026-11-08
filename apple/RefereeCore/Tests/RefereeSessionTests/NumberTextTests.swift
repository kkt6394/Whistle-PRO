import Foundation
import Testing
@testable import RefereeSession

/// 화면 소수점: 언어에 맞게 (독일어 쉼표, 영어 점), 반올림은 String(format:)과 같다 (안드로이드 NumberTextTests와 같은 값)
struct NumberTextTests {
    @Test func decimalFollowsLocale() {
        #expect(NumberText.decimal(12.34, digits: 1, locale: Locale(identifier: "de_DE")) == "12,3")
        #expect(NumberText.decimal(12.34, digits: 1, locale: Locale(identifier: "en_US")) == "12.3")
        #expect(NumberText.decimal(3, digits: 1, locale: Locale(identifier: "fr_FR")) == "3,0")
        #expect(NumberText.decimal(0.25, digits: 1, locale: Locale(identifier: "en_US")) == "0.3")
        #expect(NumberText.decimal(1234.5, digits: 2, locale: Locale(identifier: "de_DE")) == "1234,50")
    }

    @Test func monthDistanceUsesLocale() {
        #expect(MonthSummary.distanceText(meters: 9_840, locale: Locale(identifier: "de_DE")) == "9,8")
        #expect(MonthSummary.distanceText(meters: 9_840, locale: Locale(identifier: "ko_KR")) == "9.8")
        #expect(MonthSummary.distanceText(meters: 12_300, locale: Locale(identifier: "de_DE")) == "12")
    }
}
