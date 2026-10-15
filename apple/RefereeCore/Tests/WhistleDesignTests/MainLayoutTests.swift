import CoreGraphics
import Foundation
import Testing
@testable import WhistleDesign

struct MainLayoutTests {
    /// watchOS 화면(pt): SE 40mm, 41, 42, 44, 45, 46, Ultra 1·2 49mm, Ultra 3 49mm (시뮬레이터에서 잼)
    static let screens: [CGSize] = [
        CGSize(width: 162, height: 197), CGSize(width: 176, height: 215), CGSize(width: 187, height: 223),
        CGSize(width: 184, height: 224), CGSize(width: 198, height: 242), CGSize(width: 208, height: 248),
        CGSize(width: 205, height: 251), CGSize(width: 211, height: 257),
    ]
    /// 상단 상태 표시줄을 뺀 높이도 함께 검사한다.
    static let sizes: [CGSize] = screens + screens.map { CGSize(width: $0.width, height: $0.height - 30) }

    @Test(arguments: MainLayoutTests.sizes) func rowsStayInsideRing(size: CGSize) {
        let l = MainLayout(size: size)
        let inner = l.ringRadius - l.lineWidth / 2
        #expect(l.rows.count == 5)
        for row in l.rows {
            let corners = [
                CGPoint(x: row.minX, y: row.minY), CGPoint(x: row.maxX, y: row.minY),
                CGPoint(x: row.minX, y: row.maxY), CGPoint(x: row.maxX, y: row.maxY),
            ]
            for corner in corners {
                let distance = hypot(corner.x - l.ringCenter.x, corner.y - l.ringCenter.y)
                #expect(distance <= inner + 0.001, "\(size) \(row)")
            }
        }
    }

    @Test(arguments: MainLayoutTests.sizes) func rowsDoNotOverlap(size: CGSize) {
        let rows = MainLayout(size: size).rows
        for (upper, lower) in zip(rows, rows.dropFirst()) {
            #expect(upper.maxY <= lower.minY + 0.001, "\(size)")
        }
        for row in rows {
            #expect(row.height > 0 && row.width > 0, "\(size)")
        }
    }

    @Test(arguments: MainLayoutTests.sizes) func buttonsSitBelowRing(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(l.buttons.minY >= l.ringCenter.y + l.ringRadius + l.lineWidth / 2, "\(size)")
        #expect(l.buttons.maxY <= size.height, "\(size)")
        #expect(l.buttons.minX >= 0 && l.buttons.maxX <= size.width, "\(size)")
    }

    @Test(arguments: MainLayoutTests.sizes) func ringFitsScreen(size: CGSize) {
        let l = MainLayout(size: size)
        let outer = l.ringRadius + l.lineWidth / 2
        #expect(l.ringCenter.x - outer >= 0 && l.ringCenter.x + outer <= size.width, "\(size)")
        #expect(l.ringCenter.y - outer >= 0, "\(size)")
    }

    /// 스펙 §3.2: 앱은 위쪽 안전 영역(시스템 시각, 약 30pt)을 뺀 높이에서 배치한다.
    /// 높이가 링 크기를 정하므로 시계는 40mm 약 29pt ~ 49mm 약 40pt
    @Test func clockScalesFromSmallestToLargestWatch() {
        let small = MainLayout(size: CGSize(width: 162, height: 197 - 30)).clockFontSize
        let large = MainLayout(size: CGSize(width: 205, height: 251 - 30)).clockFontSize
        #expect((28...31).contains(small), "\(small)")
        #expect((38...42).contains(large), "\(large)")
    }

    /// 가장 작은 워치에서도 상단 줄·상태 줄 글자(줄 높이의 0.8)가 10pt 이상
    @Test func secondaryRowsReadableOnSmallestWatch() {
        let rows = MainLayout(size: CGSize(width: 162, height: 197 - 30)).rows
        #expect(rows[0].height * 0.8 >= 10, "\(rows[0].height)")
        #expect(rows[4].height * 0.8 >= 10, "\(rows[4].height)")
    }

    @Test func zeroSizeDoesNotCrash() {
        let l = MainLayout(size: .zero)
        #expect(l.rows.count == 5)
        #expect(l.ringRadius >= 0)
        #expect(l.rows.allSatisfy { $0.width >= 0 && $0.height >= 0 })
    }

    /// Always-On: 시계·추가시간·점수는 남기고 상단 줄·상태 줄은 숨긴다 (추가시간 중엔 시계가 멈춰 있으므로 +M:SS가 필요)
    @Test func alwaysOnKeepsClockAddedTimeAndScore() {
        #expect((0..<5).filter { MainLayout.showsRow($0, reduced: true) } == [1, 2, 3])
        #expect((0..<5).filter { MainLayout.showsRow($0, reduced: false) } == [0, 1, 2, 3, 4])
    }
}
