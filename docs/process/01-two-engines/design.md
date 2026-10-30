## 2. 아키텍처

### 접근법
생태계별 네이티브 구현. 생태계 안에서는 코어 모듈을 공유하고, Swift와 Kotlin 경기 엔진의 동작 일치는 **공용 JSON 시나리오 테스트**로 보장한다. KMP, Flutter는 채택하지 않는다(엔진 규모 대비 빌드·연동 비용이 크다. Flutter는 워치에서 동작하지 않는다).

### 저장소 구조 (모노레포)
```
/
├─ shared/
│  ├─ engine-scenarios/     # 공용 JSON 시나리오 (명령 목록 → 기대 상태)
│  └─ docs/                 # 규칙 정의 등
├─ apple/
│  ├─ RefereeCore/          # Swift Package: 경기 엔진, 모델, 보고서 데이터
│  ├─ RefereeiOS/           # iPhone 앱 (SwiftUI)
│  └─ RefereeWatch/         # watchOS 앱 (SwiftUI)
└─ android/                 # Phase 2
   ├─ core/                 # Kotlin 엔진 (동일 시나리오 통과)
   ├─ mobile/               # Android 폰 앱 (Compose)
   └─ wear/                 # Wear OS 앱 (Compose for Wear OS)
```

### 기기별 역할
| 시점 | 워치 | 폰 |
|---|---|---|
| 경기 전 | 빠른 시작, 또는 폰에서 받은 프리셋으로 시작 | 경기 설정, 프리셋 저장·전송 |
| 경기 중 | 타이머, 이벤트 기록, 운동 세션. 폰 없이 100% 동작 | 관여 없음 |
| 경기 후 | 완료된 경기를 폰으로 전송 | 기록 조회, 운동 데이터, 보고서 공유, 구독 관리 |

### 최소 지원 버전
- iOS 17 / watchOS 10
- Android 9 (API 28) / Wear OS 3

## 3. 경기 엔진

### 원칙
- **순수 로직.** 시계·진동·저장소에 의존하지 않는다. 현재 시각은 인자로 주입한다.
- **명령 로그 + 리듀서.** 상태는 저장하지 않고 `state = reduce(config, commands, now)`로 계산한다.
  - 크래시 복구 = 로그 재생
  - 실행 취소 = 마지막 명령 제거
  - 보고서 = 로그에서 파생
- 모든 명령은 wall-clock 타임스탬프를 가진다. 경과 시간은 타임스탬프 차이로 계산한다(틱 카운팅 금지).

### 규칙 기준
- **기본값은 IFAB 경기규칙(Laws of the Game, FIFA 채택)**이다. 내장 프리셋 "FIFA 표준"이 이 값을 담고 있으며 수정·삭제할 수 없다.
- 아마추어 경기는 리그·동호회마다 규칙이 다르므로 **모든 규칙 항목을 설정으로 바꿀 수 있다.** 사용자는 FIFA 표준을 복제해 자기 프리셋(예: "조기축구 25분×4")을 만든다.
- 엔진은 설정값만 따르며, 규칙 조항은 기본값의 근거로만 쓴다.
- 보고서에는 FIFA 표준과 다른 설정 항목을 "적용 규칙" 섹션으로 표시한다(리그 제출용).

