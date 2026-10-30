# 히트맵에 경기장 넣기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 경기 상세 지도에서 경기장 네 모서리를 찍어 경기장을 정한다. 정한 경기장은 같은 구장 경기에 다시 쓰고, 히트맵을 [위성 | 경기장] 두 보기와 SNS 카드에 경기장 기준으로 보여 준다. 안드로이드 폰과 아이폰 둘 다.

**Architecture:**
- 순수 계산은 코틀린 `android/session`과 스위프트 `apple/RefereeCore/Sources/RefereeSession`에 같은 동작·같은 테스트로 넣는다.
  - 기하: 모서리 → 직사각형, 경기장 좌표
  - 자동 추정
  - 라인 치수
  - 경기장 좌표 히트 격자
  - 구장 저장·판정
- 화면은 각 플랫폼 지도 위에 얹는다.
  - 안드로이드: maps-compose `GoogleMap`
  - 아이폰: `MKMapView`
- 바탕 사진 선택(위성 / 브이월드 타일 / 일반 지도)은 플랫폼마다 한 곳(`Basemap`)에 모은다.

**Tech Stack:** Kotlin·Jetpack Compose·maps-compose(Google Maps SDK), Swift·SwiftUI·MapKit, kotlinx.serialization JSON(직접 읽고 쓰기), Swift Codable

**Spec:** `docs/superpowers/specs/2026-10-28-pitch-heatmap-design.md` (먼저 끝까지 읽는다)

## Global Constraints

- 공통 계산은 코틀린·스위프트 **같은 함수 이름, 같은 테스트 이름과 값**. 한쪽만 바꾸지 않는다.
- 저장 파일 `pitches.json`은 경기 보관함 폴더(노트 `notes.json` 옆)에 두고, 두 플랫폼이 **같은 JSON**을 쓴다(아래 Task 3 형식). 워치로 보내지 않는다.
- 히트맵·자동 추정·구장 판정은 **기간 중 위치**(`Heatmap.duringPeriods`)이면서 정확도 20 m 이하(`WorkoutAnalysis.MAXIMUM_ACCURACY`)인 지점만 쓴다.
- 박스·서클 정규 치수(m): 센터서클 반지름 9.15, 페널티 박스 40.32×16.5, 골 에어리어 18.32×5.5, 페널티 마크 11.
- [위성] 보기에는 테두리와 하프라인만 그린다. 박스·서클은 [경기장] 보기와 SNS 카드에만, 테두리보다 흐리게 그린다.
- 경기장이 없는 경기의 지도 탭은 지금과 똑같아야 한다. [경기장 맞추기] 버튼만 더해진다.
- 한국(`HeatmapBounds.inKorea`)의 바탕은 다음 순서로 고른다.
  1. 브이월드 키가 있으면 브이월드 항공사진 타일
  2. 없거나 실패하면 다크 일반 지도 + 맞추기 화면 안내 "라인이 보이지 않아 오차가 클 수 있습니다"
- 한국 밖은 Google·Apple 위성을 쓴다.
- 브이월드 키 보관:
  - 안드로이드: `android/local.properties`의 `VWORLD_KEY` → `BuildConfig.VWORLD_KEY`(없으면 빈 문자열)
  - 아이폰: git에 올리지 않는 `apple/Config/Secrets.xcconfig`의 `VWORLD_KEY` → Info.plist `VWorldKey`(없거나 비면 사용 안 함)
  - 키는 git에 올리지 않는다.
- 브이월드 사진을 쓸 때는 지도 구석에 출처 "© 브이월드(국토교통부)"를 표기한다(약관 제13조).
- 새 문구는 한국어 키로 쓴다. `apple/i18n/*.json` 8개 언어(en, th, vi, id, pt-BR, es, ja, zh-Hans)에 번역을 넣고 다음을 돌린다.
  - `python3 android/scripts/gen_wear_strings.py --module mobile`
  - 아이폰 generic 빌드 뒤 `python3 apple/scripts/sync_strings.py apple/.build/DDsync WhistlePRO apple/RefereeiOS/Localizable.xcstrings apple/i18n`
  - 용어는 `docs/i18n/glossary.md`를 따른다.
- 커밋 메시지에 `Co-Authored-By` 줄을 넣지 않는다.
- 빌드 환경: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. 테스트 명령은 다음과 같다.
  - 코틀린 공통: `cd android && ./gradlew :session:test`
  - 폰: `:mobile:testDebugUnitTest`
  - 스위프트 공통: `cd apple/RefereeCore && swift test`

