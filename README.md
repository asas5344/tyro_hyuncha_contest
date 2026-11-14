# tyro — 초보운전자 주차 연습 앱

현대차·기아 차량용 앱 공모전(안전운전 부문) 출품작. 팀 **초보면 어때**.

**한 줄 소개**: 주차된 내 차 안에서 차종별 가상 환경으로 다양한 주차 상황을 연습하고, 주행 경로 분석과 맞춤 피드백으로 차폭·거리·조향 감각을 익히는 차량용 앱.

차가 P단에 서 있는 상태에서 **실제 핸들과 브레이크로** 화면 속 차를 움직여 주차를 연습하는 Pleos Connect 앱입니다.

## 제출 안내 (심사용 요약)

| 항목 | 내용 |
|---|---|
| 앱 이름 | **tyro** |
| APK 파일 위치 | [`apk/초보면어때_tyro_release.apk`](apk/초보면어때_tyro_release.apk) (release 빌드, 약 72MB) |
| 개발·테스트에 사용한 에뮬레이터 | **Pleos Connect 에뮬레이터 v2.1.0** (Android Automotive, Android 14 / API 34, arm64-v8a 이미지). Apple M 시리즈 Mac에서 실행 |
| 화면 규격 | **2560 × 1440 픽셀, 가로 화면, 160dpi** (에뮬레이터 기본 설정). 앱은 이 화면에서 차량 정보 패널을 뺀 오른쪽 영역에 표시됩니다 |
| 차량 입력 | 에뮬레이터의 **Virtual Console**로 핸들·브레이크·기어 값을 넣습니다 (`http://127.0.0.1:8088`) |

### 실행 방법 (APK 설치)

