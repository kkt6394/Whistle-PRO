# M6-1: Kotlin 경기 엔진 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wear OS·Android 폰 앱이 함께 쓸 순수 Kotlin 경기 엔진(`android/core`)을 만들고, Swift 엔진과 같은 공용 시나리오(`shared/engine-scenarios/*.json`)를 모두 통과시킨다.

**Architecture:** Swift `RefereeCore`를 파일 단위로 1:1 옮긴다. 상태는 명령 로그를 리듀서로 재생해 얻고(`MatchEngine.reduce(rules, commands)`), 시간 의존 값은 `now`를 인자로 받는다. Swift의 값 타입 + `mutating`은 Kotlin에서 **불변 data class + 새 상태 반환**으로 바꾼다. 명령 하나가 실패하면 예외를 던지고 이전 상태를 그대로 쓰므로 "전부 적용되거나 전혀 적용되지 않음"이 자연히 지켜진다. Swift 테스트(`@Test` 96개)도 같은 이름으로 옮겨 규칙 하나하나의 일치를 확인한다.

**Tech Stack:** Kotlin 2.2 (JVM), Gradle 9 (Kotlin DSL, 버전 카탈로그), kotlinx.serialization-json, JUnit 5 + kotlin.test. Android 의존성 없음 — JVM 라이브러리라 에뮬레이터 없이 테스트하고, 이후 `:wear`·`:mobile` 모듈이 그대로 의존한다.

**Spec:** `docs/superpowers/specs/2026-10-12-referee-app-design.md` §3, §10 M6+ / 기준 문서: `shared/engine-scenarios/README.md` / 기준 구현: `apple/RefereeCore/Sources/RefereeCore/`

## Global Constraints

- 저장소 루트: 저장소 최상위. 브랜치 `m6-kotlin-engine`.
- Gradle 루트: `android/` (나중에 `:wear`, `:mobile`이 여기 추가된다). 이번 계획은 `:core`만 만든다.
- 테스트 실행: `cd android && ./gradlew :core:test`
- JDK: 따로 설치하지 않고 Android Studio 내장 JBR(21)을 쓴다.
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 바이트코드 타깃은 JVM 17 (Android 모듈이 의존할 수 있게). 툴체인 자동 다운로드는 쓰지 않는다.
- 패키지: `com.aidenstudio.whistlepro.core`
- **동작 기준은 Swift 구현이다.** 옮기다가 Swift 쪽 버그로 보이는 것을 찾으면 Kotlin에서 고치지 말고 멈춰서 보고한다. 고치기로 하면 시나리오를 먼저 추가하고 양쪽을 같이 고친다.
- `shared/engine-scenarios/`의 JSON은 이 계획에서 바꾸지 않는다(README 문서 보강만).
- 커밋 메시지에 `Co-Authored-By` trailer를 넣지 않는다.

### Swift → Kotlin 옮김 규칙

| Swift | Kotlin |
|---|---|
| `struct` + `mutating func` | `data class` (`val`, 불변 `List`) + 새 상태를 반환하는 함수 |
| `throws(EngineError)` | `EngineException(val error: EngineError)` 를 던짐 |
| `enum` with associated values (`Phase`, `EventPayload`, `CommandKind`) | `sealed interface` + `data class`/`data object` |
| `Int` (64비트) — **초 단위 계산 결과** | `Long`. `wholeSeconds`가 ±1조 초로 자르므로 Kotlin `Int`로는 넘친다. `elapsed`·`matchSecond`·`remaining`·`addedTime`·`stoppageTotal(s)`·`intervalRemaining`·일시 퇴장 `remaining`·`EventTime.matchSecond`는 모두 `Long` |
| `Int` — 규칙 값, 등번호, 횟수 | `Int` |
| `Double` 타임스탬프 | `Double` |
| `.rounded(.down)` / `.rounded(.up)` | `floor()` / `ceil()` 후 `toLong()` |
| `TeamPair<T>` (JSON 배열) | `data class TeamPair<T>(val home: T, val away: T)` + `get(team)`, `with(team, value)` |
| Optional 키 (`encodeIfPresent`) | 스냅샷 JSON에서 키 생략 |
| 계산 프로퍼티 (`score`, `shootout`, `resolvedEvents`) | `val ... get() =` (매번 계산, 저장하지 않음) |