## Review Focus

1. **모서리를 순서 없이 찍음**(대각선 순서, 시계·반시계 섞임). 꼬인 사각형이 아니라 같은 경기장으로 다듬어져야 한다 → Task 1 `cornersInAnyOrderGiveSamePitch`.
2. **이동 경로가 거의 없음**(0~9개 지점, GPS 꺼짐). 맞추기 화면이 죽지 않고 빈 상태로 열려야 한다 → Task 2 `tooFewPointsGiveNoEstimate`, Task 6·9 빈 상태 열기.
3. **`pitches.json`이 깨짐·모르는 키**. 지운 것처럼 다뤄 경기 상세가 열리고, 저장하면 새 파일로 덮는다 → Task 3 `corruptFileActsEmptyAndSaveRecovers`.
4. **이벤트 핀·히트가 경기장 밖**(터치라인 밖 워밍업, GPS 튐). 여백(8 m) 안은 그리고, 그 밖은 버린다. 그림이 깨지지 않는다 → Task 1 `localCoordinatesOutsidePitch`, Task 4 `heatOutsideMarginIsDropped`.
5. **아주 작은 구장**(풋살 40×20). 박스가 경기장을 넘지 않게 줄어든다 → Task 1 `smallPitchScalesMarkings`.

---

### Task 1: 경기장 기하 (코틀린·스위프트)

**Files:**
- Create: `android/session/src/main/kotlin/com/aidenstudio/whistlepro/session/Pitch.kt`
- Create: `apple/RefereeCore/Sources/RefereeSession/Pitch.swift`
- Test: `android/session/src/test/kotlin/com/aidenstudio/whistlepro/session/PitchTests.kt`, `apple/RefereeCore/Tests/RefereeSessionTests/PitchTests.swift`

**Interfaces (Produces):**
- `data class GeoPoint(val latitude: Double, val longitude: Double)` / `public struct GeoPoint: Codable, Equatable, Hashable, Sendable { latitude, longitude }`
- `data class PitchPoint(val x: Double, val y: Double)`
  - `x`: 왼쪽 터치라인에서 오른쪽으로 m (0…width)
  - `y`: 위쪽 골라인(A)에서 아래로 m (0…length)
  - 스위프트도 같은 이름이다.
- `data class Pitch(val center: GeoPoint, val length: Double, val width: Double, val bearing: Double)`
  - `bearing`: 골라인 A→B 방향의 방위각(도, 북=0, 시계 방향, 0..<360)
  - `val corners: List<GeoPoint>`: 순서는 A왼쪽, A오른쪽, B오른쪽, B왼쪽
  - `fun local(p: GeoPoint): PitchPoint`
  - `fun geo(p: PitchPoint): GeoPoint`
  - `fun contains(p: GeoPoint, margin: Double): Boolean`
  - `companion object { fun fromCorners(corners: List<GeoPoint>): Pitch? }`: 정확히 4개가 아니거나 넓이가 100 m² 미만이면 null
- `data class PitchMarkings(val scale: Double)`
  - 값: `centerCircleRadius`·`penaltyAreaWidth`·`penaltyAreaDepth`·`goalAreaWidth`·`goalAreaDepth`·`penaltyMarkDistance` = 정규 치수 × scale
  - `companion object { fun of(length: Double, width: Double): PitchMarkings }`

- [ ] **Step 1: 실패하는 테스트 작성 (두 언어 같은 이름·값)**

```kotlin
@Test fun rectangleCornersGivePitch()          // 위도 37.5·경도 127.0 중심, 105×68, bearing 30°로 만든 corners → fromCorners: length 105±0.5, width 68±0.5, bearing 30±0.5, center ±0.5 m
@Test fun cornersInAnyOrderGiveSamePitch()     // 같은 네 점을 [0,2,1,3]·[3,2,1,0] 순서로 → 길이·너비·중심 같음(±0.5)
@Test fun firstCornerSideIsGoalLineA()         // corners[0]이 local()에서 y≈0 (±0.5) — 첫 번째로 찍은 점 쪽이 위
@Test fun skewedQuadIsSquared()                // 한 모서리를 3 m 밀어도 length·width ±2 m
@Test fun degenerateCornersGiveNull()          // 3개, 또는 네 점이 한 줄 → null
@Test fun localAndGeoRoundTrip()               // geo(local(p)) == p (±0.01 m), corners의 local은 (0,0),(68,0),(68,105),(0,105) ±0.5
@Test fun localCoordinatesOutsidePitch()       // 왼쪽 터치라인 밖 5 m 지점 → x≈-5; contains(margin 8)=true, contains(margin 2)=false
@Test fun standardPitchKeepsMarkings()         // of(105,68).scale == 1.0, of(90,60).scale == 1.0
@Test fun smallPitchScalesMarkings()           // of(40,20).scale ≈ 0.397 (±0.01), penaltyAreaWidth ≈ 16.0
```

