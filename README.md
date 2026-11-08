# 옆자리 (가칭) — 초보운전자 주차 연습 앱

현대차·기아 차량용 앱 공모전(안전운전 부문) 출품작.
차가 P단에 서 있는 상태에서 **실제 핸들과 브레이크로** 화면 속 차를 움직여 주차를 연습하는 Pleos Connect 앱입니다.

- 실제 3D 운전석과 탑뷰로 연습하는 주차 3가지: T자 주차·평행 주차·사선 주차 (목표 칸과 주차된 차가 매번 랜덤)
- 차종 선택: 아반떼·그랜저 (세단), 투싼·싼타페 (SUV) — 차 크기와 회전 반경이 반영됨
- 입력 2가지: **화면 조작** (슬라이더·버튼·키보드) / **실제 차량** (Pleos Vehicle SDK)
- 안전 잠금: 실제 차량 모드는 기어가 P일 때만 진행
- 충돌하면 실패, 칸 안에 반듯하게 멈추면 성공 (각도 오차·중앙 거리·전후진 횟수·시간 표시)

## 현재 개발 상태

`feature/realistic` 브랜치에서 3D 운전석을 개발하고 있습니다. Filament 3D 운전석을 기본으로 사용하며, 기존 주행·충돌·주차 판정과 운전석 조작 방식을 유지합니다. 별도의 기존/3D 운전석 선택 UI는 없습니다.

- 구현: 네 차종 GLB, 승인된 그랜저 외형, 공통 실내와 차종별 눈높이, 미러·후방 화면·다시 보기 연결.
- 확인: APK 빌드 및 단위 테스트 70개 통과. 실제 차량 모드의 브레이크 안내창과 시점 버튼은 사용자 확인 완료.
- 진행 중: 주행 중 영상·다시 보기 통합 검증, 모든 차종·세 주차 방식 회귀 검증, 다른 차종 외형 개선, 성능·장시간 안정성 측정. 최근 3D 계측은 프로세스 종료로 실패하여 원인 확인이 필요합니다. 지속 30fps는 미검증입니다.

개발 상태, 검증 기록, 재생성 방법과 차량 이미지 제작 기록은 [SESSION_HANDOFF.md](SESSION_HANDOFF.md)에서 확인할 수 있습니다.

---

## 1. 처음 한 번만 하는 설정

| 항목 | 내용 |
|---|---|
| Android Studio | 최신 안정 버전 (JDK는 Android Studio에 들어 있는 것 사용) |
| 에뮬레이터 | **Pleos Connect v2.1.0** (API 34). M 시리즈 Mac은 arm64 이미지 |
| Pleos SDK | `ai.pleos.playground:Vehicle:2.3.2` (2.3.1은 저장소에 없음) |
| minSdk | 28 |

에뮬레이터 이미지 등록 주소 (Android Studio > Settings > Android SDK > SDK Update Sites):

- Apple M 시리즈: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Intel: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

SDK 저장소는 `settings.gradle.kts`의 `dependencyResolutionManagement` 안에 이미 들어 있습니다.

---

## 2. 매번 시작할 때 하는 순서 ⭐

### ① 에뮬레이터 켜기
Android Studio > Device Manager > Pleos Connect ▶. **홈 화면이 다 뜰 때까지** 기다립니다.

### ② 앱 실행
Android Studio에서 ▶ Run (또는 터미널에서 아래 "빌드" 참고).

### ③ Virtual Console 준비 (실제 차량 모드로 테스트할 때, macOS 도우미)
다른 환경에서는 `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088`을 열고, 개발자 콘솔에서 `tools/virtual-console-keyboard.js`를 실행합니다. Q·E 연결에는 `tools/steering-buttons-bridge.py`도 필요합니다.

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

### ④ 앱에서 "실제 차량" 선택
오른쪽 패널 **입력 > 실제 차량** → 브레이크를 한 번 밟으면(Space) 연습 시작.

---

## 3. 조작법

### 화면 조작 모드 (차량 없이 개발·백업 시연용)
| 조작 | 동작 |
|---|---|
| 핸들 슬라이더 | 핸들 각도 |
| 전진 (D) / 후진 (R) 버튼 | 진행 방향 |
| 빨간 버튼 누르고 있기 | 브레이크 떼기 → 천천히 진행 |
| 키보드 ←→ / ↑↓ / Space / R | 핸들 / 전진·후진 / 진행 / 다시 하기 |

