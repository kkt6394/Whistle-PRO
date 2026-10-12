import CoreGraphics
import Foundation
import Testing
@testable import WhistleDesign

struct MainLayoutTests {
    /// watchOS 화면(pt): SE 40mm, 41, 42, 44, 45, 46, Ultra 49mm. 상단 상태 표시줄을 뺀 높이도 함께 검사한다.
    static let sizes: [CGSize] = {
        let screens = [
            CGSize(width: 162, height: 197), CGSize(width: 176, height: 215), CGSize(width: 187, height: 223),
            CGSize(width: 184, height: 224), CGSize(width: 198, height: 242), CGSize(width: 208, height: 248),
            CGSize(width: 205, height: 251),
        ]
        return screens + screens.map { CGSize(width: $0.width, height: $0.height - 30) }
    }()

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

    /// 스펙 §3.2: 시계는 40mm 약 36pt ~ 49mm 약 46pt
    @Test func clockScalesFromSmallestToLargestWatch() {
        let small = MainLayout(size: CGSize(width: 162, height: 197)).clockFontSize
        let large = MainLayout(size: CGSize(width: 205, height: 251)).clockFontSize
        #expect((34...38).contains(small), "\(small)")
        #expect((43...47).contains(large), "\(large)")
    }

    @Test func zeroSizeDoesNotCrash() {
        let l = MainLayout(size: .zero)
        #expect(l.rows.count == 5)
        #expect(l.ringRadius >= 0)
        #expect(l.rows.allSatisfy { $0.width >= 0 && $0.height >= 0 })
    }
}