- [ ] **Step 2: 실행해 실패 확인**

Run: `cd android && ./gradlew :session:test --tests '*PitchTests'` 그리고 `cd apple/RefereeCore && swift test --filter PitchTests`
Expected: 컴파일 실패(Pitch 없음)

- [ ] **Step 3: 구현**

- 좌표는 중심 기준 평면 근사를 쓴다. 북쪽 m = Δlat×111 320, 동쪽 m = Δlon×111 320×cos(중심 위도). 기존 `WorkoutAnalysis.distance`와 같은 근사다.
- `fromCorners` 순서:
  1. 네 점의 무게중심을 구하고, 무게중심 기준 각도로 정렬한다(꼬인 순서를 푼다).
  2. 마주 보는 변 두 쌍의 평균 길이를 비교한다. 긴 쌍이 터치라인이고 그 평균이 length, 짧은 쌍의 평균이 width다.
  3. 방향은 두 터치라인 벡터의 평균이다.
  4. 원래 `corners[0]`에 가까운 쪽 골라인을 A로 둔다(`bearing`은 A→B).
- `PitchMarkings.of`: `scale = min(1.0, (width - 4) / 40.32, (length / 2 - 1) / (16.5 + 9.15))`를 0.2 이상으로 자른다.

- [ ] **Step 4: 두 쪽 테스트 통과 확인** (Step 2 명령, 전부 PASS)

- [ ] **Step 5: Commit** — `feat(pitch): 경기장 기하 — 네 모서리를 직사각형으로, 경기장 좌표, 라인 치수 (코틀린·스위프트)`

---

### Task 2: 이동 경로로 경기장 자동 추정

**Files:**
- Modify: `Pitch.kt`, `Pitch.swift` (같은 파일 끝에 `PitchEstimate`)
- Test: `PitchTests.kt`, `PitchTests.swift`

**Interfaces:**
- Consumes: Task 1 `Pitch`, `GeoPoint`
- Produces: `object PitchEstimate { fun of(points: List<GeoPoint>): Pitch? }` / `enum PitchEstimate { static func of(_ points: [GeoPoint]) -> Pitch? }`
  - 호출하는 쪽이 기간 중·정확도 20 m 이하 지점만 넘긴다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun estimateFitsCoveredRectangle()   // 100×60, bearing 30° 사각형 안을 1 m 간격으로 채운 점 → length 100±3, width 60±3, bearing 30±2 (또는 210±2)
