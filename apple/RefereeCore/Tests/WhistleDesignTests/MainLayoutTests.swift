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

    /// 점이 둥근 사각형 안에 있는지. 위·아래 모서리 반지름이 다를 수 있다.
    static func inside(_ p: CGPoint, _ rect: CGRect, top: CGFloat, bottom: CGFloat, tolerance: CGFloat = 0.01) -> Bool {
        guard p.x >= rect.minX - tolerance, p.x <= rect.maxX + tolerance,
              p.y >= rect.minY - tolerance, p.y <= rect.maxY + tolerance else { return false }
        let r = p.y < rect.midY ? top : bottom
        let cx = min(max(p.x, rect.minX + r), rect.maxX - r)
        let cy = min(max(p.y, rect.minY + r), rect.maxY - r)
        return hypot(p.x - cx, p.y - cy) <= r + tolerance
    }

    static func corners(_ r: CGRect) -> [CGPoint] {
        [CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.minX, y: r.maxY), CGPoint(x: r.maxX, y: r.maxY)]
    }

    @Test(arguments: MainLayoutTests.screens)
    func boxesStayInsideBorder(size: CGSize) {
        let l = MainLayout(size: size)
        for (name, box) in [("clock", l.clock), ("subClock", l.subClock), ("home", l.home), ("away", l.away)] {
            for p in Self.corners(box) {
                #expect(Self.inside(p, l.content, top: l.contentTopRadius, bottom: l.contentBottomRadius), "\(size) \(name) \(box)")
            }
        }
        // 버튼 줄 양 끝은 캡슐: 끝 원(반지름 = 높이/2)이 내용 영역 안에 있어야 한다
        let half = l.buttons.height / 2
        let shrunk = l.content.insetBy(dx: half, dy: half)
        for x in [l.buttons.minX + half, l.buttons.maxX - half] {
            #expect(Self.inside(CGPoint(x: x, y: l.buttons.midY), shrunk,
                                top: max(0, l.contentTopRadius - half), bottom: max(0, l.contentBottomRadius - half)),
                    "\(size) buttons \(l.buttons)")
        }
    }

    /// 테두리는 시스템 시각 글자 아래에서 시작하고 (사용자 지적 2026-10-15: 오른쪽 위 곡선이 시각에 닿았다),
    /// 선 바깥 가장자리까지 화면 모서리 안에 있다
    @Test(arguments: MainLayoutTests.screens)
    func borderClearsSystemClockAndFitsScreen(size: CGSize) {
        let l = MainLayout(size: size)
        let screen = WatchScreen.metrics(for: size)
        #expect(l.border.rect.minY - l.lineWidth / 2 >= screen.clockBottom + 1, "\(size) \(l.border.rect)")
        let bounds = CGRect(origin: .zero, size: size)
        let center = CGPoint(x: l.border.rect.midX, y: l.border.rect.midY)
        for i in 0..<400 {
            let p = l.border.point(at: Double(i) / 400)
            // 선 바깥 가장자리 근사: 테두리 중심에서 그 점 방향으로 선 두께의 절반만큼 더
            let d = hypot(p.x - center.x, p.y - center.y)
            let edge = CGPoint(x: p.x + (p.x - center.x) / d * l.lineWidth / 2, y: p.y + (p.y - center.y) / d * l.lineWidth / 2)
            #expect(Self.inside(edge, bounds, top: screen.cornerRadius, bottom: screen.cornerRadius, tolerance: 0.5),
                    "\(size) \(i) \(edge)")
        }
    }

    /// 위 모서리도 아래(화면 모서리)와 같은 반지름 (2026-10-15 사용자 결정)
    @Test(arguments: MainLayoutTests.screens)
    func topCornersMatchBottom(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(abs(l.border.topRadius - l.border.bottomRadius) < 0.001, "\(size) \(l.border.topRadius) \(l.border.bottomRadius)")
        #expect(l.border.bottomRadius > 20)
    }

    @Test(arguments: MainLayoutTests.screens)
    func boxesStackWithoutOverlap(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(l.header.maxY <= l.border.rect.minY - l.lineWidth / 2 + 0.001, "상단 띠는 테두리 위 \(size)")
        // 작은 줄(남은 시간·정지시간)은 큰 시계 바로 아래, 팀 칸 위 (2026-10-15 사용자 결정: 정지시간이 상단 띠에서 시스템 시각 옆 아이콘과 겹쳤다)
        #expect(l.clock.maxY <= l.subClock.minY + 0.001)
        #expect(l.subClock.maxY <= l.teams.minY + 0.001)
        #expect(l.teams.maxY <= l.buttons.minY + 0.001)
        #expect(l.home.maxX + MainLayout.dividerGap <= l.away.minX + 0.001)
        #expect(l.home.minX >= l.teams.minX - 0.001 && l.away.maxX <= l.teams.maxX + 0.001)
        // 팀 칸에 점수 줄과 일시 퇴장 줄이 다 들어간다
        #expect(l.scoreRowHeight + CGFloat(l.sinBinLines) * l.sinBinRowHeight <= l.teams.height + 0.001, "\(size)")
        for box in [l.header, l.clock, l.subClock, l.home, l.away, l.buttons] {
            #expect(box.width > 0 && box.height > 0, "\(size) \(box)")
        }
    }

    /// 하한은 가장 작은 40mm에서도 지킨다 (스펙 §3 크기 규칙)
    @Test(arguments: MainLayoutTests.screens)
    func meetsMinimums(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(l.buttonHeight >= 44, "\(size) \(l.buttonHeight)")
        #expect(l.clockFontSize >= 44, "\(size) \(l.clockFontSize)")
        #expect(l.scoreFontSize >= 30, "\(size) \(l.scoreFontSize)")
        #expect(l.sinBinFontSize >= 13, "\(size) \(l.sinBinFontSize)")
        #expect(l.subClockFontSize >= 13, "\(size) \(l.subClockFontSize)")
        #expect(l.subClock.height >= l.subClockFontSize, "\(size) \(l.subClock)")
        #expect(l.sinBinLines >= 1)
    }

    /// 상단 띠는 시스템 시각 왼쪽의 상태 아이콘(아이폰 연결 끊김 등) 한 개 자리를 비운다
    /// (2026-10-15 사용자 지적: 띠 글자가 시각 옆 아이콘과 겹쳤다)
    @Test(arguments: MainLayoutTests.screens)
    func headerLeavesRoomForStatusIcon(size: CGSize) {
        let l = MainLayout(size: size)
        let screen = WatchScreen.metrics(for: size)
        #expect(l.header.maxX <= screen.clockLeft - 14 + 0.001, "\(size) \(l.header) \(screen.clockLeft)")
    }

    /// 버튼 둘은 폭이 높이 이상이라 둥글게 보인다 (2026-10-15 사용자 지적: 울트라에서 아래 모서리를 피하느라
    /// 줄이 좁아져 버튼이 세로로 긴 타원이었다)
    @Test(arguments: MainLayoutTests.screens)
    func buttonsAreAtLeastAsWideAsTall(size: CGSize) {
        let l = MainLayout(size: size)
        let count = CGFloat(MainLayout.buttonCount)
        let each = (l.buttons.width - MainLayout.buttonSpacing * (count - 1)) / count
        #expect(each >= l.buttons.height - 0.001, "\(size) 버튼 폭 \(each) 높이 \(l.buttons.height)")
    }

    /// 큰 화면에서는 모두 하한보다 커진다
    @Test func largestWatchGrowsEverything() {
        let l = MainLayout(size: CGSize(width: 205, height: 251))
        #expect(l.buttonHeight > 44)
        #expect(l.clockFontSize > 44)
        #expect(l.scoreFontSize > 30)
        // 작은 줄을 넣은 뒤(2026-10-15) 일시 퇴장은 49mm에서도 한 줄 — 여러 명이면 "×3"으로 수를 보인다
        #expect(l.sinBinLines >= 1)
    }

    /// 시계 "38:12"(다섯 글자, 약 2.8em)가 줄어들지 않고 들어간다
    @Test(arguments: MainLayoutTests.screens)
    func clockFitsWidth(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(l.clockFontSize * 2.9 <= l.clock.width + 0.001, "\(size) \(l.clockFontSize) \(l.clock.width)")
    }

    /// 연장 후반 경과 시계 "105:00"(여섯 글자, 약 3.4em)은 0.8배 이상으로만 줄어든다 (Review Focus 2)
    @Test(arguments: MainLayoutTests.screens)
    func sixCharClockShrinksLittle(size: CGSize) {
        let l = MainLayout(size: size)
        #expect(l.clock.width / (l.clockFontSize * 3.4) >= 0.8, "\(size) \(l.clockFontSize) \(l.clock.width)")
    }

    /// 상단 띠는 테두리 밖, 시스템 시각 왼쪽 같은 줄. 화면 모서리 안 (B1 스펙 §3.1)
    @Test(arguments: MainLayoutTests.screens)
    func headerSitsBesideSystemClock(size: CGSize) {
        let l = MainLayout(size: size)
        let screen = WatchScreen.metrics(for: size)
        #expect(l.header.maxX <= screen.clockLeft - 2, "\(size) \(l.header)")
        #expect(l.header.minY <= screen.clockTop && l.header.maxY >= screen.clockBottom, "시각과 같은 줄 \(size) \(l.header)")
        for p in Self.corners(l.header) {
            #expect(Self.inside(p, CGRect(origin: .zero, size: size), top: screen.cornerRadius, bottom: screen.cornerRadius),
                    "\(size) \(l.header)")
        }
        #expect(l.headerFontSize >= 11, "\(size) \(l.headerFontSize)")
        #expect(l.header.width >= 70, "\(size) \(l.header.width)")
    }

    @Test func zeroSizeDoesNotCrash() {
        let l = MainLayout(size: .zero)
        for box in [l.header, l.clock, l.teams, l.home, l.away, l.buttons] {
            #expect(box.width >= 0 && box.height >= 0)
        }
        #expect(l.sinBinLines >= 1)
    }
}