이름은 Swift와 같게 둔다(`resolvedEvents`, `activeSinBins(at)`, `snapshot(at, warningBeforeEnd)` …). 나중에 두 구현을 나란히 놓고 비교하기 쉬워야 한다.

## Review Focus

1. **±1조 초 값** (손상된 타임스탬프) → Kotlin `Int` 오버플로 없이 Swift와 같은 값. → Task 4 `hugeTimestampsDoNotOverflow`
2. **실패한 명령의 원자성** (킥 순서 위반, 범위 밖 등번호) → 상태가 전혀 바뀌지 않고 `ignored`에만 남음. → Task 3 `rejectedCommandLeavesStateUnchanged`
3. **숫자 비교** (`2400` vs `2400.0`, 시나리오의 `at`·알림 `at`) → 값으로 같다고 판정. → Task 9 `jsonNumbersCompareByValue`
4. **null 기대값** (`"nextShootoutTeam": null`) → 키가 없거나 null이면 통과, 값이 있으면 실패. → Task 9 `nullExpectationMatchesMissingKey`
5. **id 없는 명령** → 디코더는 UUID, 러너는 원본에 id가 없을 때만 `#i`. → Task 2 `missingIdsAreUnique`, Task 9 러너
6. **RuleSet 부분 JSON의 명시적 null** (`"minimumPlayers": null`) → 끔(null), 키가 없으면 기본값 7. → Task 1 `explicitNullTurnsOffOptionalRules`

---

## File Structure

```
android/
├─ .gitignore                       # .gradle/, build/, local.properties, .idea/, .kotlin/
├─ settings.gradle.kts              # rootProject "WhistlePRO", include(":core")
├─ build.gradle.kts                 # 플러그인 선언만 (apply false)
├─ gradle.properties                # org.gradle.jvmargs, kotlin.code.style=official
├─ gradle/libs.versions.toml        # 버전 카탈로그
├─ gradle/wrapper/                  # gradle-wrapper.jar, gradle-wrapper.properties (커밋)
├─ gradlew, gradlew.bat
└─ core/
   ├─ build.gradle.kts              # kotlin("jvm"), kotlin("plugin.serialization"), JUnit 5
   └─ src/
      ├─ main/kotlin/com/aidenstudio/whistlepro/core/
      │  ├─ Team.kt                 # Team, TeamPair            ← Team.swift
      │  ├─ RuleSet.kt              # RuleSet, Field, validate, deviations, JSON  ← RuleSet.swift
      │  ├─ Command.kt              # CardKind, EventPayload, CommandKind, Command, JSON  ← Command.swift
      │  ├─ MatchState.kt           # PeriodRef, Phase, PeriodRecord, EventTime, RecordedEvent, EngineError, MatchState
      │  ├─ MatchStateApply.kt      # apply + 기간 전환          ← MatchState+Apply.swift
      │  ├─ MatchStateDerived.kt    # score, kickoffTeam, intervalLength, currentPeriodLabel
      │  ├─ MatchStateClock.kt      # wholeSeconds, ClockReading, clock, stoppage, intervalRemaining
      │  ├─ MatchStateEvents.kt     # 기록·수정·삭제
      │  ├─ MatchStateResolution.kt # ResolvedEvent, 경고 누적, 교체 한도, 인원, 경기 경고
      │  ├─ MatchStateSinBin.kt     # ActiveSinBin, playedSeconds, activeSinBins
      │  ├─ MatchStateShootout.kt   # ShootoutStatus, 차례, 재개·종료
      │  ├─ MatchStateAlerts.kt     # AlertKind, MatchAlert, alerts, nextAlert
      │  ├─ MatchSnapshot.kt        # 스냅샷 + toJson()
      │  └─ MatchEngine.kt          # effectiveCommands, reduce
      └─ test/kotlin/com/aidenstudio/whistlepro/core/
         ├─ TestHelpers.kt          # cmd, play, goal, card, sub, twoHalves  ← TestHelpers.swift
         ├─ RuleSetTests.kt  CommandCodingTests.kt  PhaseTransitionTests.kt  ClockTests.kt
         ├─ EventTests.kt  SinBinTests.kt  ShootoutTests.kt  AlertTests.kt
         ├─ ReviewFixTests.kt  BacklogEngineTests.kt
         ├─ JsonMatch.kt            # 시나리오 기대값 비교 (값 비교, null=없음)
         └─ ScenarioTests.kt        # shared/engine-scenarios/*.json 동적 테스트
```

