import Testing
@testable import RefereeSession

struct CrownGestureTests {
    /// (delta, 시각) 목록을 차례로 넣어 나온 이벤트를 모은다. 마지막엔 손이 멈춘 것으로 보고 flush한 결과를 덧붙인다
    private func run(_ samples: [(Double, Double)], flushAfter: Double = 0.3) -> [CrownGestureEvent?] {
        var detector = CrownGestureDetector()
        var events = samples.map { detector.feed(delta: $0.0, at: $0.1) }
        events.append(detector.flush(at: (samples.last?.1 ?? 0) + flushAfter))
        return events
    }

    private func steps(_ delta: Double, count: Int, from start: Double = 0, every gap: Double = 0.05) -> [(Double, Double)] {
        (0..<count).map { (delta, start + Double($0) * gap) }
    }

    @Test func halfTurnClockwiseIsPrimary() {
        let events = run(steps(0.1, count: 5))
        // 반 바퀴를 넘겨도 획이 끝날 때까지 기다렸다가 실행한다. 시각은 획 시작
        #expect(events.prefix(5).allSatisfy { $0 == nil })
        #expect(events[5] == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    @Test func halfTurnCounterClockwiseIsSecondary() {
        let events = run(steps(-0.1, count: 5))
        #expect(events[5] == CrownGestureEvent(input: .secondary, startedAt: 0))
    }

    /// 멈춘 지 strokeEnd가 안 됐으면 아직 실행하지 않는다
    @Test func halfTurnWaitsForStrokeEnd() {
        var detector = CrownGestureDetector()
        for (d, t) in steps(0.1, count: 5) { _ = detector.feed(delta: d, at: t) }
        #expect(detector.hasPending)
        #expect(detector.flush(at: 0.2 + 0.1) == nil)
        #expect(detector.flush(at: 0.2 + 0.16) == CrownGestureEvent(input: .primary, startedAt: 0))
        #expect(!detector.hasPending)
    }