@Test fun estimateIgnoresOutliers()        // 위 점들 + 500 m 떨어진 점 3개 → 결과 같음(±3)
@Test fun thinStripIsWidenedTo30()         // 100×4 띠(부심 터치라인) → width == 30.0 (±0.5), length 100±3
@Test fun tooFewPointsGiveNoEstimate()     // 0개·9개 → null
```

- [ ] **Step 2: 실패 확인** (Task 1과 같은 명령, `--tests '*PitchTests'`)

- [ ] **Step 3: 구현** — 아래 순서로 정한다(테스트가 정하지 않는 부분):
  1. 지점이 10개 미만이면 null.
  2. 지점들의 위도·경도 중앙값을 중심으로 삼는다. 중심에서의 거리가 95번째 백분위수를 넘는 점은 버린다.
  3. 0°부터 179°까지 1°씩 돌려 가며 축에 투영한 범위의 넓이가 가장 작은 각도를 고른다.
  4. 긴 쪽이 length다. width가 30 m 미만이면 중심 기준으로 양쪽을 넓혀 30 m로 만든다.
  5. A는 bearing이 0..<180인 쪽 끝으로 정한다. 그래야 같은 입력에 늘 같은 답이 나온다.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: Commit** — `feat(pitch): 이동 경로로 경기장 자동 추정 (최소 면적 직사각형, 튀는 점 제외, 띠는 30 m로)`

---

### Task 3: 구장 저장·같은 구장 판정 (`pitches.json`)

**Files:**
- Create: `android/session/src/main/kotlin/com/aidenstudio/whistlepro/session/PitchStore.kt`, `apple/RefereeCore/Sources/RefereeSession/PitchStore.swift`
- Test: `PitchStoreTests.kt`, `PitchStoreTests.swift` (같은 폴더)

**Interfaces:**
- Consumes: Task 1 `Pitch.fromCorners`, `Pitch.contains`, `GeoPoint`; 기존 `MatchLibrary`(코틀린 `directory`, `writeFileAtomically`, `parseJson`, `swiftString`; 스위프트 `directory`)
- Produces (MatchLibrary 확장, 두 언어 같은 이름):
  - `fun MatchLibrary.pitch(matchID: UUID, points: List<GeoPoint>): Pitch?`: 연결 → 지운 표시면 null → 자동 판정 순서. 파일에 쓰지 않는다.
  - `fun MatchLibrary.savePitch(matchID: UUID, corners: List<GeoPoint>, now: Double): Pitch?`: corners가 다듬어지지 않으면 null이고 저장하지 않는다.
  - `fun MatchLibrary.clearPitch(matchID: UUID)`
  - 스위프트는 `pitch(matchID:points:)`, `savePitch(matchID:corners:now:) throws -> Pitch?`, `clearPitch(matchID:) throws`
- JSON 형식(스위프트 Codable 기본 모양과 같다). UUID는 대문자 문자열(`swiftString`). `links`는 노트와 같은 "키, 값, 키, 값" 배열이다. 값이 `{}`이면 "이 경기에서 지움"이다.

```json
{"pitches":[{"id":"UUID","corners":[{"latitude":37.5,"longitude":127.0},…4개],"createdAt":1793000000.0}],
 "links":["MATCH-UUID",{"pitch":"UUID"},"MATCH-UUID-2",{}]}