### 실제 차량 모드 (Virtual Console 키보드 조작)
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
  R·D로 바꾸면 앱의 안전 잠금이 걸리고, D에서는 Pleos 시스템이 "주행 중에는 사용할 수 없습니다"로 화면을 가립니다.
- 콘솔의 큰 BRAKE 버튼은 마우스를 누르고 있는 동안만 밟혀서 트랙패드로는 불편합니다. 키보드(Space)를 쓰세요.
- 키보드 대신 Emulation Controls > Motion 카드의 슬라이더(Brake Pedal, Steering Angle)로도 값을 바꿀 수 있습니다.

---

## 4. 빌드·테스트 (터미널)

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r --user current app/build/outputs/apk/debug/app-debug.apk
adb shell am start --user current -W -n com.team.hyuncha/.MainActivity
```

프로젝트 루트에서 실행합니다. Android Studio의 Gradle JDK 또는 `JAVA_HOME`을 설정하고, Android SDK의 `platform-tools`를 `PATH`에 추가하세요. Windows에서는 `gradlew.bat`을 사용합니다. 다중 사용자 에뮬레이터는 현재 화면의 사용자에 앱을 설치·실행해야 합니다.

---

## 5. 폴더 구조

```
app/src/main/java/com/team/hyuncha/
├── MainActivity.kt        Vehicle SDK 연결(initialize/release) + 화면
├── input/                 DrivingInput 인터페이스 (시뮬레이터는 입력이 어디서 오는지 모름)
│   ├── TouchInput.kt      화면 슬라이더·버튼·키보드
│   └── VehicleInput.kt    Pleos SDK: 조향각·브레이크·기어 리스너
├── safety/SafetyLock.kt   P단 확인
├── sim/                   자전거 모델, 충돌(SAT)·성공 판정, 주차장 맵
├── data/CarSpec.kt        차종 제원
├── render3d/              Filament 렌더러, 카메라·미러·차종 프로필
└── ui/                    3D 운전석, 탑뷰, 조작 패널
tools/
├── start-console.sh               Virtual Console 시작 준비
├── virtual-console-keyboard.js    콘솔 키보드 조작
└── steering-buttons-bridge.py     Q·E 키 → 에뮬레이터 핸들 버튼 (◀◀·▶▶)
```

새 입력 방식(예: 게이밍 휠)은 `DrivingInput`을 구현하는 클래스 하나만 추가하면 시뮬레이터 수정 없이 붙습니다.

---

## 6. 알아둘 점 · 자주 막히는 곳

| 증상 / 사항 | 원인 · 해결 |
|---|---|
| `Could not find ai.pleos.playground:Vehicle:2.3.1` | 2.3.1은 저장소에 없음. **2.3.2** 사용 |
| SDK 호출에 빨간 줄 (`@RequiresPermission`) | Manifest에 권한 누락. 조향·브레이크·기어는 모두 `pleos.car.permission.CAR_DRIVE` |
| Virtual Console 접속 안 됨 | 에뮬레이터 부팅 완료 확인 후 `./tools/start-console.sh` 다시 실행 |
| `cmd car_service inject-vhal-event` 안 됨 | 에뮬레이터가 user 빌드라 막혀 있음. Virtual Console을 사용 |
| **조향값 방향·단위** | SDK 문서는 "앞바퀴 각도, 왼쪽이 음수"지만, 에뮬레이터에서는 **핸들 각도가 그대로, 오른쪽이 음수**로 들어옴. 앱의 실제 차량 패널 **"조향값 해석"** 버튼(핸들 각도 / 앞바퀴 각도, 좌우 반전)으로 맞춤. **실제 차에서는 다시 확인 필요** |
| 기어 API deprecated 경고 | SDK 2.3.2에서는 정상 동작. 이후 버전에서는 Google `GEAR_SELECTION` 속성으로 바꿔야 할 수 있음 |
| 차종 제원 | 전장·전폭·휠베이스는 공식 제원표. 뒤 오버행·조향비·최대 조향각은 회전 반경에서 계산한 추정치 |