각 Task는 **Swift 테스트 파일을 먼저 옮겨 실패를 확인 → 소스 옮김 → 통과 → 커밋** 순서다. Swift 테스트의 `@Test` 이름을 Kotlin 함수 이름으로 그대로 쓴다(백틱 없이 camelCase). 테스트 수가 Swift와 같아야 한다(파일별 개수는 각 Task에 적음).

---

### Task 1: Gradle 골격 + Team + RuleSet

**Files:** `android/` 골격 전체, `core/.../Team.kt`, `RuleSet.kt`, `test/.../RuleSetTests.kt`, `TestHelpers.kt`(빈 파일로 시작)

- [ ] **Step 1: Gradle 래퍼 만들기**

  Gradle이 설치돼 있지 않으므로 Homebrew로 한 번 설치해 래퍼만 만든다(이후에는 `./gradlew`만 쓴다).

  ```bash
  brew install gradle
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  mkdir -p android && cd android
  gradle wrapper --gradle-version <brew로 설치된 9.x 버전>
  ```

- [ ] **Step 2: 빌드 파일**

  `gradle/libs.versions.toml` — 실행 시점의 최신 안정판으로 고정한다(아래는 기준값).

  ```toml
  [versions]
  kotlin = "2.2.20"
  serialization = "1.9.0"
  junit = "5.13.4"

  [libraries]
  serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
  junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
  junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
  junit-launcher = { module = "org.junit.platform:junit-platform-launcher" }

  [plugins]
  kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
  kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
  ```

  `core/build.gradle.kts`

  ```kotlin
  import org.jetbrains.kotlin.gradle.dsl.JvmTarget

  plugins {
      alias(libs.plugins.kotlin.jvm)
      alias(libs.plugins.kotlin.serialization)
  }

  java {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
      compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }

  dependencies {
      implementation(libs.serialization.json)
      testImplementation(kotlin("test"))
      testImplementation(platform(libs.junit.bom))
      testImplementation(libs.junit.jupiter)
      testRuntimeOnly(libs.junit.launcher)
  }

  tasks.test {
      useJUnitPlatform()
      // 공용 시나리오 위치 (저장소 루트/shared/engine-scenarios)
      systemProperty("scenarioDir", rootProject.projectDir.resolve("../shared/engine-scenarios").canonicalPath)
      testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
  }
  ```

  루트 `build.gradle.kts`는 두 플러그인을 `apply false`로 선언만 한다. `settings.gradle.kts`는 `pluginManagement`/`dependencyResolutionManagement`에 `mavenCentral()`, `gradlePluginPortal()`(google()은 Android 모듈을 붙일 때 추가).

- [ ] **Step 3: RuleSetTests 옮기기 (7개) → 실패 확인**

  `RuleSetTests.swift`의 7개 테스트를 옮긴다. 추가로 Review Focus 6번 `explicitNullTurnsOffOptionalRules`가 Swift 쪽에 같은 내용으로 있는지 확인하고, 없으면 Kotlin에만 추가 테스트로 넣는다(Swift 개수 + 추가분을 Task 끝에 기록).

  ```bash
  ./gradlew :core:test   # 컴파일 실패 = 예상된 실패
  ```