```

- [ ] **Step 1: 실패하는 테스트** (임시 폴더의 `MatchLibrary`, 경기장은 Task 1 테스트 도우미로 만든 105×68)

```kotlin
@Test fun savedPitchIsLinkedToMatch()                  // savePitch(A) 뒤 pitch(A, 아무 점) == 저장한 경기장(±0.5 m)
@Test fun otherMatchOnSamePitchIsMatchedAutomatically() // 경기 B 점 60%가 경기장 안 → pitch(B) == 같은 경기장, 파일의 links에 B 없음
@Test fun matchMostlyOutsideIsNotMatched()              // 경기 C 점 40%만 안 → null
@Test fun marginOf15MetersCounts()                      // 모든 점이 터치라인 밖 10 m → 같은 경기장, 20 m → null
@Test fun bestOfSeveralPitchesWins()                    // 두 구장에 각각 55%·80% → 80% 쪽
@Test fun clearedMatchStaysCleared()                    // clearPitch(B) 뒤 점이 다 안이어도 null
@Test fun refittingNearbyUpdatesSharedPitch()           // A·B가 같은 구장, A에서 중심 10 m 옮겨 저장 → pitch(B)도 옮겨진 것, pitches 1개
@Test fun refittingFarAwayCreatesNewPitch()             // A에서 중심 40 m 옮겨 저장 → pitches 2개, pitch(B)는 원래 그대로
@Test fun corruptFileActsEmptyAndSaveRecovers()         // pitches.json에 "garbage" → pitch()=null, savePitch 성공 뒤 정상 읽힘
@Test fun jsonMatchesSwiftShape()                       // 코틀린이 쓴 파일을 고정 문자열과 비교(키 순서 무관): 위 형식 그대로. 스위프트 테스트는 같은 고정 문자열을 디코드
```

- [ ] **Step 2: 실패 확인** (`--tests '*PitchStoreTests'`, `swift test --filter PitchStoreTests`)

- [ ] **Step 3: 구현**
  - 값: 판정 비율 0.5 이상, 넓히는 거리 15 m, 같은 구장으로 볼 중심 이동 30 m 이하.
  - 저장할 때 "기존 구장"은 이 경기에 연결된 구장이다. 연결이 없으면 지금 자동 판정된 구장이다.
  - 파일 쓰기는 노트처럼 원자적으로 한다.

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: 백업 확인** — `android/mobile/src/main/res/xml/backup_rules.xml`·`data_extraction_rules.xml`은 이미 `matches/` 폴더 전체를 넣는다. 바꿀 것이 없음을 확인만 한다.

- [ ] **Step 6: Commit** — `feat(pitch): 구장 저장·같은 구장 자동 판정 (pitches.json, 가까우면 고치고 멀면 새 구장)`

---

### Task 4: 경기장 좌표 히트 격자 + 상세 데이터에 경기장 싣기

**Files:**
- Modify: `android/session/.../Heatmap.kt`, `apple/RefereeCore/Sources/RefereeSession/Heatmap.swift` (`PitchHeat` 추가)
- Modify: `android/mobile/src/main/kotlin/com/aidenstudio/whistlepro/mobile/MatchDetailData.kt`, `MobileModel.kt` (`loadDetail`), `apple/RefereeiOS/MatchDetailData.swift`, `PhoneModel.swift` (`loadDetail`), `apple/RefereeiOS/Views/MatchDetailView.swift:86` (노트 저장 뒤 다시 만들 때 pitch 유지)
- Test: `HeatDensityTests.kt`/`.swift` 에 추가

**Interfaces:**
- Consumes: Task 1 `Pitch.local`, Task 3 `MatchLibrary.pitch`
- Produces:
  - `object PitchHeat { const val MARGIN = 8.0; fun density(cells: List<HeatCell>, pitch: Pitch, maximumSide: Int = 256): HeatDensity? }`
    - 격자는 경기장 좌표다. 가로는 width+2×MARGIN, 세로는 length+2×MARGIN(m)이고, 첫 줄은 골라인 A 쪽이다.
    - 반환 타입은 기존 `HeatDensity`를 그대로 쓴다. 위경도 필드에는 경기장 m 범위(`minLatitude`=-MARGIN … `maxLatitude`=length+MARGIN, `minLongitude`=-MARGIN … `maxLongitude`=width+MARGIN)를 넣는다. 그림 그리는 쪽은 이 값을 m로 읽는다.
    - 번지는 반경과 정규화(가장 진한 곳 1)는 `Heatmap.density`와 같다.
  - `MatchDetailData`에 추가:
    - `val pitch: Pitch?`
    - `val pitchPoints: List<GeoPoint>`: 기간 중·정확도 20 m 이하 지점. 맞추기 화면의 이동 경로와 자동 추정에 쓴다.
  - `MatchDetailData.of(bundle, note, referee, pitch: Pitch?)` / 스위프트 `init(bundle:note:referee:pitch:)`. `loadDetail`이 `library.pitch(matchID, pitchPoints)`로 채운다.
  - `MobileModel.savePitch(id: UUID, corners: List<GeoPoint>): Pitch?`, `MobileModel.clearPitch(id: UUID)` (IO 스레드). `PhoneModel`도 같은 이름이다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun pitchHeatCentersOnPitchCenter()   // 경기장 중심 한 칸만 → 가장 진한 픽셀이 격자 가운데(±1 px), 값 1
@Test fun pitchHeatTopRowIsGoalLineA()      // 골라인 A 근처 칸 → 진한 곳이 위쪽 1/4 안
@Test fun heatOutsideMarginIsDropped()      // 터치라인 밖 30 m 칸만 → null
```

- [ ] **Step 2~4: 실패 확인 → 구현 → 통과** (`:session:test --tests '*HeatDensityTests'`, `swift test --filter HeatDensityTests`)

- [ ] **Step 5: 상세 데이터 연결.** 폰 단위 테스트 `:mobile:testDebugUnitTest`와 아이폰 generic 빌드가 통과해야 한다. 기존 호출부의 `pitch` 기본값은 null로 둔다.

- [ ] **Step 6: Commit** — `feat(pitch): 경기장 좌표 히트 격자, 상세 데이터에 경기장·기간 중 위치`

---

### Task 5: 안드로이드 — 경기장 그림 보기와 SNS 카드

**Files:**
- Create: `android/mobile/src/main/kotlin/com/aidenstudio/whistlepro/mobile/ui/matches/PitchDiagram.kt`
- Modify: `android/mobile/src/main/kotlin/com/aidenstudio/whistlepro/mobile/ui/report/SNSCardExporter.kt`, `MatchDetailScreen.kt` (`exportSNSCard`: pitch가 있으면 지도 스냅숏을 건너뛴다)
- Test: `android/mobile/src/androidTest/kotlin/com/aidenstudio/whistlepro/mobile/PitchDiagramTests.kt`

