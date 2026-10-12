public enum MatchEngine {
    /// undo를 반영한 실제 적용 대상 명령. undo 하나가 직전 명령 하나를 지운다.
    public static func effectiveCommands(_ commands: [Command]) -> [Command] {
        var stack: [Command] = []
        for command in commands {
            if case .undo = command.kind {
                _ = stack.popLast()
            } else {
                stack.append(command)
            }
        }
        return stack
    }

    /// 명령 로그를 처음부터 재생해 상태를 만든다. 적용할 수 없는 명령은 건너뛰고 ignoredCommandIDs에 남긴다.
    public static func reduce(rules: RuleSet, commands: [Command]) -> MatchState {
        var state = MatchState(rules: rules)
        for command in effectiveCommands(commands) {
            do {
                try state.apply(command)
            } catch {
                state.ignoredCommandIDs.append(command.id)
            }
        }
        return state
    }
}
