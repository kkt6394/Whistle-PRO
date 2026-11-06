package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.RuleSet
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** 메모리 저장소. failSaves를 켜면 저장이 실패한다. */
class MemoryStore : MatchLogStore {
    class SaveFailed : Exception()

    var active: MatchRecord? = null
    val finished = mutableListOf<MatchRecord>()
    var activeWorkout: WorkoutLog? = null
    val finishedWorkouts = mutableMapOf<UUID, WorkoutLog>()
    var failSaves = false

    override fun loadActive() = active

    override fun saveActive(record: MatchRecord) {
        if (failSaves) throw SaveFailed()
        active = record
    }

    override fun finishActive(record: MatchRecord) {
        if (failSaves) throw SaveFailed()
        finished += record
        activeWorkout?.let { if (it.matchID == record.id) finishedWorkouts[record.id] = it }
        active = null
        activeWorkout = null
    }

    override fun discardActive() {
        active = null
        activeWorkout = null
    }

    override fun archived() = finished.toList()

    override fun saveActiveWorkout(log: WorkoutLog) {
        if (failSaves) throw SaveFailed()
        activeWorkout = log
    }

    override fun loadActiveWorkout() = activeWorkout

    override fun archivedWorkout(matchID: UUID) = finishedWorkouts[matchID]

    override fun deleteArchived(matchID: UUID) {
        finished.removeAll { it.id == matchID }
        finishedWorkouts.remove(matchID)
    }
}

class FakeClock {
    var t: Double = 0.0
}

fun makeState(rules: RuleSet, commands: List<Pair<CommandKind, Double>>): MatchState =
    MatchEngine.reduce(rules, commands.map { Command(timestamp = it.second, kind = it.first) })

/** FIFA 표준 규칙으로 재생 */
fun makeState(commands: List<Pair<CommandKind, Double>>): MatchState = makeState(RuleSet.FIFA_STANDARD, commands)

fun tempDirectory(): File = Files.createTempDirectory("whistle").toFile().also { it.deleteOnExit() }.let { File(it, UUID.randomUUID().toString()) }

/** 명령 목록으로 경기 기록을 만든다 (명령 id는 c0, c1, ...) */
fun makeRecord(setup: MatchSetup, commands: List<Pair<CommandKind, Double>>, createdAt: Double = 0.0) = MatchRecord(
    id = UUID.randomUUID(), createdAt = createdAt, setup = setup,
    commands = commands.mapIndexed { i, (kind, t) -> Command("c$i", t, kind) },
)

/** 기본 설정(FIFA 표준)으로 경기 기록을 만든다 */
fun makeRecord(commands: List<Pair<CommandKind, Double>>, createdAt: Double = 0.0) = makeRecord(MatchSetup(), commands, createdAt)
