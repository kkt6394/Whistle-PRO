package com.aidenstudio.whistlepro.core

object MatchEngine {
    /** undo를 반영한 실제 적용 대상 명령. undo 하나가 직전 명령 하나를 지운다. */
    fun effectiveCommands(commands: List<Command>): List<Command> {
        val stack = ArrayList<Command>()
        for (command in commands) {
            if (command.kind == CommandKind.Undo) stack.removeLastOrNull() else stack.add(command)
        }
        return stack
    }

    /** 명령 로그를 처음부터 재생해 상태를 만든다. 적용할 수 없는 명령은 건너뛰고 ignoredCommandIds에 남긴다. */
    fun reduce(rules: RuleSet, commands: List<Command>): MatchState =
        effectiveCommands(commands).fold(MatchState(rules)) { state, command ->
            try {
                state.apply(command)
            } catch (e: EngineException) {
                state.copy(ignoredCommandIds = state.ignoredCommandIds + command.id)
            }
        }
}
