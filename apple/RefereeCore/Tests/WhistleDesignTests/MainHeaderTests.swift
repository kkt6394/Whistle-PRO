import Testing
@testable import WhistleDesign

struct MainHeaderTests {
    /// 상단 띠가 넘쳐 줄어들거나 잘려도 경고는 남아야 한다 (최종 리뷰: 맨 끝의 ⚠가 먼저 사라졌다)
    @Test func warningComesFirst() {
        #expect(MainHeader.items(period: "연장 전반", warning: true) == [.warning, .period("연장 전반")])
    }

    /// 중단 시간은 띠에 없다 — 큰 시계 아래 줄로 옮겼다 (시스템 시각 옆 아이콘과 겹쳤다)
    @Test func onlyWhatExists() {
        #expect(MainHeader.items(period: "전반", warning: false) == [.period("전반")])
        #expect(MainHeader.items(period: nil, warning: true) == [.warning])
    }
}