**Interfaces:**
- Consumes: Task 1 `Pitch`, `PitchMarkings`, Task 4 `PitchHeat.density`, 기존 `HeatBitmap`(밀도 → 색 비트맵), `EventIconCanvas`(핀 그림)
- Produces:
  - `@Composable fun PitchDiagram(pitch: Pitch, cells: List<HeatCell>, pins: List<EventPin>, modifier: Modifier)`: 화면용. 핀 필터는 부르는 쪽이 거른 목록을 넘긴다.
  - `object PitchDiagramPainter { fun bitmap(pitch, cells, pins, widthPx: Int, heightPx: Int): Bitmap }`: SNS 카드용. 화면과 같은 그림이다.

- [ ] **Step 1: 실패하는 렌더 테스트 (기존 `HeatBitmapTests` 방식)**
  - `diagramDrawsPitchUpright`: 105×68 경기장 → 그림 가로:세로 ≈ (68+16):(105+16), 테두리 선 픽셀이 여백 8 m 위치에 있다.
  - `diagramScalesBoxOnSmallPitch`: 40×20 → 페널티 박스 선이 테두리 안쪽에 있다(넘지 않는다).
  - `snsCardUsesDiagramWhenPitchExists`: pitch가 있는 데이터로 카드를 만들면 `MapSnapshot.capture`를 부르지 않는다(가짜 캡처로 호출 수 0).

- [ ] **Step 2: 실패 확인**
  - Run: `./gradlew :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aidenstudio.whistlepro.mobile.PitchDiagramTests`(에뮬레이터)

- [ ] **Step 3: 구현**
  - 바탕은 `Theme.surface`, 경기장은 한 단계 밝은 면이다.
  - 테두리·하프라인은 `Theme.textSecondary` 2 dp, 박스·서클·마크는 같은 색 40% 투명도 1.5 dp다.
  - 히트 → 핀 순서로 그린다.
  - 그림 아래에 "%@ × %@ m"을 쓴다(정수로 반올림).

- [ ] **Step 4: 통과 확인**

- [ ] **Step 5: Commit** — `feat(android): 경기장 그림 히트맵과 SNS 카드`

---

### Task 6: 안드로이드 — 지도 탭 [위성 | 경기장], 바탕 사진, 맞추기 화면

**Files:**
- Create: `android/mobile/src/main/kotlin/com/aidenstudio/whistlepro/mobile/ui/matches/Basemap.kt`, `PitchEditor.kt`
- Modify: `HeatmapMap.kt` (`GoogleMapBox`의 한국 분기를 `Basemap`으로, [위성] 보기에 테두리·하프라인 `Polyline`), `MatchDetailScreen.kt` (`MapTab`: 전환·버튼·편집기 열기, 저장 뒤 data 다시 만들기), `android/mobile/build.gradle.kts` (`VWORLD_KEY` → `buildConfigField("String", "VWORLD_KEY", …)`), `Storage.kt`/설정 (`pitchView` 기억)
- Test: `android/mobile/src/androidTest/.../PitchEditorUITests.kt`, `HeatmapMapTests.kt` 보강

**Interfaces:**
- Consumes: Task 2 `PitchEstimate.of`, Task 4 `MatchDetailData.pitch/pitchPoints`, `MobileModel.savePitch/clearPitch`, Task 5 `PitchDiagram`
- Produces:
  - `enum class Basemap { SATELLITE, VWORLD, DARK_STANDARD }`, `fun Basemap.Companion.choose(inKorea: Boolean, vworldKey: String, vworldFailed: Boolean): Basemap`
    - 순수 함수이며 JVM 단위 테스트로 확인한다.
  - VWORLD 타일 주소: `https://api.vworld.kr/req/wmts/1.0.0/{KEY}/Satellite/{z}/{y}/{x}.jpeg`. 줌 6~19. maps-compose `TileOverlay` + `UrlTileProvider`로 띄우고, 바탕은 `MapType.NONE`이다. 타일 요청이 연속 실패하면(예: 3장) `vworldFailed=true`로 다크 일반 지도로 내린다.
  - `enum class PitchView { SATELLITE, PITCH }`. SharedPreferences 키 `pitchView`, 처음 값 `PITCH`.
  - `@Composable fun PitchEditor(points: List<GeoPoint>, initial: Pitch?, inKorea: Boolean, onSave: (List<GeoPoint>) -> Unit, onCancel: () -> Unit, onClear: (() -> Unit)?)`: 전체 화면 대화상자.

