package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.FinishReason
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.ResolvedEventKind
import com.aidenstudio.whistlepro.core.RuleSet
import com.aidenstudio.whistlepro.core.Team
import com.aidenstudio.whistlepro.core.TeamPair
import com.aidenstudio.whistlepro.core.resolvedEvents
import com.aidenstudio.whistlepro.core.score
import com.aidenstudio.whistlepro.core.shootout
import com.aidenstudio.whistlepro.core.stoppageTotals
import com.aidenstudio.whistlepro.core.wholeSeconds
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max

data class ReportLine(
    val minute: EventMinute,
    val team: Team,
    val kind: ResolvedEventKind,
    val detail: String,
    /** periods 인덱스 (승부차기는 null). 휴식 중 기록은 직전 기간. */
    val period: Int? = null,
    /** 기간 안 경과 초. 휴식 중이면 그 기간 played. */
    val second: Long? = null,
    /** 골·자책골만: 이 줄 직후 점수 (승부차기 제외) */
    val scoreAfter: TeamPair<Int>? = null,
) {
    /** 이 줄이 속하는 열. `team`은 기록한 팀이라 자책골은 자책한 팀이다 → 득점한 팀(상대)의 열. */
    val columnTeam: Team get() = if (kind == ResolvedEventKind.OWN_GOAL) team.opponent else team
}

data class PeriodSummary(
    val label: String,
    /** 실제 진행 시간(초) */
    val played: Long,
    /** 기록된 중단 시간(초) */
    val stoppage: Long,
    /** 규정 시간을 넘겨 진행한 시간(초) */
    val added: Long,
    /** 규정 길이(초) */
    val nominal: Long = 0,
)

data class RuleLine(
    val field: RuleSet.Field,
    /** RuleSet.value(field) 원시 값 (표시는 앱이 바꾼다) */
    val value: String,
)

/** 경기 보고서·상세 화면에 쓰는 요약. 경기 기록을 재생해서 만든다. */
class MatchReport(bundle: MatchBundle, note: MatchNote? = null, referee: String? = null) {
    /** 보고서 머리 값 (공백을 다듬어 비면 null). 대회명은 경기 노트가 프리셋보다 앞선다. */
    val competition: String?
    val venue: String?
    val referee: String?
    val homeName: String
    val awayName: String
    val homeColor: TeamColor
    val awayColor: TeamColor
    val homeColor2: TeamColor?
    val awayColor2: TeamColor?
    /** 첫 킥오프 시각 (킥오프 전이면 기록 생성 시각) */
    val startedAt: Double
    val outcome: ReportOutcome
    val score: TeamPair<Int>
    val shootout: TeamPair<Int>?
    val goals: List<ReportLine>
    /** 옐로·경고 누적 퇴장·레드·일시 퇴장 */
    val discipline: List<ReportLine>
    val substitutions: List<ReportLine>
    /** 골·징계·교체를 기록한 순서대로 (상세 [기록] 탭) */
    val timeline: List<ReportLine>
    val periods: List<PeriodSummary>
    /** `periods`와 같은 길이: 그 기간에 난 골 (자책골 포함, 승부차기 제외). 휴식 중 골은 직전에 끝난 기간에 센다. */
    val periodScores: List<TeamPair<Int>>
    /** 팀별 골·옐로·퇴장·교체 합계 */
    val totals: TeamPair<ReportTotals>
    /** 경기 규칙 (제출용 보고서의 "경기 방식" 줄) */
    val rules: RuleSet
    /** FIFA 표준과 다른 규칙 */
    val ruleDeviations: List<RuleLine>
    val workoutPeriods: List<WorkoutStats>
    val workoutOverall: WorkoutStats?

