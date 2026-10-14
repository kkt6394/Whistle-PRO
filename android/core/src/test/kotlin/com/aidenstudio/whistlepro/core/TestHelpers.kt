package com.aidenstudio.whistlepro.core

import java.util.UUID

fun cmd(kind: CommandKind, at: Double, id: String = UUID.randomUUID().toString()) = Command(id, at, kind)

fun goal(team: Team, at: Double, number: Int? = null, id: String = UUID.randomUUID().toString()) =
    cmd(CommandKind.RecordEvent(EventPayload.Goal(team, number, false)), at, id)

fun card(team: Team, number: Int?, kind: CardKind, at: Double, id: String = UUID.randomUUID().toString()) =
    cmd(CommandKind.RecordEvent(EventPayload.Card(team, number, kind)), at, id)

fun sub(team: Team, at: Double, id: String = UUID.randomUUID().toString()) =
    cmd(CommandKind.RecordEvent(EventPayload.Substitution(team, null, null)), at, id)

/** FIFA 표준 전·후반을 득점 없이 끝내는 명령 (후반 종료 시각 6300) */
fun twoHalves(): List<Command> = listOf(
    cmd(CommandKind.StartPeriod, 0.0), cmd(CommandKind.EndPeriod, 2700.0),
    cmd(CommandKind.StartPeriod, 3600.0), cmd(CommandKind.EndPeriod, 6300.0),
)