1. Pleos Connect 에뮬레이터를 켭니다. (에뮬레이터 이미지 등록은 아래 [1. 처음 한 번만 하는 설정](#1-처음-한-번만-하는-설정))
2. APK를 설치하고 실행합니다.

   ```bash
   adb install -r --user current apk/초보면어때_tyro_release.apk
   adb shell am start --user current -n com.team.hyuncha/.MainActivity
   ```

   다른 서명으로 빌드한 같은 앱이 이미 깔려 있으면 설치가 거부됩니다. 그때는 `adb uninstall com.team.hyuncha` 후 다시 설치하세요.
3. Virtual Console을 엽니다.

   ```bash
   adb forward tcp:8088 tcp:8088
   ```

   브라우저에서 `http://127.0.0.1:8088` 을 엽니다. macOS에서는 `./tools/start-console.sh` 한 번으로 준비됩니다.
4. 콘솔의 **기어를 P**에 둔 채, 앱 홈에서 **연습 시작**을 누릅니다.
5. 콘솔에서 **브레이크를 한 번 밟았다 떼면** 가상 차가 천천히 움직입니다. 핸들은 콘솔의 Steering Angle로 돌리고, 전진·후진은 앱 화면 오른쪽 아래 버튼으로 바꿉니다.

소스에서 직접 빌드하는 방법은 [4. 빌드·설치·테스트](#4-빌드설치테스트-터미널)에 있습니다.

### 필요한 설정

- **기어는 P**: 안전 잠금 때문에 P단이 아니면 연습이 멈춥니다. D단에서는 Pleos 시스템이 앱 화면을 가립니다.
- **권한**: 조향·브레이크(`pleos.car.permission.CAR_DRIVE`), 기어(`android.car.permission.CAR_POWERTRAIN`), 음성 인식(`pleos.car.permission.STT_SERVICE`). 설치할 때 자동으로 부여되며 따로 허용할 것은 없습니다.
- **음성 명령**: 에뮬레이터 마이크가 켜져 있어야 합니다 (`adb emu avd hostmicon`).
- **키보드·레이싱 휠 조작**(선택): 콘솔을 키보드나 레이싱 휠로 조작하는 보조 스크립트가 `tools/`에 있습니다. 사용법은 [3. 조작법](#3-조작법).
- 차량 연결이나 입력 조회에 실패하면 앱이 안내 문구와 오류 코드를 보여 주고 연습을 시작하지 않습니다.

---

## 주요 기능

- 실제 3D 운전석과 탑뷰로 연습하는 주차 3가지: T자 주차·평행 주차·사선 주차 (목표 칸과 주차된 차가 매번 랜덤)
- 운전석의 미러 확인과 탑뷰 전환 지원. 운전석 시점에서 후진을 선택하면 미니맵 자리에 후방 카메라 또는 서라운드 뷰가 표시됩니다.
- 차종 선택: 아반떼·그랜저 (세단), 투싼·싼타페 (SUV) — 차 크기와 회전 반경이 반영됨
- **차량 입력과 안전 잠금** — Pleos Vehicle SDK로 조향·브레이크를, Android 표준 `GEAR_SELECTION`으로 기어를 읽으며 실제 기어가 P일 때만 연습할 수 있습니다.
- 충돌하면 실패, 칸 안에 반듯하게 멈추면 성공
- 성공하면 **1000점 만점 점수**와 S~D 등급, 항목별 근거 + 코치 피드백 + 모범 주행 다시 보기
  (각도 300 · 좌우 위치 200 · 앞뒤 위치 100 · 전후진 전환 200 · 주행 시간 200, 기준은 `sim/Score.kt` 주석)
- **랭킹**: 주차 방식별 점수 순위 (이 기기 안에 저장, 이름은 설정 > 운전자 이름)
- **음성 명령** (Pleos SpeechToText, 차 안 엔진): 주차 화면 왼쪽 아래 [음성 명령] 버튼
- **하드 모드** (T자 주차만, 홈 > 하드 모드): 제한시간 40초(처음 움직인 순간부터) · 속도 8km/h · 클락션 3번(4초 고정 + 랜덤 2번) ·
  통로 폭 5.7m · 칸 폭 2.5m + 판마다 랜덤 상황 하나(두 칸 사이 기둥 / 옆 차 침범 / 이중주차). 랭킹에는 저장하지 않음.
  홈에서 랜덤 또는 연습할 상황을 선택할 수 있습니다. 경적은 오디오 포커스를 받은 동안 재생합니다.

## 1. 처음 한 번만 하는 설정

| 항목 | 내용 |
|---|---|
| Java | JDK 17로 빌드 확인 |
| 에뮬레이터 | **Pleos Connect v2.1.0** (API 34). M 시리즈 Mac은 arm64 이미지 |
| Pleos Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| Android SDK | 빌드용 Android SDK 37 및 Platform-Tools. 앱 최소 API는 28 |

에뮬레이터 이미지 등록 주소 (Android Studio > Settings > Android SDK > SDK Update Sites):

- Apple M 시리즈: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Intel: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

SDK 저장소는 `settings.gradle.kts`의 `dependencyResolutionManagement` 안에 이미 들어 있습니다.

---

## 2. Pleos에서 실행

### ① 에뮬레이터 켜기
Android Studio의 **Device Manager**에서 Pleos Connect를 실행합니다. macOS 터미널에서는 아래처럼 저장된 상태를 불러오지 않고 시작할 수 있습니다. AVD 이름이 다르면 `-list-avds`로 확인해 바꾸세요.

```bash
"$HOME/Library/Android/sdk/emulator/emulator" \
  -avd Pleos_Connect_v2.1.0 -no-snapshot-load
```

Pleos 홈 화면이 뜰 때까지 기다립니다.

### ② 앱 실행
Android Studio에서 **Run**을 누르거나, 새 터미널에서 [빌드·설치 명령](#4-빌드설치테스트-터미널)을 실행합니다.

### ③ Virtual Console 연결
`adb forward tcp:8088 tcp:8088`을 실행하고 브라우저에서 [Virtual Console](http://127.0.0.1:8088/)을 엽니다. 콘솔 버튼이나 **Emulation Controls > Motion**에서 기어·브레이크·조향을 조작할 수 있습니다.

키보드로 조작하려면 브라우저 개발자 콘솔에서 [키보드 스크립트](tools/virtual-console-keyboard.js)를 실행합니다. Q·E 키에는 [핸들 버튼 연결 프로그램](tools/steering-buttons-bridge.py)도 필요합니다.

macOS에서는 프로젝트 폴더에서 도우미를 실행할 수 있습니다:

```bash
./tools/start-console.sh
```

이 스크립트가 해주는 것:
1. 에뮬레이터 연결 확인
2. `adb forward tcp:8088 tcp:8088` (에뮬레이터를 껐다 켜면 매번 다시 필요)
3. 핸들 버튼 연결 프로그램 켜기 (Q·E 키 → 핸들의 ◀◀·▶▶ 버튼. 터미널을 닫아도 계속 켜져 있음)
4. 키보드 조작 스크립트를 클립보드에 복사
5. 브라우저로 `http://127.0.0.1:8088` 열기

그다음 직접 할 것:
1. 열린 브라우저에서 **Cmd + Option + J** (개발자 콘솔)
2. **Cmd + V** 로 붙여넣고 **Enter**
   - 크롬이 "allow pasting"을 입력하라고 하면 입력 후 다시 붙여넣기
   - 사파리는 설정 > 고급 > "웹 개발자용 기능 보기"를 먼저 켜야 함
3. 페이지 빈 곳을 한 번 클릭

> 페이지를 새로고침하면 키보드 조작이 꺼지므로 2번을 다시 하세요.

### ④ 앱에서 연습 시작
홈에서 **연습 시작**을 누르면 실제 차량 연결을 확인합니다. P단에서 브레이크를 한 번 밟으면(Space) 연습을 시작할 수 있습니다.

---

## 3. 조작법

### 화면 조작 모드 (개발·테스트용)

일반 실행은 차량 입력을 사용합니다. 아래 조작은 테스트 코드 등에서 차량 SDK 객체 없이 화면을 열었을 때 사용할 수 있습니다.

| 조작 | 동작 |
|---|---|
| 핸들 슬라이더 | 핸들 각도 |
| 전진 (D) / 후진 (R) 버튼 | 진행 방향 |
| 빨간 버튼 누르고 있기 | 브레이크 떼기 → 천천히 진행 |
| 키보드 ←→ / ↑↓ / Space / R | 핸들 / 전진·후진 / 진행 / 다시 하기 |

### 실제 차량 모드 (Virtual Console 키보드 조작)

연습을 열면 조향·브레이크 정보의 읽기 지원 여부를 먼저 확인합니다. 차량 연결이나 조회에 실패하면 연습을 차단하고 원인과 오류 코드를 표시합니다. 코드는 실패 단계·대상·원인 순서입니다. `CAP-BRAKE-NO_READ`는 브레이크 지원 조회에서 읽기가 불가능하다는 뜻입니다. SDK 숫자 코드 없이 권한 예외가 발생하면 `CAP-STEER-NO_PERMISSION`처럼 표시합니다.

| 키 | 동작 |
|---|---|
| **Space** 누르고 있기 | 브레이크 밟기 (떼면 차가 천천히 움직임) |
| **← / →** 누르고 있기 | 핸들이 계속 돌아감 (초당 약 300°) |
| **Shift + ← / →** | 더 빨리 돌림 (초당 약 600°) |
| **0** | 핸들 가운데 |
| **Q / E** | 핸들의 이전 곡(◀◀) / 다음 곡(▶▶) 버튼 → 운전석 화면에서 왼쪽 / 오른쪽 사이드미러 |

- 사이드미러 버튼: 짧게 누르면 한 칸씩 돌아감 (오른쪽 미러 ↔ 앞 ↔ 왼쪽 미러), 누르고 있으면 누르는 동안만 보고 떼면 원래대로.
  Q·E가 안 되면 `./tools/start-console.sh`를 다시 실행하세요 (연결 프로그램이 꺼진 경우).
  터미널로 직접 보내기: `adb shell cmd car_service inject-key 88` (◀◀) / `87` (▶▶)

- **콘솔의 기어는 P에 그대로 두세요.** 방향은 앱의 [전진 (D)] / [후진 (R)] 버튼으로 바꿉니다.
  R·N·D는 앱의 안전 잠금 대상입니다. D에서는 Pleos의 주행 중 화면 제한도 적용될 수 있습니다.
- 콘솔의 큰 BRAKE 버튼은 마우스를 누르고 있는 동안만 밟혀서 트랙패드로는 불편합니다. 키보드(Space)를 쓰세요.
- 키보드 대신 Emulation Controls > Motion 카드의 슬라이더(Brake Pedal, Steering Angle)로도 값을 바꿀 수 있습니다.

### 실제 차량 모드 (레이싱 휠, Thrustmaster T300 등)

맥에 꽂은 휠의 핸들·브레이크 페달로 조작합니다. 크롬이 휠 값을 읽어 Virtual Console에 넣어 주므로 앱은 평소처럼 Pleos SDK로 값을 받습니다.

```bash
./tools/start-console.sh wheel
```

1. 열린 크롬 창에서 **Cmd + Option + J** → **Cmd + V** → **Enter** ([휠 스크립트](tools/virtual-console-wheel.js))
2. 휠의 아무 버튼이나 한 번 누르면 오른쪽 위 패널에 휠 이름이 뜹니다.
3. 처음 한 번만: **[핸들 찾기]**(4초 동안 핸들을 좌우로) → **[브레이크 찾기]**(4초 안에 페달을 끝까지 밟았다 떼기) → 핸들을 가운데 두고 **[가운데 맞추기]**
4. 핸들을 오른쪽으로 90°(3시 방향) 돌린 채 **[90도 맞추기]** → 실제 핸들 각도와 화면 각도가 같아집니다.
5. 선택: 아래 버튼을 누른 뒤 4초 안에 쓸 패들·버튼을 누르면 연결됩니다.

| 패널 버튼 | 휠 버튼을 누르면 |
|---|---|
| **[전후진 버튼]** | 누를 때마다 전진 ↔ 후진 (앱의 ↑·↓ 단축키를 대신 눌러 줌) |
| **[다시 하기 버튼]** | 새 판 시작 (앱의 R 단축키) |
| **[음성 명령 버튼]** | 화면의 [음성 명령]을 누른 것과 같음 (앱의 V 단축키). 듣는 중에 다시 누르면 취소 |
| **[왼쪽 미러 버튼]** / **[오른쪽 미러 버튼]** | 사이드미러 보기 (핸들의 ◀◀ / ▶▶ 버튼) |

- 전후진 버튼은 휠 쪽에서 전진 → 후진 → 전진 순서를 셉니다. 화면 버튼이나 음성으로 방향을 바꾼 뒤에는 한 번 더 눌러야 할 수 있습니다.

- 설정은 브라우저에 저장됩니다. 페이지를 새로고침하면 1~2번만 다시 하면 됩니다.
- 더 둔하게·민감하게 하려면 **[회전 범위]** 숫자를 줄이거나 늘립니다. 방향이 반대면 **[좌우 반전]**.
- Thrustmaster T300은 본체 스위치를 **PC**에 둡니다 (PS4·PS5 쪽에서는 핸들·페달 값이 오지 않음, 2026-11-14 확인). 맥용 드라이버가 없어서 브레이크는 밟음/뗌으로만 오고, 핸들이 저절로 돌아오는 힘은 없습니다.
- 콘솔 창을 최소화하거나 다른 탭 뒤로 보내면 크롬이 휠 값을 멈춥니다. 그때는 브레이크를 밟은 상태로 둡니다.
- 키보드 스크립트와 같이 쓰지 않습니다. 휠이 안 보이거나 브레이크를 아직 못 찾았으면 브레이크를 밟은 상태로 둡니다.
- 실제 T300(PC 모드)으로 핸들·브레이크가 앱까지 전달되는 것을 확인했습니다(2026-11-14).

---

### 음성 명령
주차 화면 왼쪽 아래 **[음성 명령]**을 누르고 말합니다 (8초 동안 말이 없으면 멈춤). 브레이크는 페달로 조작합니다. 핸들의 음성 버튼은 차량 음성 비서용입니다.

| 이렇게 말하기 | 동작 |
|---|---|
| "후진해 줘" / "앞으로 갈게" | 후진 / 전진 (실제 기어는 P 그대로, 앱의 방향만 바뀜) |
| "기어 R로 바꿔 줘" / "방향 바꿔 줘" | 후진 / 반대 방향으로 |
| "P로 바꿔 줘" | 실제 기어는 P에 두라는 안내 |
| "다시 하기" | 처음부터 |
| "왼쪽 미러" / "오른쪽 미러" / "앞을 보여 줘" | 고개 돌리기 (운전석 화면) |
| "위에서 보기" / "운전석 시점" | 시점 바꾸기 |
| "모범 주행" / "내 주행" / "그만 보기" | 결과 화면에서 다시 보기 / 닫기 |

- 한 단어("후진", "전진", "탑뷰", "닫기")는 엔진이 자주 잘못 들어서 위처럼 두 단어 이상으로 말하는 게 정확합니다.
- "기어 D로"는 엔진이 "뒤로"로 듣는 경우가 있어 후진이 될 수 있습니다. 전진은 "앞으로 갈게"를 권합니다.
- 같은 뜻의 다른 말(예: "빠꾸", "파킹", "한 번 더", "원래대로", "방금 거 돌려 봐")도 최대한 알아듣습니다 → `input/VoiceCommand.kt`
- 에뮬레이터에서 실제 마이크로 시험하려면: 에뮬레이터를 `-allow-host-audio`로 켜고 `adb emu avd hostmicon`
  (또는 Extended Controls > Microphone에서 호스트 오디오 입력 켜기).

---

## 4. 빌드·설치·테스트 (터미널)

macOS에서 이 저장소의 루트 폴더를 열고 실행합니다. Android SDK를 다른 위치에 설치했다면 `ANDROID_HOME`을 그 경로로 바꾸세요.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:assembleDebug &&
adb install -r --user current app/build/outputs/apk/debug/app-debug.apk &&
adb shell am start --user current -W -n com.team.hyuncha/.MainActivity
```

Pleos의 현재 사용자에 APK를 설치합니다. 기기가 여러 대 연결돼 있으면 `adb devices`로 Pleos 기기 ID를 확인하고 각 `adb` 명령에 `-s <기기 ID>`를 지정하세요. Windows에서는 `gradlew.bat`을 사용합니다.

```bash
./gradlew :app:testDebugUnitTest        # 단위 테스트
./gradlew :app:assembleDebugAndroidTest # Android 테스트 APK 빌드
./gradlew :app:assembleRelease          # 제출용 release APK (app/build/outputs/apk/release/app-release.apk)
```

제출용 APK는 release 빌드를 `apk/초보면어때_tyro_release.apk`로 복사한 것입니다. 따로 만든 서명 키 없이 설치할 수 있도록 디버그 키로 서명합니다 (`app/build.gradle.kts`).

### 차량 연동 검증

PR #17에서 디버그 APK와 Android 테스트 APK를 빌드하고 단위 테스트 126개를 통과했습니다. Pleos Connect v2.1.0에서 P단 진입과 홈 복귀를 확인했고, 검증용 빌드로 권한 오류와 브레이크 미지원 안내를 재현했습니다.

Pleos Connect v2.1.0의 비교용 빌드에서 Virtual Console의 `P → R → N → D → P` 전환을 Android 표준 기어와 Pleos SDK가 모두 수신했습니다. 표준 기어로 전환한 앱에서는 P단 진입, N단 잠금 안내, P단 복귀 후 잠금 해제를 확인했습니다. 앱은 유효한 P단을 확인한 상태에서만 연습을 진행합니다. 기어 정보가 사라지거나 기어 서비스 연결이 끊기면 연습을 멈춥니다.

하드 모드 경적은 재생 직전에 일시 오디오 포커스를 요청합니다. 포커스를 받지 못하면 해당 경적을 건너뜁니다. Pleos 계측 테스트에서 경적 재생 중 포커스 획득과 재생 종료·중단 시 반환을 확인했습니다.

---

## 5. 폴더 구조

```
app/src/main/java/com/team/hyuncha/
├── MainActivity.kt        Vehicle SDK 연결(initialize/release) + 화면
├── input/                 DrivingInput 인터페이스 (시뮬레이터는 입력이 어디서 오는지 모름)
│   ├── TouchInput.kt      화면 슬라이더·버튼·키보드
│   ├── VehicleInput.kt    읽기 지원 확인 및 조향각·브레이크·기어 입력
│   ├── VehicleConnectionFailure.kt  차량 연동 오류 문구와 코드
│   ├── LookButtons.kt     핸들 ◀◀/▶▶ 버튼 → 운전석 화면에서 고개 돌리기
│   └── VoiceInput.kt      Pleos SpeechToText 음성 명령 (VoiceCommand.kt: 말 → 명령)
├── safety/SafetyLock.kt   P단 확인
├── sim/                   자전거 모델, 충돌·성공 판정, 주차장 맵, 코치·점수
├── data/                  차종 제원, 화면 설정, 랭킹 기록
├── render3d/              Filament 렌더러, 카메라·미러·차종 프로필
└── ui/                    3D 운전석, 탑뷰, 조작·결과·랭킹·설정 화면
tools/
├── start-console.sh               Virtual Console 시작 준비
├── virtual-console-keyboard.js    콘솔 키보드 조작
├── virtual-console-wheel.js       콘솔 레이싱 휠 조작
└── steering-buttons-bridge.py     Q·E 키 → 에뮬레이터 핸들 버튼 (◀◀·▶▶)
```

---

## 6. 알아둘 점 · 자주 막히는 곳

| 증상 / 사항 | 원인 · 해결 |
|---|---|
| SDK 호출에 빨간 줄 (`@RequiresPermission`) | Manifest에 권한 누락. 조향·브레이크는 `pleos.car.permission.CAR_DRIVE`, 표준 기어는 `android.car.permission.CAR_POWERTRAIN` |
| Virtual Console 접속 안 됨 | Pleos 홈 화면이 뜬 뒤 `adb forward tcp:8088 tcp:8088`을 다시 실행합니다. |
| 콘솔은 열리지만 입력이 적용되지 않음 | `503 emulator_inactive`가 반환되면 저장 상태를 불러오지 않는 방식으로 에뮬레이터를 부팅하고 다시 연결합니다. 같은 오류가 반복되면 차량 입력 검증이 보류된 상태입니다. |
| `cmd car_service inject-vhal-event` 안 됨 | 에뮬레이터가 user 빌드라 막혀 있음. Virtual Console을 사용 |
| **조향값 방향·단위** | SDK 문서는 "앞바퀴 각도, 왼쪽이 음수"지만, 에뮬레이터에서는 **핸들 각도가 그대로, 오른쪽이 음수**로 들어옴. 앱의 **설정 > 실제 차량 조향값**에서 단위와 좌우 방향을 맞춥니다. **실제 차에서는 다시 확인 필요** |
| 차종 제원 | 전장·전폭·휠베이스는 공식 제원표. 뒤 오버행·조향비·최대 조향각은 회전 반경에서 계산한 추정치 |
