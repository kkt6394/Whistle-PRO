/// 워치 메인 상단 줄의 ⚠ (스펙 §4.1). 자세한 내용은 메뉴 페이지에 있다.
public enum MainWarning {
    /// 운동 세션이 멈췄거나(손목을 내리면 앱이 멈출 수 있음), 심박·거리 수집이 실패했거나, 경기 저장이 실패했을 때
    public static func shows(workoutRunning: Bool, workoutError: String?, saveError: String?) -> Bool {
        !workoutRunning || workoutError != nil || saveError != nil
    }
}