- [ ] **Step 1: 실패하는 테스트**
  - JVM `BasemapTests`:
    - `abroadIsSatellite`
    - `koreaWithKeyIsVWorld`
    - `koreaWithoutKeyIsDark`
    - `koreaAfterFailureIsDark`
  - UI `PitchEditorUITests`(데모 경기, 지도 키 있는 에뮬레이터):
    - `openEditorShowsFourEstimatedCorners`: [경기장 맞추기] → 점 4개(`pitch.corner.0`…`3`) 보임
    - `retapThenSaveCreatesPitch`: [다시 찍기] → 저장 꺼짐 → 지도 네 곳 탭 → 저장 켜짐 → 저장 → [위성|경기장] 전환이 보이고 `PitchDiagram` 표시
    - `cancelKeepsNoPitch`
    - `clearRemovesPitchAndStaysCleared`: 지우기 뒤 상세를 다시 열어도 전환이 없다
    - `noRouteOpensEmptyEditor`: GPS 없는 데모(`NO_WORKOUT_ID`처럼 경로 없는 경기)는 탭이 없으니, 경로가 아주 짧은 데모를 쓴다. 점 0개, "경기장 네 모서리를 차례로 누르세요 · 0 / 4"

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 구현**
  - 맞추기 화면:
    - 바탕은 `Basemap`을 따른다.
    - 이동 경로는 옅은 `Polyline`으로 그린다.
    - 점은 `draggable = true` `Marker`(번호 아이콘)로 둔다.
    - 빈 곳을 탭하면 다음 번호를 추가한다(4개까지).
    - 바탕이 `DARK_STANDARD`면 위쪽에 "라인이 보이지 않아 오차가 클 수 있습니다"를 띄운다.
    - VWORLD면 오른쪽 아래에 "© 브이월드(국토교통부)"를 둔다.
  - 지도 키나 Play 서비스가 없는 기기(`FlatHeatmap` 경로)에서는 맞추기 버튼을 숨긴다. 이미 경기장이 있으면 [경기장] 보기만 보인다.
  - 문구는 아래 목록 그대로 쓴다.
    - "경기장 맞추기"
    - "경기장 다시 맞추기"
    - "경기장 지우기"
    - "경기장 네 모서리를 차례로 누르세요 · %lld / 4"
    - "다시 찍기"
    - "위성"
    - "경기장"
    - "라인이 보이지 않아 오차가 클 수 있습니다"
    - "%@ × %@ m"
    - "© 브이월드(국토교통부)"
    - "저장"·"취소"는 기존 키를 쓴다.

- [ ] **Step 4: 문구 8개 언어 + 생성 스크립트** (Global Constraints 명령). 생성 스크립트가 0으로 끝나야 한다.

- [ ] **Step 5: 통과 확인** — JVM·UI 테스트, `:mobile:lintRelease`

- [ ] **Step 6: Commit** — `feat(android): 경기장 맞추기(네 모서리), [위성|경기장] 보기, 한국 바탕(브이월드 키 있을 때만·없으면 일반 지도+경로)`

---

### Task 7: 아이폰 — 경기장 그림 보기와 SNS 카드

**Files:**
- Create: `apple/RefereeiOS/Views/PitchDiagramView.swift`
- Modify: `apple/RefereeiOS/Views/Report/SNSCardExporter.swift` (`image(for:)`: `data.pitch`가 있으면 `ImageRenderer(PitchDiagramView)`로, 지도 스냅숏 건너뜀)
- Test: `apple/PhoneTests/PitchDiagramTests.swift`

**Interfaces:**
- Consumes: Task 1·4 (스위프트 같은 이름)
- Produces: `struct PitchDiagramView: View { let pitch: Pitch; let cells: [HeatCell]; let pins: [EventPin] }`. 모양·색·글은 Task 5와 같다(`Theme` 토큰).

- [ ] **Step 1: 실패하는 테스트** — Task 5와 같은 세 가지를 `ImageRenderer` 결과 픽셀로 확인한다(`snsCardUsesDiagramWhenPitchExists`는 스냅숏 함수를 주입해 호출 수 0).
- [ ] **Step 2: 실패 확인** — `xcodebuild test -project apple/WhistlePRO.xcodeproj -scheme WhistlePRO -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PhoneTests/PitchDiagramTests`
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `feat(ios): 경기장 그림 히트맵과 SNS 카드`

