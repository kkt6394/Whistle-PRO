# 공용 경기 엔진 시나리오

Swift(`apple/RefereeCore`)와 Kotlin(`android/core`) 경기 엔진이 **똑같이 통과해야 하는** 테스트 시나리오.

```bash
swift test --package-path apple/RefereeCore     # Swift
cd android && ./gradlew :core:test               # Kotlin
```

**엔진 규칙을 바꿀 때:** ① 바뀐 동작을 드러내는 시나리오를 먼저 추가해 실패를 확인 → ② Swift·Kotlin 양쪽을 고침 → ③ 두 테스트가 모두 통과. 한쪽 엔진만 고치는 커밋을 만들지 않는다.

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
- 엔진 디코더는 `id`가 없는 명령에 UUID를 만든다(빈 문자열 금지). 러너는 원본 JSON에 `id`가 없는 명령에만 `#i`를 붙인다.
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
| period | 마지막으로 시작한 기간 라벨 (`1H`, `Q3`, `ET1`). 승부차기 중이거나 승부차기로 끝났으면 `PK`. 시작 전이면 없음 |
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
| events | 이벤트 배열: `{id, type, team, number?, numberOut?, numberIn?, matchSecond, warnings}`. type은 `goal`·`ownGoal`·`yellow`·`secondYellow`·`red`·`sinBin`·`substitution`·`penaltyKick`, warnings 원소는 `substitutionLimitExceeded`·`repeatPenaltyKicker`·`playerAlreadySentOff` |
| sinBins | 진행 중 일시 퇴장: `{eventId, team, number?, remaining}` (remaining 올림) 승부차기·종료 상태에서는 항상 빈 배열 |
| warnings | 경기 경고: `belowMinimumPlayers:home` 형식 |
| nextAlert | `{kind, at}` — kind는 `warningBeforeEnd`·`periodEnd`·`breakEndingSoon`·`breakEnd`·`sinBinEnd`. `at`보다 뒤의 첫 알림 |
| ignored | 적용되지 못한 명령 id 배열 |

## 엔진 규칙 (스냅샷만으로 드러나지 않는 것)

다른 언어로 엔진을 옮길 때 `ignored`와 `events` 결과가 같아지려면 아래 규칙을 그대로 따라야 한다.

### 재생과 undo
- 명령은 **로그 순서대로** 적용한다(타임스탬프로 정렬하지 않는다). 시나리오의 `t`는 단조 증가해야 한다.
- `undo`는 적용 전에 처리한다. 로그를 앞에서부터 읽으며 `undo`를 만나면 **직전에 남아 있는 명령 하나**를 지운다(그 명령이 무시된 명령이어도 지운다). 빈 상태에서의 `undo`는 아무 일도 하지 않는다. `undo` 자신은 `ignored`에 들어가지 않는다.
- 적용할 수 없는 명령은 상태를 바꾸지 않고 `ignored`에 id를 남긴다. 명령 하나는 전부 적용되거나 전혀 적용되지 않는다.

### 상태별 허용 명령
| 명령 | 허용 상태 |
|---|---|
| setKickoffTeam | notStarted(정규 킥오프), preExtraTime(연장 킥오프) |
| startPeriod | notStarted, interval, preExtraTime |
| endPeriod, toggleStoppage | period |
| adjustPeriodLength | notStarted·interval·period·preExtraTime. `seconds`는 60~5400. 현재(또는 다음) 기간이 연장이면 연장 길이, 아니면 정규 길이를 바꾸고, period 중이면 진행 중인 기간에도 바로 적용 |
| setShootoutFirstTeam | penalties이고 아직 킥이 없을 때 |
| recordGoal | period |
| recordCard | period, interval, preExtraTime, penalties. `sinBin`은 규칙 `sinBin`이 켜져 있을 때만 |
| recordSubstitution | period, interval, preExtraTime |
| recordPenaltyKick | penalties, 선축 팀 지정 후, 차례인 팀만 |
| abandonMatch | finished가 아닌 모든 상태 |

등번호는 0~99만 허용한다.

### 수정·삭제
- `editEvent`는 같은 종류(`goal`·`card`·`substitution`·`penaltyKick`) 안에서만 가능하다. 시각은 원래 기록 시각을 유지한다.
- 골의 **득점 팀이 바뀌는 수정**(팀 변경, 자책골 여부 변경)과 골 삭제는 period·interval에서만 허용한다. 마지막 기간이 끝난 뒤에는 점수로 이미 연장·승부차기·종료가 정해졌기 때문이다. 득점 팀이 같은 수정(등번호 정정)은 언제나 가능하다.
- 승부차기 킥은 penalties 중이거나 승부차기로 끝난(finished·completed) 경기에서 수정할 수 있고 팀은 바꿀 수 없다. 삭제는 **마지막 킥**만 가능하다. 수정·삭제 후 승부가 다시 미정이면 penalties로 돌아가고, 승부가 나면 finished가 된다.

### 이벤트 해석
- `matchSecond` = 기록 시점의 마지막 기간 `offset` + 그 기간 경과 초(내림). 휴식 중 기록이면 기간이 끝난 시점에서 멈춘 경과 초를 쓴다.
- 경고 누적: `secondYellowSendOff`가 켜져 있고 등번호가 있을 때, 같은 팀·같은 번호의 두 번째 yellow는 `secondYellow`.
- 이미 퇴장한(`red` 또는 `secondYellow`) 선수에게 다시 기록된 카드는 원래 종류(`yellow`·`red`·`sinBin`)로 두고 `playerAlreadySentOff` 경고를 붙이며, 퇴장 인원에 다시 세지 않는다.
- 일시 퇴장 중인 선수가 이후 퇴장당하면 그 일시 퇴장은 즉시 끝난다(`sinBins`·알림에서 빠짐).
- 교체 한도 = `substitutionLimit` + (연장에 들어간 뒤 기록된 교체라면 `extraTimeExtraSubs`). `returnSubstitutions`이거나 한도가 null이면 검사하지 않는다.

### 초 변환
- 경과·중단·남은 시간 등 초를 정수로 바꿀 때 값이 유한하지 않으면 0, ±1조 초 범위로 자른 뒤 반올림 규칙(경과는 내림, 남은 시간은 올림)을 적용한다.

### 숫자 비교
- 모든 숫자는 **값으로** 비교한다. `2400`과 `2400.0`은 같다.
