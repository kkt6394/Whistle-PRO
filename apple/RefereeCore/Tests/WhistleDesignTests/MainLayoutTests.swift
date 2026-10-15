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
    /// 크기마다 위쪽 안전 영역 있음(높이의 16%, 시스템 시각)/없음
    static let cases: [(CGSize, CGFloat)] = screens.flatMap { [($0, ($0.height * 0.16).rounded()), ($0, 0)] }

    /// 점이 둥근 사각형 안에 있는지 (가장 가까운 모서리 원 중심까지 거리로 판단)
    static func inside(_ p: CGPoint, _ rect: CGRect, radius: CGFloat, tolerance: CGFloat = 0.01) -> Bool {
        guard p.x >= rect.minX - tolerance, p.x <= rect.maxX + tolerance,
              p.y >= rect.minY - tolerance, p.y <= rect.maxY + tolerance else { return false }
        let r = min(radius, min(rect.width, rect.height) / 2)
        let cx = min(max(p.x, rect.minX + r), rect.maxX - r)
        let cy = min(max(p.y, rect.minY + r), rect.maxY - r)
        return hypot(p.x - cx, p.y - cy) <= r + tolerance
    }

    static func corners(_ r: CGRect) -> [CGPoint] {
        [CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.minX, y: r.maxY), CGPoint(x: r.maxX, y: r.maxY)]
    }

    @Test(arguments: MainLayoutTests.cases)
    func boxesStayInsideBorder(size: CGSize, topInset: CGFloat) {
        let l = MainLayout(size: size, topInset: topInset)
        for (name, box) in [("header", l.header), ("clock", l.clock), ("home", l.home), ("away", l.away)] {
            for p in Self.corners(box) {
                #expect(Self.inside(p, l.content, radius: l.contentRadius), "\(size) \(topInset) \(name) \(box)")
            }
        }
        // 버튼 줄 양 끝은 캡슐: 끝 원(반지름 = 높이/2)이 내용 영역 안에 있어야 한다
        let half = l.buttons.height / 2
        let shrunk = l.content.insetBy(dx: half, dy: half)
        for x in [l.buttons.minX + half, l.buttons.maxX - half] {
            #expect(Self.inside(CGPoint(x: x, y: l.buttons.midY), shrunk, radius: max(0, l.contentRadius - half)),
                    "\(size) \(topInset) buttons \(l.buttons)")
        }
    }

    @Test(arguments: MainLayoutTests.cases)
    func boxesStackWithoutOverlap(size: CGSize, topInset: CGFloat) {
        let l = MainLayout(size: size, topInset: topInset)
        #expect(l.header.maxY <= l.clock.minY + 0.001)
        #expect(l.clock.maxY <= l.teams.minY + 0.001)
        #expect(l.teams.maxY <= l.buttons.minY + 0.001)
        #expect(l.home.maxX + MainLayout.dividerGap <= l.away.minX + 0.001)
        #expect(l.home.minX >= l.teams.minX - 0.001 && l.away.maxX <= l.teams.maxX + 0.001)
        // 팀 칸에 점수 줄과 일시 퇴장 줄이 다 들어간다
        #expect(l.scoreRowHeight + CGFloat(l.sinBinLines) * l.sinBinRowHeight <= l.teams.height + 0.001, "\(size)")
        for box in [l.header, l.clock, l.home, l.away, l.buttons] {
            #expect(box.width > 0 && box.height > 0, "\(size) \(box)")
        }
    }

    /// 하한은 가장 작은 40mm에서도 지킨다 (스펙 §3 크기 규칙)
    @Test(arguments: MainLayoutTests.cases)
    func meetsMinimums(size: CGSize, topInset: CGFloat) {
        let l = MainLayout(size: size, topInset: topInset)
        #expect(l.buttonHeight >= 44, "\(size) \(topInset) \(l.buttonHeight)")
        #expect(l.clockFontSize >= 44, "\(size) \(topInset) \(l.clockFontSize)")
        #expect(l.scoreFontSize >= 30, "\(size) \(topInset) \(l.scoreFontSize)")
        #expect(l.sinBinFontSize >= 13, "\(size) \(topInset) \(l.sinBinFontSize)")
        #expect(l.sinBinLines >= 1)
    }

    /// 큰 화면에서는 모두 하한보다 커진다
    @Test func largestWatchGrowsEverything() {
        let size = CGSize(width: 205, height: 251)
        let l = MainLayout(size: size, topInset: (size.height * 0.16).rounded())
        #expect(l.buttonHeight > 44)
        #expect(l.clockFontSize > 44)
        #expect(l.scoreFontSize > 30)
        #expect(l.sinBinLines >= 2)
    }

    /// 시계 "38:12"(다섯 글자, 약 2.8em)가 줄어들지 않고 들어간다
    @Test(arguments: MainLayoutTests.cases)
    func clockFitsWidth(size: CGSize, topInset: CGFloat) {
        let l = MainLayout(size: size, topInset: topInset)
        #expect(l.clockFontSize * 2.9 <= l.clock.width + 0.001, "\(size) \(l.clockFontSize) \(l.clock.width)")
    }

    /// 연장 후반 경과 시계 "105:00"(여섯 글자, 약 3.4em)은 0.8배 이상으로만 줄어든다 (Review Focus 2)
    @Test(arguments: MainLayoutTests.cases)
    func sixCharClockShrinksLittle(size: CGSize, topInset: CGFloat) {
        let l = MainLayout(size: size, topInset: topInset)
        #expect(l.clock.width / (l.clockFontSize * 3.4) >= 0.8, "\(size) \(l.clockFontSize) \(l.clock.width)")
    }

    /// 상단 띠는 시스템 시각 왼쪽, 위쪽 안전 영역 높이 안 (스펙 §3.1)
    @Test(arguments: MainLayoutTests.screens)
    func headerSitsBesideSystemTime(size: CGSize) {
        let topInset = (size.height * 0.16).rounded()
        let l = MainLayout(size: size, topInset: topInset)
        #expect(l.header.maxX <= size.width * (1 - MainLayout.systemTimeShare) + 0.001)
        #expect(l.header.maxY <= topInset + 0.001)
        #expect(l.headerFontSize >= 11, "\(size) \(l.headerFontSize)")
    }

    /// 테두리는 화면 안, 내용은 테두리 선 안쪽
    @Test(arguments: MainLayoutTests.screens)
    func borderFitsScreenAndWrapsContent(size: CGSize) {
        let l = MainLayout(size: size, topInset: 0)
        let outer = l.border.rect.insetBy(dx: -l.lineWidth / 2, dy: -l.lineWidth / 2)
        #expect(outer.minX >= 0 && outer.minY >= 0 && outer.maxX <= size.width && outer.maxY <= size.height)
        let inner = l.border.rect.insetBy(dx: l.lineWidth / 2, dy: l.lineWidth / 2)
        #expect(inner.contains(l.content))
        #expect(l.border.cornerRadius > 0)
    }

    @Test func zeroSizeDoesNotCrash() {
        let l = MainLayout(size: .zero, topInset: 0)
        for box in [l.header, l.clock, l.teams, l.home, l.away, l.buttons] {
            #expect(box.width >= 0 && box.height >= 0)
        }
        #expect(l.sinBinLines >= 1)
    }
}