- [ ] **Step 4: Team.kt, RuleSet.kt**

  ```kotlin
  @Serializable
  enum class Team {
      @SerialName("home") HOME, @SerialName("away") AWAY;
      val opponent: Team get() = if (this == HOME) AWAY else HOME
      /** JSON·스냅샷 문자열 ("home"/"away") */
      val code: String get() = name.lowercase()
  }

  /** 홈/원정 한 쌍. JSON으로는 [home, away] 배열. */
  data class TeamPair<T>(val home: T, val away: T) {
      operator fun get(team: Team): T = if (team == Team.HOME) home else away
      fun with(team: Team, value: T): TeamPair<T> = if (team == Team.HOME) copy(home = value) else copy(away = value)
  }
  ```

  `RuleSet`은 Swift와 같은 18개 필드·기본값의 `data class`. `companion object { val FIFA_STANDARD = RuleSet() }`.
  - `enum class Field` — 선언 순서를 Swift `Field`와 같게 (deviations 순서가 여기에 의존).
  - `value(field): String` — Bool은 `"true"/"false"`, null은 `"off"`/`"unlimited"`.
  - `validate()` — 범위 밖이면 `RuleSetException(field)`.
  - JSON: kotlinx 자동 직렬화로는 "키 없음 = 기본값, 명시적 null = 끔"을 구분하기 어려우므로 `JsonObject`를 직접 읽는 `RuleSet.fromJson(JsonObject)` / `toJson(): JsonObject`를 쓴다. `toJson`은 선택 항목도 `null`을 명시한다(Swift encode와 동일).

- [ ] **Step 5: 통과 확인 후 커밋**

  ```bash
  ./gradlew :core:test
  git add android && git commit -m "feat(android): Gradle 골격, Kotlin Team·RuleSet"
  ```

---

### Task 2: 명령 모델 + JSON

**Files:** `Command.kt`, `test/.../CommandCodingTests.kt`, `TestHelpers.kt`

- [ ] **Step 1: CommandCodingTests 옮기기 (7개) → 실패 확인**
- [ ] **Step 2: Command.kt**

  ```kotlin
  enum class CardKind(val code: String) { YELLOW("yellow"), RED("red"), SIN_BIN("sinBin") }

  sealed interface EventPayload {
      val team: Team
      data class Goal(override val team: Team, val number: Int?, val ownGoal: Boolean) : EventPayload
      data class Card(override val team: Team, val number: Int?, val kind: CardKind) : EventPayload
      data class Substitution(override val team: Team, val numberOut: Int?, val numberIn: Int?) : EventPayload
      data class PenaltyKick(override val team: Team, val number: Int?, val scored: Boolean) : EventPayload
  }

  sealed interface CommandKind {
      data class SetKickoffTeam(val team: Team) : CommandKind
      data object StartPeriod : CommandKind
      data object EndPeriod : CommandKind
      data class AdjustPeriodLength(val seconds: Int) : CommandKind
      data object ToggleStoppage : CommandKind
      data class SetShootoutFirstTeam(val team: Team) : CommandKind
      data class RecordEvent(val payload: EventPayload) : CommandKind
      data class EditEvent(val id: String, val payload: EventPayload) : CommandKind
      data class DeleteEvent(val id: String) : CommandKind
      data object Undo : CommandKind
      data class AbandonMatch(val reason: String) : CommandKind
  }

  data class Command(val id: String = UUID.randomUUID().toString(), val timestamp: Double, val kind: CommandKind)
  ```

  - `typeName`, `recordCommandType`, `typeName(forRecordCommand)` 를 Swift와 같게.
  - JSON: `Command.fromJson(JsonObject)` / `toJson()` — 평평한 형식(`{id, t, type, team, ...}`), `editEvent`의 `event`는 `{type, ...필드}`. 형식은 Swift `Command: Codable`과 바이트 단위가 아니라 **키·값 단위로** 같으면 된다. `id`가 없으면 UUID, `abandonMatch`의 `reason`이 없으면 `""`, `goal`의 `ownGoal`이 없으면 `false`. 알 수 없는 type은 `SerializationException`.
  - `List<Command>` ↔ JSON 배열 헬퍼(`commandsFromJson`, `commandsToJson`) — 나중에 워치 로그 저장·동기화에 쓴다.
- [ ] **Step 3: TestHelpers.kt** — Swift `TestHelpers.swift`의 `cmd`, `play`(규칙 있음/없음), `goal`, `card`, `sub`, `twoHalves`를 같은 기본값으로.
- [ ] **Step 4: 통과 확인 후 커밋** — `feat(android): 명령 모델과 JSON`

