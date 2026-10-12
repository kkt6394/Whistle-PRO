# 공용 경기 엔진 시나리오

Swift(`apple/RefereeCore`)와 Kotlin(`android/core`) 경기 엔진이 **똑같이 통과해야 하는** 테스트 시나리오.

## 파일 형식

```json
{
  "name": "설명",
  "config": { "periodLength": 1500 },
  "warningBeforeEnd": 300,
  "commands": [ { "type": "startPeriod", "t": 0 } ],
  "expect": [ { "at": 0, "phase": "period" } ]
}
```

- `config`: RuleSet 부분 객체. 없는 키는 FIFA 표준 기본값. `minimumPlayers`·`substitutionLimit`에 `null`을 주면 끔.
- `warningBeforeEnd`: 종료 N초 전 예고. 생략하면 300.
- `commands`: 명령 로그. `id`가 없으면 러너가 `"#<0부터 시작하는 인덱스>"`를 붙인다.
- `expect`: 각 항목마다 `t <= at`인 명령만 재생한 뒤, `at` 시점 스냅샷을 JSON으로 만들어 **적힌 키만** 비교한다. 값이 `null`이면 키가 없거나 null이어야 한다. 배열과 객체는 전체가 같아야 한다.
- 시간 단위는 초.

## 명령 (`type`)

| type | 필드 |
|---|---|
| setKickoffTeam | team |
| startPeriod | — |
| endPeriod | — |
| adjustPeriodLength | seconds |
| toggleStoppage | — |
| setShootoutFirstTeam | team |
| recordGoal | team, number?, ownGoal? |
| recordCard | team, number?, kind (yellow·red·sinBin) |
| recordSubstitution | team, numberOut?, numberIn? |
| recordPenaltyKick | team, number?, scored |
| editEvent | eventId, event ({"type": "goal"·"card"·"substitution"·"penaltyKick", ...record 필드}) |
| deleteEvent | eventId |
| undo | — |
| abandonMatch | reason |

`team`은 `"home"` 또는 `"away"`.

## 스냅샷 키

| 키 | 값 |
|---|---|
| phase | `notStarted`·`period`·`interval`·`preExtraTime`·`penalties`·`finished` |
| period | 마지막으로 시작한 기간 라벨 (`1H`, `Q3`, `ET1`). 시작 전이면 없음 |
| finishReason | `completed`·`abandoned` (종료 시에만) |
| abandonReason | 중단 사유 (중단 시에만) |
| score | `[home, away]` 정규+연장 득점 (자책골은 상대 팀) |
| shootoutScore | `[home, away]` 승부차기 득점 |
| nextShootoutTeam | 다음 키커 팀 (승부차기 중이고 선축 지정 후, 미결정일 때만) |
| elapsed | 마지막 기간 경과 초 (내림) |
| matchSecond | 누적 경기 시각 = 이전 기간 규정 길이 합 + elapsed |
| remaining | 규정 시간까지 남은 초 (0 이상) |
| addedTime | 규정 시간 초과 초 (0 이상) |
| intervalRemaining | 휴식 남은 초 (올림, 휴식 중에만) |
| stoppageTotals | 기간별 누적 중단 초 배열 |
| stoppageRunning | 중단 스톱워치 진행 여부 |
| kickoffTeam | 진행 중이거나 다음 기간의 킥오프 팀 (모르면 없음) |
| playersOnField | `[home, away]` 시작 인원 − 퇴장 − 진행 중 일시 퇴장 |
| substitutions | `[home, away]` 교체 횟수 |
| events | 이벤트 배열: `{id, type, team, number?, numberOut?, numberIn?, matchSecond, warnings}`. type은 `goal`·`ownGoal`·`yellow`·`secondYellow`·`red`·`sinBin`·`substitution`·`penaltyKick`, warnings 원소는 `substitutionLimitExceeded`·`repeatPenaltyKicker` |
| sinBins | 진행 중 일시 퇴장: `{eventId, team, number?, remaining}` (remaining 올림) |
| warnings | 경기 경고: `belowMinimumPlayers:home` 형식 |
| nextAlert | `{kind, at}` — kind는 `warningBeforeEnd`·`periodEnd`·`breakEndingSoon`·`breakEnd`·`sinBinEnd`. `at`보다 뒤의 첫 알림 |
| ignored | 적용되지 못한 명령 id 배열 |
