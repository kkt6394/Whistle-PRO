import Testing
@testable import WhistleDesign

struct MainHeaderTests {
    /// 상단 띠가 넘쳐 줄어들거나 잘려도 경고는 남아야 한다 (최종 리뷰: 맨 끝의 ⚠가 먼저 사라졌다)
    @Test func warningComesFirst() {
        #expect(MainHeader.items(period: "연장 전반", stoppage: "12:34", stoppageRunning: true, warning: true)
                == [.warning, .period("연장 전반"), .stoppage("12:34", running: true)])
    }

    @Test func onlyWhatExists() {
        #expect(MainHeader.items(period: "전반", stoppage: nil, stoppageRunning: false, warning: false) == [.period("전반")])
        #expect(MainHeader.items(period: nil, stoppage: "0:30", stoppageRunning: false, warning: false)
                == [.stoppage("0:30", running: false)])
    }
}