    init {
        // 노트에 바꾼 팀 색이 있으면 그 색을 쓴다
        val setup = note?.colors?.applied(bundle.record.setup) ?: bundle.record.setup
        val state = MatchEngine.reduce(setup.rules, bundle.record.commands)
        val end = state.periods.lastOrNull()?.let { it.endedAt ?: it.startedAt } ?: bundle.record.createdAt

        fun trimmed(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }
        // 노트의 값이 비면(공백뿐이면) 프리셋 대회명으로 돌아간다
        competition = trimmed(note?.competition) ?: trimmed(setup.competition)
        venue = trimmed(note?.venue)
        this.referee = trimmed(referee)
        homeName = setup.homeName
        awayName = setup.awayName
        homeColor = setup.homeColor
        awayColor = setup.awayColor
        homeColor2 = setup.homeColor2
        awayColor2 = setup.awayColor2
        startedAt = state.periods.firstOrNull()?.startedAt ?: bundle.record.createdAt
        outcome = when (val phase = state.phase) {
            is Phase.Finished -> when (val reason = phase.reason) {
                FinishReason.Completed -> ReportOutcome.Completed
                is FinishReason.Abandoned -> ReportOutcome.Abandoned(reason.reason)
            }
            else -> ReportOutcome.InProgress
        }
        score = state.score
        val shootoutStatus = state.shootout
        shootout = if (shootoutStatus.kicks.home + shootoutStatus.kicks.away > 0) shootoutStatus.goals else null

        val rows = state.eventRows(setup).reversed()
        val stoppages = state.stoppageTotals(end)
        val playedSeconds = state.periods.map { max(0L, wholeSeconds((it.endedAt ?: end) - it.startedAt)) }
        val timelineKinds = setOf(
            ResolvedEventKind.GOAL, ResolvedEventKind.OWN_GOAL, ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW,
            ResolvedEventKind.RED, ResolvedEventKind.SIN_BIN, ResolvedEventKind.SUBSTITUTION)
        var running = TeamPair(0, 0)
        val all = rows.filter { it.kind in timelineKinds }.map { row ->
            var line = ReportLine(row.minute, row.team, row.kind, row.detail)
            val index = state.periods.indexOfFirst { it.ref == row.time.period }
            if (index >= 0) {
                val second = if (row.time.duringPlay) row.time.matchSecond - state.periods[index].offset else playedSeconds[index]
                line = line.copy(period = index, second = second)
            }
            if (row.kind == ResolvedEventKind.GOAL || row.kind == ResolvedEventKind.OWN_GOAL) {
                val scorer = line.columnTeam
                running = running.with(scorer, running[scorer] + 1)
                line = line.copy(scoreAfter = running)
            }
            line
        }
        fun lines(kinds: Set<ResolvedEventKind>): List<ReportLine> = all.filter { it.kind in kinds }
        goals = lines(setOf(ResolvedEventKind.GOAL, ResolvedEventKind.OWN_GOAL))
        discipline = lines(setOf(
            ResolvedEventKind.YELLOW, ResolvedEventKind.SECOND_YELLOW, ResolvedEventKind.RED, ResolvedEventKind.SIN_BIN))
        substitutions = lines(setOf(ResolvedEventKind.SUBSTITUTION))
        timeline = all

        periods = state.periods.mapIndexed { index, p ->
            val played = playedSeconds[index]
            PeriodSummary(
                label = p.ref.label(setup.rules.periodCount),
                played = played,
                stoppage = stoppages[index],
                added = max(0L, played - p.nominalLength),
                nominal = p.nominalLength,
            )
        }
        val scores = MutableList(state.periods.size) { TeamPair(0, 0) }
        for (resolved in state.resolvedEvents) {
            if (resolved.kind != ResolvedEventKind.GOAL && resolved.kind != ResolvedEventKind.OWN_GOAL) continue
            val index = state.periods.indexOfFirst { it.ref == resolved.event.time.period }
            if (index < 0) continue
            val team = resolved.event.payload.team
            val scorer = if (resolved.kind == ResolvedEventKind.OWN_GOAL) team.opponent else team
            val current = scores[index]
            scores[index] = current.with(scorer, current[scorer] + 1)
        }
        periodScores = scores
        totals = TeamPair(
            home = ReportTotals.of(timeline.filter { it.columnTeam == Team.HOME }),
            away = ReportTotals.of(timeline.filter { it.columnTeam == Team.AWAY }),
        )

        rules = setup.rules
        ruleDeviations = setup.rules.deviations().map { RuleLine(it, setup.rules.value(it)) }

        val workout = bundle.workout
        if (workout != null) {
            workoutPeriods = WorkoutAnalysis.perPeriod(workout, state, end)
            workoutOverall = WorkoutAnalysis.overall(workout, state, end)
        } else {
            workoutPeriods = emptyList()
            workoutOverall = null
        }
    }

    /**
     * 공유 파일 이름 (확장자 없이): "WhistlePRO 2026-10-15 FC 서울숲 vs 한강 유나이티드".
     * 경로에 못 쓰는 글자는 "-"로 (백로그: "WhistlePRO-1791945042"처럼 숫자였다)
     */
    fun fileStem(zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochSecond(floor(startedAt).toLong()).atZone(zone).toLocalDate()
        val date = "%04d-%02d-%02d".format(day.year, day.monthValue, day.dayOfMonth)
        fun clean(name: String) = name.map { if (it in "/\\:?%*|\"<>") '-' else it }.joinToString("").trim()
        return "WhistlePRO $date ${clean(homeName)} vs ${clean(awayName)}"
    }

    /** index번째 기간 시작 때의 점수 (앞 기간 골 합) */
    fun scoreBefore(period: Int): TeamPair<Int> =
        TeamPair(periodScores.take(period).sumOf { it.home }, periodScores.take(period).sumOf { it.away })

    fun color2(team: Team): TeamColor? = if (team == Team.HOME) homeColor2 else awayColor2

    fun name(team: Team): String = if (team == Team.HOME) homeName else awayName
}