---

### Task 3: 경기 상태 + 기간 전환 + 로그 재생(undo)

**Files:** `MatchState.kt`, `MatchStateApply.kt`, `MatchStateDerived.kt`, `MatchEngine.kt`, `test/.../PhaseTransitionTests.kt`

- [ ] **Step 1: PhaseTransitionTests 옮기기 (13개) + `rejectedCommandLeavesStateUnchanged` 추가 → 실패 확인**

  추가 테스트: 승부차기에서 차례가 아닌 팀의 킥, 등번호 100인 골을 넣은 로그를 재생해 `ignored`에 두 id가 있고 `events`·`phase`가 그 명령이 없을 때와 같은지 확인.

- [ ] **Step 2: 상태 타입**

  ```kotlin
  data class PeriodRef(val kind: Kind, val index: Int) {
      enum class Kind { REGULAR, EXTRA }
      fun label(periodCount: Int): String = when (kind) {
          Kind.REGULAR -> if (periodCount == 4) "Q$index" else "${index}H"
          Kind.EXTRA -> "ET$index"
      }
  }

  sealed interface FinishReason {
      data object Completed : FinishReason
      data class Abandoned(val reason: String) : FinishReason
  }

  sealed interface Phase {
      data object NotStarted : Phase
      data class Period(val ref: PeriodRef) : Phase
      data class Interval(val after: PeriodRef) : Phase
      data object PreExtraTime : Phase
      data object Penalties : Phase
      data class Finished(val reason: FinishReason) : Phase
  }

  enum class EngineError { INVALID_IN_PHASE, INVALID_VALUE, UNKNOWN_EVENT, SHOOTOUT_FIRST_TEAM_MISSING, NOT_YOUR_TURN }
  class EngineException(val error: EngineError) : Exception(error.name)

  data class MatchState(
      val rules: RuleSet,
      val phase: Phase = Phase.NotStarted,
      val periods: List<PeriodRecord> = emptyList(),
      val events: List<RecordedEvent> = emptyList(),
      val regularKickoff: Team? = null,
      val extraKickoff: Team? = null,
      val shootoutFirstTeam: Team? = null,
      val regularPeriodLength: Int = rules.periodLength,
      val extraPeriodLength: Int = rules.extraTimePeriodLength,
      val ignoredCommandIds: List<String> = emptyList(),
  )
  ```

  `PeriodRecord.nominalLength`·`offset`은 규칙 값이라 `Int`, `StoppageInterval(start: Double, end: Double?)`.

- [ ] **Step 3: apply·reduce**

  ```kotlin
  /** 명령 하나를 적용한 새 상태. 허용되지 않으면 EngineException (이 상태는 그대로). */
  fun MatchState.apply(command: Command): MatchState = when (val k = command.kind) {
      is CommandKind.SetKickoffTeam -> setKickoff(k.team)
      CommandKind.StartPeriod -> startPeriod(command.timestamp)
      // ... Swift applyUnchecked와 같은 분기
      CommandKind.Undo -> throw EngineException(EngineError.INVALID_IN_PHASE)
  }

  object MatchEngine {
      fun effectiveCommands(commands: List<Command>): List<Command> { /* Swift와 같은 스택 */ }

      fun reduce(rules: RuleSet, commands: List<Command>): MatchState =
          effectiveCommands(commands).fold(MatchState(rules)) { state, command ->
              try { state.apply(command) } catch (e: EngineException) {
                  state.copy(ignoredCommandIds = state.ignoredCommandIds + command.id)
              }
          }
  }
  ```

  내부 헬퍼(`closeRunningPeriod`, 마지막 기간 교체)는 `periods.dropLast(1) + updated` 형태의 private 확장 함수로. `MatchStateDerived.kt`에 `score`, `kickoffTeam`, `intervalLength(after)`, `currentPeriodLabel` (`currentPeriodLabel`은 Task 7의 `shootout`이 필요하므로 이 Task에서는 킥 수 조건을 `events.any { it.payload is PenaltyKick }`로 두지 말고, Task 7에서 `shootout.kicks` 기반으로 바꾼다 — 두 표현이 같은 결과인지 Task 7 테스트로 확인).

