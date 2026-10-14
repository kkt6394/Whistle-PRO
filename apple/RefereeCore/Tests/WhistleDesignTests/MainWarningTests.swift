import Testing
@testable import WhistleDesign

/// 메인 상단 ⚠: 메뉴에 오류 문구가 있으면 메인에서도 알 수 있어야 한다
struct MainWarningTests {
    @Test func noWarningWhenAllIsWell() {
        #expect(!MainWarning.shows(workoutRunning: true, workoutError: nil, saveError: nil))
    }

    @Test func warnsWhenWorkoutSessionStopped() {
        #expect(MainWarning.shows(workoutRunning: false, workoutError: nil, saveError: nil))
    }

    /// 실기기: 건강 권한을 거부하면 운동 세션은 켜지지만 심박·거리 수집이 실패한다
    @Test func warnsWhenDataCollectionFailed() {
        #expect(MainWarning.shows(workoutRunning: true, workoutError: "권한 없음", saveError: nil))
    }

    @Test func warnsWhenSavingFailed() {
        #expect(MainWarning.shows(workoutRunning: true, workoutError: nil, saveError: "디스크 가득 참"))
    }
}