---

### Task 8: 아이폰 — 지도 탭 [위성 | 경기장], 바탕 사진, 맞추기 화면

**Files:**
- Create: `apple/RefereeiOS/Views/Basemap.swift`, `apple/RefereeiOS/Views/PitchEditorView.swift`
- Modify:
  - `HeatmapMapView.swift`: `inKorea` 분기를 `Basemap`으로, [위성] 보기에 `MKPolyline` 테두리·하프라인, VWORLD면 `MKTileOverlay(urlTemplate:)` `canReplaceMapContent = true`
  - `HeatmapView.swift`
  - `MatchDetailView.swift`: 전환·버튼·시트, 저장 뒤 data 다시 만들기
  - `project.yml`: `Secrets.xcconfig` 선택 포함, Info.plist `VWorldKey`
  - `.gitignore`: `apple/Config/Secrets.xcconfig`
- Test: `apple/PhoneTests/BasemapTests.swift`, `apple/PhoneUITests/PitchEditorUITests.swift`

**Interfaces:**
- Consumes: Task 2·3·4 스위프트, `PhoneModel.savePitch/clearPitch`, Task 7 `PitchDiagramView`
- Produces:
  - `enum Basemap { case satellite, vworld, darkStandard; static func choose(inKorea: Bool, vworldKey: String?, vworldFailed: Bool) -> Basemap }`
  - `@AppStorage("pitchView")` 값 `"pitch"`/`"satellite"`, 처음 `"pitch"`
  - `struct PitchEditorView: View`: Task 6 `PitchEditor`와 같은 입력·동작·문구·접근성 식별자(`pitch.corner.N`)
- 점 끌기: `MKAnnotationView.isDraggable = true`. 빈 곳 탭은 `UITapGestureRecognizer` → `convert(_:toCoordinateFrom:)`. VWorld 실패 판정은 Task 6과 같다(타일 3장 연속 실패).

- [ ] **Step 1: 실패하는 테스트** — `BasemapTests` 네 가지(Task 6과 같은 이름), `PitchEditorUITests` 다섯 가지(Task 6과 같은 이름·흐름)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 문구 동기화** — `sync_strings.py`가 0으로 끝나야 한다. 키는 Task 6에서 이미 json에 넣었다.
- [ ] **Step 5: 통과 확인** — 단위·UI 테스트, `xcodebuild -scheme WhistlePRO build`, `-scheme WhistlePROWatch build`
- [ ] **Step 6: Commit** — `feat(ios): 경기장 맞추기(네 모서리), [위성|경기장] 보기, 한국 바탕(브이월드 키 있을 때만·없으면 일반 지도+경로)`

---

### Task 9: 데모 경기장과 마무리 검증

**Files:**
- Modify:
  - `android/mobile/src/main/kotlin/com/aidenstudio/whistlepro/mobile/MobileModel.kt`: 데모 `sample`에 경기장 하나를 `savePitch`
  - `apple/RefereeiOS/DemoData.swift`: 같은 좌표
  - `android/README.md`: 브이월드 키 한 줄
  - 스펙 문서 끝: 구현 메모
- Test: 기존 `StoreDemoUITests`(양쪽)

- [ ] **Step 1: 데모 경로에 맞는 경기장 좌표 넣기.** 데모 경로를 감싸는 105×68이다. `PitchEstimate.of(데모 경로)` 결과를 반올림해 상수로 쓴다.
- [ ] **Step 2: 전체 검증**
  - Run:
    - `cd android && ./gradlew :session:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintRelease`
    - 폰 UI 테스트 전체(`connectedDebugAndroidTest`, 폰 에뮬레이터)
    - `cd apple/RefereeCore && swift test`
    - 아이폰 `PhoneTests`·`PhoneUITests`
    - 워치 빌드
  - Expected: 전부 통과. 기존 `PhoneFlowUITests` 지도 탭 검사가 경기장 없는 경기에서 그대로 통과해야 한다.
- [ ] **Step 3: 실기기 확인(사람)**
  - 노트10과 아이폰에서 실제 경기 하나(한국)를 연다.
  - 맞추기 → 저장 → 두 보기 → SNS 카드까지 확인한다.
  - 일반 지도 + 경로로 찍는 안내가 보이는지 본다.
- [ ] **Step 4: Commit** — `chore(pitch): 데모 경기장, 브이월드 키 안내`