- [ ] **Step 4: 통과 확인 후 커밋** — `feat(android): 경기 상태, 기간 전환, undo 재생`

---

### Task 4: 시계 + 추가시간(중단) + 시간 조정

**Files:** `MatchStateClock.kt`, `test/.../ClockTests.kt`

- [ ] **Step 1: ClockTests 옮기기 (12개) + `hugeTimestampsDoNotOverflow` 추가 → 실패 확인**

  추가 테스트: `startPeriod` at 0, `now = 5e12`, `now = Double.POSITIVE_INFINITY`, `Double.NaN`에서 `clock(now)`의 `elapsed`가 각각 `1_000_000_000_000L`, `0L`, `0L`이고 예외가 없다.

- [ ] **Step 2: 구현**

  ```kotlin
  /** 초 단위 값을 Long으로. 무한대·NaN은 0, ±1조 초로 자른 뒤 내림(기본) 또는 올림. */
  fun wholeSeconds(value: Double, roundUp: Boolean = false): Long {
      if (!value.isFinite()) return 0
      val clamped = value.coerceIn(-1e12, 1e12)
      return (if (roundUp) ceil(clamped) else floor(clamped)).toLong()
  }
  ```

  `ClockReading`의 시간 필드는 `Long`. `matchSecond = offset.toLong() + elapsed`. `toggleStoppage`는 이 파일에.

- [ ] **Step 3: 통과 확인 후 커밋** — `feat(android): 경기 시계, 중단 시간, 기간 길이 조정`

---

### Task 5: 이벤트 기록·수정·삭제 + 경고 누적 + 교체 한도 + 인원 경고

**Files:** `MatchStateEvents.kt`, `MatchStateResolution.kt`, `test/.../EventTests.kt`

- [ ] **Step 1: EventTests 옮기기 (16개) → 실패 확인**
- [ ] **Step 2: 구현**
  - `ResolvedEventKind`, `EventWarning`의 JSON 문자열(`goal`, `ownGoal`, `secondYellow`, `substitutionLimitExceeded` …)을 `code` 프로퍼티로 둔다. 스냅샷이 이 값을 쓴다.
  - `resolvedEvents`는 Swift처럼 지역 가변 집합(`mutableSetOf<Pair<Team, Int>>()`)으로 한 번 훑는다. 계산 순서·조건을 Swift와 한 줄씩 대조한다(특히 `playerAlreadySentOff`일 때 `cautioned`에 넣지 않는 것, 퇴장 인원 증가 위치).
  - `editEvent`·`deleteEvent`는 Task 7의 `shootoutIsEditable`·`reopenShootoutIfUndecided`·`finishShootoutIfDecided`가 필요하다. 이 Task에서는 `MatchStateShootout.kt`에 그 세 함수와 `shootout`만 먼저 옮기고, 승부차기 테스트는 Task 7에서 한다.
  - `MatchWarning.BelowMinimumPlayers(team).code` = `"belowMinimumPlayers:home"`.
- [ ] **Step 3: 통과 확인 후 커밋** — `feat(android): 이벤트 기록·수정·삭제, 경고 누적, 교체 한도`

---

### Task 6: 일시 퇴장

**Files:** `MatchStateSinBin.kt`, `test/.../SinBinTests.kt`

- [ ] **Step 1: SinBinTests 옮기기 (5개) → 실패 확인**
- [ ] **Step 2: 구현** — `ActiveSinBin.remainingSeconds: Double`, `remaining: Long` (올림). `playersOnField(now)`가 이 결과를 쓰도록 Task 5 코드와 연결.
- [ ] **Step 3: 통과 확인 후 커밋** — `feat(android): 일시 퇴장`

---

### Task 7: 승부차기

**Files:** `MatchStateShootout.kt`, `MatchStateDerived.kt`(라벨), `test/.../ShootoutTests.kt`