### RuleSet (경기 규칙 설정)
| 필드 | 설명 | FIFA 기본값 (근거) | 설정 범위 |
|---|---|---|---|
| periodCount | 정규 기간 수 | 2 (Law 7.1) | 2 또는 4(쿼터제) |
| periodLength | 기간 길이 (모두 동일) | 45분 (Law 7.1) | 1~90분 |
| halfTimeLength | 하프타임 (2기간: 1↔2, 4기간: 2↔3 사이) | 15분 (Law 7.2) | 0~60분 |
| quarterBreakLength | 쿼터 사이 휴식 (4기간: 1↔2, 3↔4) | — | 0~30분 |
| extraTime | 연장전 사용 | 대회 규정 (Law 10.2) | 켬/끔 |
| extraTimePeriodLength | 연장 전·후반 길이 | 15분 (Law 10.2) | 1~30분 |
| extraTimeBreakLength | 연장 하프타임 | 1분 (Law 7.2) | 0~15분 |
| penalties | 승부차기 사용 | 대회 규정 (Law 10.3) | 켬/끔 |
| penaltyKicksPerTeam | 승부차기 기본 키커 수 | 5 (Law 10.3) | 1~10 |
| playersPerTeam | 시작 인원 | 11 (Law 3.1) | 3~11 |
| minimumPlayers | 경기 지속 최소 인원 | 7 (Law 3.1) | 1~playersPerTeam, 끔 가능 |
| substitutionLimit | 교체 한도 | 5 (Law 3.2) | 0~20 또는 무제한 |
| returnSubstitutions | 복귀 교체 허용 | 끔 (Law 3.2) | 켬/끔 (켜면 한도 검사 안 함) |
| extraTimeExtraSub | 연장전 추가 교체 | 0 (Law 3.2) | 0~3 |
| secondYellowSendOff | 경고 2회 자동 퇴장 | 켬 (Law 12.3) | 켬/끔 |
| sinBin | 일시 퇴장 사용, 시간 | 끔 (IFAB Modifications) | 켬/끔, 1~20분 (기본 10분) |
| ~~alternateKickoff~~ | 지움 (2026-10-15 사용자 결정): 제8조대로 기간마다 항상 상대 팀이 킥오프. 예전 데이터의 키는 무시 | | |

경기 설정(MatchConfig) = RuleSet + 팀(홈/원정 이름, 색상) + 화면 설정(clockDisplay: 경과/남은 시간, warningBeforeEnd: 종료 N분 전 예고, 기본 5분, 0이면 끔).

**설정 위치**
- 폰: 모든 항목을 편집하고 프리셋으로 저장해 워치로 보낸다.
- 워치: 프리셋을 고르고, 시작 직전에 **기간 길이·하프타임만 Digital Crown으로 빠르게 조정**할 수 있다. 경기장에서 "오늘은 30분씩 하자"는 상황을 위해서다. 나머지 항목은 폰에서 바꾼다.

### 상태 흐름
```
notStarted → period(1) → break → period(2) → … → period(periodCount)
  → [동점 && extraTime] preExtraTime → extraPeriod(1) → extraBreak → extraPeriod(2)
  → [동점 && penalties] penalties
  → finished
```
- 기간 종료는 심판의 명시적 명령으로만 일어난다. 규정 시간이 지나도 시계는 계속 흐르며 추가시간(예: `45:00 +2:13`)으로 표시한다. 페널티킥 진행·재시행으로 기간이 연장되는 경우도 이 방식으로 처리된다(Law 7.4).
- 기간 라벨: 2기간이면 `1H/2H`, 4기간이면 `Q1~Q4`, 연장은 `ET1/ET2`, 승부차기는 `PK`.
- `break`: 해당 휴식 길이로 카운트다운. 길이가 0이면 휴식 없이 바로 다음 기간 대기 상태가 된다.
- `preExtraTime`: 규칙에 정해진 길이가 없으므로 경과 시간만 표시하고, 심판이 시작한다.
- 동점이 아니거나 연장·승부차기 설정이 없으면 마지막 기간 종료 시 finished.
- `abandonMatch`로 어느 상태에서든 중단 종료할 수 있다(Law 7.5). 보고서에 "경기 중단"과 사유를 표시한다.

### 킥오프 (Law 8)
- 경기 시작 전 동전 던지기 결과로 첫 기간 킥오프 팀을 입력한다. 이후 기간마다 킥오프 팀을 번갈아 표시한다 (제8조).
- 연장 첫 기간 시작 전 킥오프 팀을 다시 입력한다.

### 명령 (Command)
| 명령 | 데이터 |
|---|---|
| setKickoffTeam | team |
| startPeriod | — |
| endPeriod | — |
| adjustPeriodLength | 새 길이 (진행 중 기간에도 적용 가능 — 경기 중 합의로 시간 변경 시) |
| toggleStoppage | — |
| recordGoal | team, number?, isOwnGoal |
| recordCard | team, number?, kind(yellow / red / sinBin) |
| recordSubstitution | team, numberOut?, numberIn? |
| setShootoutFirstTeam | team |
| recordPenaltyKick | team, number?, scored |
| editEvent | eventId, 변경 필드 |
| deleteEvent | eventId |
| undo | 마지막 명령 제거 |
| abandonMatch | reason |

