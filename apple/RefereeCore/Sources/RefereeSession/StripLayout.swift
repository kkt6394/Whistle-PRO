import Foundation
import RefereeCore

/// 경기 띠의 한 표시 (골·카드 위치)
public struct StripMark: Equatable, Sendable {
    public var lineIndex: Int
    /// 0…1
    public var x: Double
    /// 0 또는 1 (가까운 표시끼리 위아래로 엇갈린다)
    public var row: Int
}

public struct StripLabel: Equatable, Sendable {
    public var x: Double
    public var minute: EventMinute
}

/// 경기 띠 좌표·라벨 계산. 구간 폭은 기간마다 실제 진행 시간(played)에 비례한다.
public enum StripLayout {
    /// x = (앞 기간 played 합 + second) / 전체 played 합. period·second가 nil인 줄은 건너뛴다.
    /// row: 같은 team의 직전 mark가 row 0이고 |Δx| < minGap이면 1, 그 밖엔 0. played 합이 0이면 [].
    public static func marks(periods: [PeriodSummary], lines: [ReportLine], minGap: Double) -> [StripMark] {
        let total = periods.reduce(0) { $0 + $1.played }
        guard total > 0 else { return [] }
        var result: [StripMark] = []
        var last: [Team: StripMark] = [:]
        for (index, line) in lines.enumerated() {
            guard let p = line.period, let second = line.second, periods.indices.contains(p) else { continue }
            let before = periods.prefix(p).reduce(0) { $0 + $1.played }
            let x = Double(before + second) / Double(total)
            var row = 0
            if let prev = last[line.team], prev.row == 0, abs(x - prev.x) < minGap { row = 1 }
            let mark = StripMark(lineIndex: index, x: x, row: row)
            result.append(mark)
            last[line.team] = mark
        }
        return result
    }

    /// 기간 경계 x (periods.count - 1개). played 합이 0이면 [].
    public static func boundaries(periods: [PeriodSummary]) -> [Double] {
        let total = periods.reduce(0) { $0 + $1.played }
        guard total > 0 else { return [] }
        var sum = 0
        return periods.dropLast().map { sum += $0.played; return Double(sum) / Double(total) }
    }

    /// 양 끝 + 경계 라벨. 0은 .regular(0). 경계·정상 종료 끝은 규정 누적 분.
    /// completed == false면 끝 라벨은 마지막 기간이 멈춘 분 (규정 시간에 딱 멈췄으면 +0' 없이 정규 분).
    public static func labels(periods: [PeriodSummary], completed: Bool) -> [StripLabel] {
        let total = periods.reduce(0) { $0 + $1.played }
        guard total > 0, let lastPeriod = periods.last else { return [] }
        var result = [StripLabel(x: 0, minute: .regular(0))]
        let xs = boundaries(periods: periods)
        var nominalSum = 0
        for (i, x) in xs.enumerated() {
            nominalSum += periods[i].nominal
            result.append(StripLabel(x: x, minute: .regular(nominalSum / 60)))
        }
        let end: EventMinute
        if completed {
            end = .regular((nominalSum + lastPeriod.nominal) / 60)
        } else if lastPeriod.played <= lastPeriod.nominal {
            end = .regular((nominalSum + lastPeriod.played) / 60)
        } else {
            end = .added(base: (nominalSum + lastPeriod.nominal) / 60, extra: (lastPeriod.played - lastPeriod.nominal + 59) / 60)
        }
        result.append(StripLabel(x: 1, minute: end))
        return result
    }

    /// 보일 라벨 번호: 양 끝은 늘 남기고, 가운데 라벨은 앞에 남긴 라벨·끝 라벨과 `gap`(0…1 폭 비율) 이상 떨어질 때만.
    /// (연장 경기의 `105'`·`120'`처럼 끝에 붙는 경계 라벨만 빠지고 `45'`·`90'`은 남는다)
    public static func visibleLabelIndices(_ labels: [StripLabel], gap: Double) -> [Int] {
        guard labels.count > 2 else { return Array(labels.indices) }
        let last = labels.count - 1
        var kept = [0]
        for i in 1..<last {
            if labels[i].x - labels[kept.last!].x >= gap, labels[last].x - labels[i].x >= gap {
                kept.append(i)
            }
        }
        kept.append(last)
        return kept
    }
}