- [ ] **Step 1: ShootoutTests 옮기기 (12개) → 실패 확인** (Task 5에서 옮긴 함수 덕분에 일부는 이미 통과할 수 있다. 실패하는 것만 확인)
- [ ] **Step 2: 구현** — `setShootoutFirstTeam`, `checkShootoutTurn`, `currentPeriodLabel`을 `shootout.kicks.home + shootout.kicks.away > 0` 조건으로.
- [ ] **Step 3: 통과 확인 후 커밋** — `feat(android): 승부차기`

---

### Task 8: 진동 알림 시각

**Files:** `MatchStateAlerts.kt`, `test/.../AlertTests.kt`

- [ ] **Step 1: AlertTests 옮기기 (7개) → 실패 확인**
- [ ] **Step 2: 구현** — `AlertKind.code` (`warningBeforeEnd` …). 정렬은 `sortedBy { it.at }` (Swift `sorted`와 마찬가지로 같은 시각의 순서는 시나리오가 의존하지 않음 — 안정 정렬이라 추가 순서대로 남는다).
- [ ] **Step 3: 통과 확인 후 커밋** — `feat(android): 알림 시각 계산`

---

### Task 9: 스냅샷 + 공용 시나리오 러너 + 리뷰·백로그 테스트

**Files:** `MatchSnapshot.kt`, `test/.../JsonMatch.kt`, `ScenarioTests.kt`, `ReviewFixTests.kt`, `BacklogEngineTests.kt`

- [ ] **Step 1: 테스트 옮기기 → 실패 확인**
  - `ReviewFixTests` (7개), `BacklogEngineTests` (7개), `ScenarioTests`의 `scenarioDirectoryIsFound`, `snapshotEncodesTeamPairsAsArraysAndOmitsNil` (2개 + 시나리오 파일별 동적 테스트)
  - `JsonMatch`용 테스트 2개: `jsonNumbersCompareByValue` (`2400` == `2400.0`, `[1, 0]` == `[1.0, 0]`, `1` != `"1"`), `nullExpectationMatchesMissingKey`

- [ ] **Step 2: MatchSnapshot**

  Swift `MatchSnapshot`과 같은 필드의 data class + `toJson(): JsonObject`. 값이 null인 선택 키는 **넣지 않는다**. `score`·`shootoutScore`·`playersOnField`·`substitutions`는 `[home, away]` 배열, `events[].number` 등 선택 필드도 null이면 생략.

- [ ] **Step 3: 시나리오 러너**

  ```kotlin
  /** 기대값 비교: 숫자는 값으로, 기대가 null이면 실제가 없거나 null, 배열·객체는 전체 일치. */
  fun jsonMatches(actual: JsonElement?, expected: JsonElement): Boolean = when {
      expected is JsonNull -> actual == null || actual is JsonNull
      actual == null -> false
      expected is JsonPrimitive && actual is JsonPrimitive ->
          if (expected.isString || actual.isString) expected.isString == actual.isString && expected.content == actual.content
          else expected.doubleOrNull?.let { it == actual.doubleOrNull } ?: (expected.booleanOrNull == actual.booleanOrNull)
      expected is JsonArray && actual is JsonArray ->
          expected.size == actual.size && expected.indices.all { jsonMatches(actual[it], expected[it]) }
      expected is JsonObject && actual is JsonObject ->
          expected.keys == actual.keys && expected.all { (k, v) -> jsonMatches(actual[k], v) }
      else -> false
  }
  ```

  주의: 중첩 객체(`events[]`, `sinBins[]`)는 "전체가 같아야" 하므로 키 집합도 같아야 한다. 이때 Swift 스냅샷이 생략하는 선택 키(예: `number` 없음)를 Kotlin도 생략해야 통과한다 — Step 2의 생략 규칙이 여기서 검증된다.

  ```kotlin
  class ScenarioTests {
      private val dir = File(System.getProperty("scenarioDir"))
      private val files get() = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

      @Test fun scenarioDirectoryIsFound() { assertTrue(files.size >= 10, "시나리오 디렉터리: $dir") }

      @TestFactory fun scenarios() = files.map { file ->
          DynamicTest.dynamicTest(file.name) {
              val raw = Json.parseToJsonElement(file.readText()).jsonObject
              val rules = RuleSet.fromJson(raw["config"]?.jsonObject ?: JsonObject(emptyMap()))
              val warning = raw["warningBeforeEnd"]?.jsonPrimitive?.int ?: 300
              val rawCommands = raw.getValue("commands").jsonArray
              val commands = rawCommands.mapIndexed { i, el ->
                  val c = Command.fromJson(el.jsonObject)
                  if ("id" in el.jsonObject) c else c.copy(id = "#$i")
              }
              for (expected in raw.getValue("expect").jsonArray.map { it.jsonObject }) {
                  val at = expected.getValue("at").jsonPrimitive.double
                  val state = MatchEngine.reduce(rules, commands.filter { it.timestamp <= at })
                  val actual = state.snapshot(at, warning).toJson()
                  for ((key, value) in expected) if (key != "at") {
                      assertTrue(jsonMatches(actual[key], value),
                          "${raw["name"]} @$at $key: 실제 ${actual[key]}, 기대 $value")
                  }
              }
          }
      }
  }
  ```

  실패 메시지는 Swift 러너와 같은 형식이다(어느 시나리오의 어느 시점·키인지 바로 보이게).