모든 명령은 `id(UUID)`, `timestamp`를 가진다. 이벤트는 기간 라벨과 경기 시각(예: `2H 67:12`)을 파생값으로 가진다. 경기 시각은 기간이 이어지는 누적 시각이다(2H 시작 = 45:00, Q3 시작 = 2 × periodLength).

### 규칙
- **추가시간 (Law 7.3):** 주 시계는 멈추지 않는다. 심판은 `toggleStoppage`로 별도 스톱워치를 켜고 끄며, 기간별 누적 중단 시간을 "권장 추가시간"으로 표시한다. 교체, 부상, 시간 지연, 음료 휴식, 쿨링 휴식 모두 이 방식으로 기록한다. 최종 결정은 심판의 재량이며 앱은 참고값만 제시한다. 기간이 끝나면 진행 중인 중단은 자동으로 닫힌다.
- **경고 2회 퇴장 (Law 12.3, `secondYellowSendOff`):** 켜져 있으면 같은 팀·같은 등번호의 두 번째 yellow를 자동으로 "경고 누적 퇴장"으로 판정하고, 워치에 확인 화면(등번호 즉시 수정 가능)을 띄운다. 등번호 없는 카드는 판정에서 제외한다.
- **일시 퇴장 (`sinBin`):** 켜져 있으면 카드 종류에 "일시 퇴장"이 추가된다. 엔진은 선수별 복귀 시각을 계산하고, 메인 화면에 남은 시간을 표시하며, 복귀 시점에 진동 알림을 보낸다. 일시 퇴장 시간은 경기 시계 기준으로 흐르고 휴식 시간에는 멈춘다.
- **인원 경고 (Law 3.1, `minimumPlayers`):** 퇴장(일시 퇴장 포함)으로 한 팀의 필드 인원이 최소 인원 미만이 되면 "경기 지속 불가" 경고와 함께 `abandonMatch`를 제안한다.
- **교체 한도 (Law 3.2):** `substitutionLimit` + (연장 시) `extraTimeExtraSub`를 넘는 교체가 기록되면 경고만 표시하고 기록은 막지 않는다. `returnSubstitutions`가 켜져 있으면 검사하지 않는다.
- **승부차기 (Law 10.3):**
  - 동전 던지기 결과로 먼저 차는 팀을 입력한다(`setShootoutFirstTeam`).
  - 양 팀이 번갈아 차며, 엔진이 다음 차례 팀과 라운드를 계산한다.
  - 각 팀 `penaltyKicksPerTeam`회 이전이라도 한 팀이 남은 킥을 모두 넣어도 따라잡을 수 없으면 즉시 종료한다.
  - 그 후에도 동점이면 같은 횟수에서 한 골 앞설 때까지 계속한다.
  - 등번호를 입력한 경우, 해당 팀의 필드 인원 전원이 한 번씩 차기 전에 같은 선수가 두 번째로 차면 경고한다.
  - 승부차기 골은 경기 득점에 합산하지 않고 별도로 표시한다(예: `1 : 1 (PK 4 : 3)`).
- **알림:** 엔진은 `nextAlert(now) -> (time, kind)?`만 계산한다. 종류: `periodEnd`, `warningBeforeEnd`, `breakEndingSoon`(휴식 종료 N분 전, 프리셋 설정·기본 5분, 0이면 끔, 휴식 길이 이상이면 생략), `breakEnd`, `sinBinEnd`. 진동 실행은 앱 책임.

### 공용 시나리오 형식
```json
{
  "name": "전반 45분, 중단 2분 → 추가시간 2분",
  "config": { "periodCount": 2, "periodLength": 2700 },
  "commands": [
    { "type": "startPeriod", "t": 0 },
    { "type": "toggleStoppage", "t": 600 },
    { "type": "toggleStoppage", "t": 720 },
    { "type": "endPeriod", "t": 2820 }
  ],
  "expect": [
    { "at": 2820, "phase": "interval", "period": "1H", "stoppageTotals": [120] }
  ]
}
```
시간 단위는 초. `expect`의 각 항목은 `at` 시점까지의 명령을 적용한 **스냅샷 JSON의 부분 집합**이다(키 목록은 `shared/engine-scenarios/README.md`). Swift와 Kotlin 엔진이 같은 디렉터리의 시나리오를 모두 통과해야 한다.

