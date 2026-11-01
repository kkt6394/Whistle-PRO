import Testing
@testable import RefereeSession

struct CrownGestureTests {
    /// (delta, 시각) 목록을 차례로 넣어 나온 이벤트를 모은다.
    private func run(_ samples: [(Double, Double)]) -> [CrownGestureEvent?] {
        var detector = CrownGestureDetector()
        return samples.map { detector.feed(delta: $0.0, at: $0.1) }
    }

    private func steps(_ delta: Double, count: Int, from start: Double = 0, every gap: Double = 0.05) -> [(Double, Double)] {
        (0..<count).map { (delta, start + Double($0) * gap) }
    }

    @Test func halfTurnClockwiseIsPrimary() {
        let events = run(steps(0.1, count: 5))
        #expect(events.prefix(4).allSatisfy { $0 == nil })
        #expect(events[4] == CrownGestureEvent(input: .primary, startedAt: 0))
    }

    @Test func halfTurnCounterClockwiseIsSecondary() {
        let events = run(steps(-0.1, count: 5))
        #expect(events[4] == CrownGestureEvent(input: .secondary, startedAt: 0))
    }

    @Test func smallTurnIsIgnored() {
        #expect(run(steps(0.1, count: 3)).allSatisfy { $0 == nil })
    }

    @Test func pauseResetsTurn() {
        let events = run([(0.1, 0), (0.1, 0.05), (0.1, 0.1), (0.1, 0.6), (0.1, 0.65), (0.1, 0.7)])
        #expect(events.allSatisfy { $0 == nil })
    }

    @Test func continuousSpinFiresOnce() {
        let fired = run(steps(0.1, count: 10)).compactMap { $0 }
        #expect(fired.count == 1)
        #expect(fired.first?.input == .primary)
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

    @Test func jitterIsNotWiggle() {
        let samples = (0..<40).map { (($0 % 2 == 0 ? 0.02 : -0.02), Double($0) * 0.02) }
        #expect(run(samples).allSatisfy { $0 == nil })
    }
}