- [ ] **Step 4: 전체 통과 확인**

  ```bash
  ./gradlew :core:test
  ```

  기대: 시나리오 10개 동적 테스트 + 단위 테스트 전부 통과. Gradle 테스트 리포트(`core/build/test-results/test/*.xml`)에서 테스트 수를 세어 Swift `@Test` 96개(시나리오 매개변수 테스트 1개 포함, 이것은 Kotlin에서 파일별 동적 테스트 10개가 됨) + Kotlin 추가분과 맞는지 기록한다.

- [ ] **Step 5: 커밋** — `feat(android): 스냅샷과 공용 시나리오 러너 — 시나리오 10개 통과`

---

### Task 10: 문서

**Files:** `README.md`, `shared/engine-scenarios/README.md`, `android/README.md`(새로)

- [ ] **Step 1:** 루트 `README.md`에 Kotlin 엔진 줄 추가: `Kotlin 엔진: android/core — cd android && ./gradlew :core:test (JAVA_HOME = Android Studio JBR)`
- [ ] **Step 2:** `shared/engine-scenarios/README.md` 맨 위에 **엔진 규칙을 바꿀 때의 순서**를 적는다: ① 시나리오 추가(실패 확인) → ② Swift·Kotlin 양쪽 수정 → ③ 두 테스트 모두 통과. 한쪽만 고치는 커밋을 만들지 않는다. 그리고 실행 명령 두 줄(Swift, Kotlin).
- [ ] **Step 3:** `android/README.md` — 모듈 구성(현재 `:core`, 예정 `:wear`·`:mobile`), JDK 설정, Swift→Kotlin 옮김 규칙 표(이 계획의 표를 옮김, 특히 초 단위 `Long`).
- [ ] **Step 4: 커밋** — `docs: Kotlin 엔진 실행법과 엔진 규칙 변경 순서`

---

## 완료 기준 (스펙 §10 M6+의 첫 단계)

- `cd android && ./gradlew :core:test` 전체 통과
- `shared/engine-scenarios/*.json` 10개 모두 통과 (Swift와 같은 파일, 수정 없음)
- Swift `@Test` 96개가 같은 이름으로 모두 옮겨짐
- `swift test --package-path apple/RefereeCore` 여전히 통과 (Swift 쪽 변경 없음 확인)
- opus 전체 리뷰 → Important 이상 수정 → `main`에 `--no-ff` 병합 후 push

## 이번 계획에서 하지 않는 것

- `RefereeSession`(동기화, 표시 문자열, 보고서, 운동 분석, 구독 판정)의 Kotlin 이식 → Wear OS 앱 계획(M6-2)에서 필요한 만큼 옮긴다.
- Swift·Kotlin 무작위 로그 교차 비교(퍼징). 시나리오와 옮긴 단위 테스트로 충분한지 리뷰 결과를 보고 따로 판단한다.
- CI. 지금은 로컬에서 두 테스트를 돌린다.
