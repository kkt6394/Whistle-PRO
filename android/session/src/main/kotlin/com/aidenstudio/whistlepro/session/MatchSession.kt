package com.aidenstudio.whistlepro.session

import com.aidenstudio.whistlepro.core.Command
import com.aidenstudio.whistlepro.core.CommandKind
import com.aidenstudio.whistlepro.core.EngineException
import com.aidenstudio.whistlepro.core.MatchAlert
import com.aidenstudio.whistlepro.core.MatchEngine
import com.aidenstudio.whistlepro.core.MatchSnapshot
import com.aidenstudio.whistlepro.core.MatchState
import com.aidenstudio.whistlepro.core.Phase
import com.aidenstudio.whistlepro.core.apply
import com.aidenstudio.whistlepro.core.nextAlert
import com.aidenstudio.whistlepro.core.snapshot
import java.util.UUID

/** 현재 시각 (Unix epoch 초) */
fun epochSeconds(): Double = System.currentTimeMillis() / 1000.0

/**
 * 진행 중인 경기 하나. 명령을 검증해 적용하고 즉시 저장한다.
 * 스레드 안전하지 않다. 앱은 메인 스레드에서만 부른다 (Swift @MainActor와 같음).
 */
class MatchSession(
    record: MatchRecord,
    private val store: MatchLogStore,
    private val now: () -> Double = ::epochSeconds,
) {
    var record: MatchRecord = record
        private set
    var state: MatchState = MatchEngine.reduce(record.setup.rules, record.commands)
        private set
    /** 마지막 저장 실패 메시지. 다음 저장이 성공하면 null. */
    var saveError: String? = null
        private set
    /** 마지막으로 받아들인 명령의 id (방금 기록한 이벤트를 찾을 때 쓴다) */
    var lastCommandID: String? = null
        private set

    val setup: MatchSetup get() = record.setup

    val isFinished: Boolean get() = state.phase is Phase.Finished

    /**
     * 첫 킥오프(startPeriod)와 그 이전 명령은 되돌리지 않는다.
     * 되돌리면 시작 버튼이 없는 화면에 갇히기 때문이다.
     */
    val canUndo: Boolean
        get() {
            val effective = MatchEngine.effectiveCommands(record.commands)
            val kickoff = effective.indexOfFirst { it.kind == CommandKind.StartPeriod }
            if (kickoff < 0) return effective.isNotEmpty()
            return effective.size > kickoff + 1
        }

    /** 현재 시각으로 명령을 만들어 적용한다. 거부되면 로그에 남기지 않고 false. */
    fun send(kind: CommandKind): Boolean {
        val command = Command(timestamp = now(), kind = kind)
        val next = try {
            state.apply(command)
        } catch (e: EngineException) {
            return false
        }
        record = record.copy(commands = record.commands + command)
        lastCommandID = command.id
        state = next
        persist()
        return true
    }

    /** 마지막으로 적용된 명령을 되돌린다. */
    fun undo(): Boolean {
        if (!canUndo) return false
        record = record.copy(commands = record.commands + Command(timestamp = now(), kind = CommandKind.Undo))
        state = MatchEngine.reduce(record.setup.rules, record.commands)
        persist()
        return true
    }

    /** 끝난 경기를 보관함으로 옮긴다. 진행 중이거나 저장에 실패하면 false. */
    fun archiveIfFinished(): Boolean {
        if (!isFinished) return false
        return try {
            store.finishActive(record)
            saveError = null
            true
        } catch (e: Exception) {
            saveError = e.toString()
            false
        }
    }

    fun snapshot(time: Double? = null): MatchSnapshot = state.snapshot(time ?: now(), setup.warningBeforeEnd, setup.breakWarningBeforeEnd)

    fun nextAlert(time: Double? = null): MatchAlert? = state.nextAlert(time ?: now(), setup.warningBeforeEnd, setup.breakWarningBeforeEnd)

    private fun persist() {
        saveError = try {
            store.saveActive(record)
            null
        } catch (e: Exception) {
            e.toString()
        }
    }

    companion object {
        fun start(
            setup: MatchSetup,
            store: MatchLogStore,
            now: () -> Double = ::epochSeconds,
            unlocked: Boolean = true,
        ): MatchSession {
            val record = MatchRecord(UUID.randomUUID(), now(), setup, emptyList(), unlocked)
            return MatchSession(record, store, now).also { it.persist() }
        }
    }
}