    /// 반 바퀴 뒤 멈추지 않고 다른 방향 표본이 오면, 앞 획을 먼저 실행하고 새 획을 시작한다
    @Test func nextStrokeAfterPauseFlushesPrevious() {
        var detector = CrownGestureDetector()
        for (d, t) in steps(0.1, count: 5) { _ = detector.feed(delta: d, at: t) }
        let event = detector.feed(delta: -0.1, at: 0.2 + 0.3)
        #expect(event == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    /// 획이 반 바퀴를 넘는 큰 흔들기(SE 실기기 76~186°)도 중단이 아니라 실행 취소
    @Test func bigWiggleIsUndoNotPrimary() {
        // 획 0.52바퀴·0.21·0.43·0.35, 표본 0.02초 간격, 방향 바꿀 때 0.08초 멈춤
        var samples: [(Double, Double)] = []
        var t = 0.0
        for (sign, size) in [(1.0, 0.52), (-1.0, 0.21), (1.0, 0.43), (-1.0, 0.35)] {
            let n = Int(size / 0.05)
            for _ in 0..<n { samples.append((sign * 0.05, t)); t += 0.02 }
            t += 0.08
        }
        let fired = run(samples).compactMap { $0 }
        #expect(fired.count == 1)
        #expect(fired.first?.input == .undo)
    }

    /// 0.1바퀴(36°)는 기준(0.15) 미만 → 무시
    @Test func smallTurnIsIgnored() {
        #expect(run(steps(0.1, count: 1)).allSatisfy { $0 == nil })
    }

    /// 0.2바퀴(72°)는 기준을 넘겨 획이 끝난 뒤 실행 (2026-11-02 반 바퀴 → 0.15바퀴)
    @Test func smallCommandTurnIsEnough() {
        let events = run(steps(0.1, count: 2))
        #expect(events.prefix(2).allSatisfy { $0 == nil })
        #expect(events[2] == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    /// 0.1바퀴 돌리고 0.5초 쉬면 처음부터: 두 번 합쳐 0.2바퀴여도 실행되지 않는다
    @Test func pauseResetsTurn() {
        let events = run([(0.1, 0), (0.1, 0.6)])
        #expect(events.allSatisfy { $0 == nil })
    }

    /// 3/4바퀴를 넘기면 멈추길 기다리지 않고 바로 실행, 시각은 획 시작
    @Test func threeQuarterTurnFiresImmediately() {
        var detector = CrownGestureDetector()
        var events: [CrownGestureEvent?] = []
        for (d, t) in steps(0.1, count: 8) { events.append(detector.feed(delta: d, at: t)) }
        #expect(events.prefix(7).allSatisfy { $0 == nil })
        #expect(events[7] == CrownGestureEvent(input: .primary, startedAt: 0))
        #expect(!detector.hasPending)
    }

    @Test func continuousSpinFiresOnce() {
        let fired = run(steps(0.1, count: 10)).compactMap { $0 }
        #expect(fired.count == 1)
        #expect(fired.first == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    @Test func wiggleTwiceIsUndo() {
        let deltas = [0.05, 0.05, -0.05, -0.05, 0.05, 0.05, -0.05, -0.05]
        let samples = deltas.enumerated().map { ($0.element, Double($0.offset) * 0.05) }
        let fired = run(samples).compactMap { $0 }
        #expect(fired.count == 1)
        #expect(fired.first?.input == .undo)
    }

    @Test func slowWiggleIsNotUndo() {
        let deltas = [0.05, 0.05, -0.05, -0.05, 0.05, 0.05, -0.05, -0.05]
        let samples = deltas.enumerated().map { ($0.element, Double($0.offset) * 0.5) }
        #expect(run(samples).allSatisfy { $0 == nil })
    }

    /// 작은 흔들기(획 0.04바퀴 ≈ 14°)도 실행 취소 (2026-11-02 SE 실기기: 29° 기준엔 안 잡힘)
    @Test func smallWiggleIsUndo() {
        let deltas = [0.02, 0.02, -0.02, -0.02, 0.02, 0.02, -0.02, -0.02]
        let samples = deltas.enumerated().map { ($0.element, Double($0.offset) * 0.08) }
        let fired = run(samples).compactMap { $0 }
        #expect(fired.count == 1)
        #expect(fired.first?.input == .undo)
    }

    // MARK: 사용자 설정 각도 (2026-11-02 워치 설정 "크라운 설정")

    /// 도 단위 설정 → 바퀴. 기본 54°
    @Test func configFromDegrees() {
        #expect(CrownGestureConfig.defaultCommandDegrees == 54)
        #expect(CrownGestureConfig(commandDegrees: 90).commandTurn == 0.25)
        #expect(CrownGestureConfig(commandDegrees: 54).commandTurn == CrownGestureConfig().commandTurn)
    }

    /// 선택지는 18°~180° 6° 간격, 범위 밖은 가까운 끝으로
    @Test func degreeOptionsAndClamp() {
        #expect(CrownGestureConfig.commandDegreeOptions.first == 18)
        #expect(CrownGestureConfig.commandDegreeOptions.last == 180)
        #expect(CrownGestureConfig.commandDegreeOptions.contains(54))
        #expect(CrownGestureConfig.commandDegreeOptions.allSatisfy { $0 % 6 == 0 })
        #expect(CrownGestureConfig.clampCommandDegrees(0) == 18)
        #expect(CrownGestureConfig.clampCommandDegrees(999) == 180)
        #expect(CrownGestureConfig.clampCommandDegrees(60) == 60)
    }

    /// 90°로 두면 72°(0.2바퀴)는 무시하고 108°(0.3바퀴)는 실행
    @Test func higherDegreesNeedMoreTurn() {
        var detector = CrownGestureDetector(config: CrownGestureConfig(commandDegrees: 90))
        for (d, t) in steps(0.1, count: 2) { _ = detector.feed(delta: d, at: t) }
        #expect(detector.flush(at: 0.05 + 0.3) == nil)
        _ = detector.feed(delta: 0.1, at: 0.6)
        #expect(detector.flush(at: 0.6 + 0.3) == nil, "쉼 뒤 새 획 0.1은 아직 기준 미만")
        var fresh = CrownGestureDetector(config: CrownGestureConfig(commandDegrees: 90))
        for (d, t) in steps(0.1, count: 3) { _ = fresh.feed(delta: d, at: t) }
        #expect(fresh.flush(at: 0.1 + 0.3) == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    @Test func jitterIsNotWiggle() {
        let samples = (0..<40).map { (($0 % 2 == 0 ? 0.02 : -0.02), Double($0) * 0.02) }
        #expect(run(samples).allSatisfy { $0 == nil })
    }

    /// 설정 줄 글자: 기본(54°)은 "기본", 작으면(덜 돌려도 됨) "예민하게", 크면 "둔하게" (2026-11-04 A1 critique)
    @Test func commandFeelComparedToDefault() {
        #expect(CrownGestureConfig.commandFeel(degrees: 54) == .standard)
        #expect(CrownGestureConfig.commandFeel(degrees: 48) == .sensitive)
        #expect(CrownGestureConfig.commandFeel(degrees: 18) == .sensitive)
        #expect(CrownGestureConfig.commandFeel(degrees: 60) == .dull)
        #expect(CrownGestureConfig.commandFeel(degrees: 180) == .dull)
    }

    /// [킥오프 취소] 뒤: 기다리던 입력은 버리고(flush해도 안 나옴) 실행 뒤 흡수 시간은 그대로 (2026-11-06)
    @Test func discardPendingDropsWaitingInputKeepsCooldown() {
        var detector = CrownGestureDetector()
        // 3/4바퀴 넘겨 바로 실행 → 0.6초 흡수
        for (d, t) in steps(0.2, count: 4) { _ = detector.feed(delta: d, at: t) }
        // 흡수 시간 안의 회전은 무시
        detector.discardPending()
        #expect(detector.feed(delta: 0.6, at: 0.2) == nil)
        #expect(!detector.hasPending)

        var waiting = CrownGestureDetector()
        for (d, t) in steps(0.1, count: 5) { _ = waiting.feed(delta: d, at: t) }
        #expect(waiting.hasPending)
        waiting.discardPending()
        #expect(!waiting.hasPending)
        #expect(waiting.flush(at: 1) == nil)
    }
}
