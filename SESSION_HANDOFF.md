# 새 세션 인수인계 — 2026-11-09

## 새 세션에 붙여 넣을 프롬프트

```text
/Users/gimmin-u/StudioProjects/Hyuncha/SESSION_HANDOFF.md를 먼저 읽고 기존 3D 주차 앱 작업을 이어가줘.
작업 브랜치는 feature/realistic이며 원본 경로로 통합한 상태야. 기존 작업과 자산을 보존하고 의미 있는 단위로 커밋해.
기존 주행·충돌·주차 판정과 운전석 조작 방식을 유지하면서 실제 3D 표현을 개선하는 작업이야.
안내창과 시점 버튼은 사용자 확인 완료이며,
미러·후방 화면·다시 보기 통합 검증을 이어가줘. 이미 승인한 그랜저 외형은 다시 만들지 마.
에뮬레이터는 실행 중인 것을 우선 사용하고, 필요하면 터미널에서 별도 실행해. 아이패드·웹 배포는 하지 마.
한국어로 응답하고, 구현·검증을 자율적으로 진행하되 확인하지 않은 결과를 완료라고 말하지 마.
```

## 작업 위치와 사용자 요구

- 실제 작업 디렉터리: `/Users/gimmin-u/StudioProjects/Hyuncha`
- 브랜치: `feature/realistic`. 기준은 `feature/first-person`의 `611de13`.
- 사용자 요청으로 원본 프로젝트로 작업을 통합했다. 앞으로 원본에서 작업하고 의미 있는 단위로 로컬 커밋한다. 별도 realistic 작업본은 보존한다.
- 기존 `ParkingSimulator`의 이동·조향·충돌·주차 판정을 활용한다. 1단위=1m, 차량 기준은 뒷바퀴 축 중심이다.
- 사용자는 별도 운전 메커니즘을 원하지 않는다. 기존 운전석 동작을 유지하며 차량 내외부와 주차장 표현을 3D로 개선하는 것이 목적이다.
- 그랜저 외형은 곡면 범퍼를 적용한 기존 버전으로 승인했다. Astra 비교안은 채택하지 않았다. 홈 차 선택 이미지가 품질 기준이다.
- 모든 차종은 공통 실내를 사용한다. 차종별 눈높이·장착 위치는 달라야 한다.
- 좌우 버튼은 사이드미러 확인용이다. 운전자 기준 좌우 미러가 비슷한 형태로 보여야 하며 오른쪽 미러는 운전자 쪽으로 더 회전한다.
- 아이패드·Sites 웹 미리보기 작업은 취소했다. 추가 웹 작업·배포를 재개하지 않는다.
- Mac 메모리 8GB. 에뮬레이터는 터미널 별도 실행을 선호한다. 중복 실행하지 않는다.

## 현재 마일스톤과 검증 한계

1. 실제 Filament 3D 실행, 시뮬레이터 좌표 연결, 맵 기반 주차장: 구현됨.
2. 승인한 그랜저 외형, 공통 실내·핸들, 차종별 운전자 시점 높이: 구현됨. 정면 viewport 왜곡과 계기판 UV도 수정했다.
3. 좌우 미러·후방 화면·다시 보기: 연결 코드가 있으며 현재 미러 품질과 실제 화면 통합 검증을 진행 중이다.
4. 세 주차 방식·모든 차종 회귀 검증, 다른 차종 외형 품질 개선, 성능·장시간 안정성 측정은 남아 있다.

30fps는 목표일 뿐 측정으로 확인한 성능이 아니다. 전체 완성 또는 모든 차종 검증 완료로 표현하지 않는다.

## 직전 수정(사용자 확인 완료): 안내창이 시점 버튼을 가리는 문제

- 사용자 실제 차량 모드 스크린샷에서 브레이크 대기 안내창이 BottomCenter의 왼쪽·정면·오른쪽 버튼을 가렸다.
- 버튼 자체에는 주행 시작/이동 여부 조건이 없다. 이전 설명에서 이동·갱신 문제로 추정한 것은 틀렸고 겹침 문제였다.
- 사용자 요청대로 버튼은 그대로 두고 안내창만 위로 옮겼다.
- `app/src/main/java/com/team/hyuncha/ui/ParkingScreen.kt`의 `waitingForBrake -> NoticeCard`:
  `Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)`.
- 버튼은 `RealisticParkingView.kt` BottomCenter, bottom=24.dp 그대로다.
- 이 수정 후 `:app:assembleDebug` 성공, 실행 중인 에뮬레이터 사용자10에 APK 설치 성공, MainActivity 실행 성공.
- **사용자가 실제 차량 모드에서 최신 안내창 위치와 세 시점 버튼의 정상 동작을 직접 확인했다(2026-11-09).**
- 기존 `DriverViewRegressionTest`는 `HyunchaApp(null)`로 실행하는 화면 조작 모드다. 실제 차량 모드의 브레이크 안내창이 없어 이번 버그를 놓쳤다. 이 테스트 통과만으로 실제 차량 모드 겹침 해결을 주장하면 안 된다.
- 필요하면 MainActivity 기반 실제 차량 모드에서 브레이크 입력 전에도 세 버튼이 보이고 눌리는지 확인한다. 검증을 위해 P단 잠금/브레이크 안전 조건을 우회하지 않는다.

## 주요 구현 파일

- `app/src/main/java/com/team/hyuncha/ui/ParkingScreen.kt`: 주차 화면, HUD·브레이크 안내·방향 버튼. 기존/3D 운전석 선택은 사용자 요청으로 제거됨.
- `app/src/main/java/com/team/hyuncha/ui/RealisticParkingView.kt`: TextureView, 렌더러 연결, 미니맵, 좌우·정면 버튼(`look_left`, `look_front`, `look_right`).
- `app/src/main/java/com/team/hyuncha/render3d/`: ParkingRenderer, CameraRig, CameraFeed, 좌표·모델·프로필 등.
- `app/src/main/java/com/team/hyuncha/ui/FirstPersonCanvas.kt`: 기존 표현 및 재사용 미니맵 함수. 기존 운전석 선택 UI로 복구하지 않는다.
- `app/src/main/assets/models/`: 실제 GLB와 프로필.
- `tools/generate_cockpit.py`: 공통 실내 생성. `tools/glb_geometry.py`: 공통 GLB 작성기.
- `tools/generate_driver_mirrors.py`: 운전자용 차체 변형과 미러 생성.
- `materials/`, `tools/build_materials.sh`: Filament 재질 소스/빌드.

Filament/gltfio는 **1.66.0**으로 고정되어 있다. matc도 같은 버전을 사용한다.

## 최신 미러 구현과 보존할 자산

- 승인한 원본 `grandeur.glb`와 `sedan.glb`는 동일하며 보존한다. 다른 차종 외형은 아직 그랜저와 같은 수준으로 승인받지 않았다.
- 운전자용 `_driver.glb`에서 구형 미러/받침만 제거한다. 원본 GLB와 주요 차체 버퍼의 동일성을 확인한 상태다.
- `driver_mirror.glb` 공통 하우징/베젤과 영상 면은 같은 16:9 superellipse 윤곽을 쓴다. 유리 크기 .25m × .140625m.
- `CameraRig.mirrorYaw`로 전체를 운전자 방향으로 향하게 한다. 장착점은 기존 landmark보다 뒤 .18m, 위 .08m다.
- 자기 미러 하우징과 받침은 layer8, 영상 면은 layer2로 분리했다. 미러 카메라는 자기 하우징을 제외해 검은 덩어리와 재귀를 방지한다.
- 좌우 영상은 768×432, FXAA. 선택 미러 요청 최대20Hz, 정면10Hz이며 실제 성능 측정값이 아니다.
- 렌더 타깃 UV와 계기판 비트맵 UV를 별도로 처리하며, 미러의 중복 tone mapping을 보정했다.
- 원본 차체 재생성은 현재 필요 없다. 부득이 소스 차량을 재생성하면 `python3 tools/generate_driver_mirrors.py`도 실행해야 한다.
- 공통 실내 재생성은 `python3 tools/generate_cockpit.py`이며 외형 재생성은 필요 없다.
- 최신 실제 Android 이미지: `verification/mirrors/*-assembly-final.jpg`. 다른 suffix는 과거 중간 결과다.
- 미러 최종 조립 시 단위 테스트70개와 Android 계측 테스트2개가 통과했다. 그랜저 실제 화면 검증이며 다른 세 차종의 최신 Android 시각 승인까지 뜻하지 않는다.

## 실행 및 빌드 명령

작업 디렉터리로 이동한 후 실행한다. 아래 명령은 로컬 Mac 기준이다.

```sh
cd '/Users/gimmin-u/StudioProjects/Hyuncha'
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug --offline --no-daemon --max-workers=1
```

Gradle 캐시는 작업 폴더 밖이므로 샌드박스 접근 오류가 나면 권한 확장 도구로 실행한다. 의존성이 없는 경우에만 offline을 빼고 원인을 확인한다.

```sh
/Users/gimmin-u/Library/Android/sdk/platform-tools/adb devices
```

기존 에뮬레이터가 없을 때만 터미널에서 별도 실행:

```sh
/Users/gimmin-u/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -gpu host -no-boot-anim
```

Pleos 화면은 **사용자10**이다. user0에만 설치/실행하면 화면에 앱이 안 나타날 수 있다.

```sh
/Users/gimmin-u/Library/Android/sdk/platform-tools/adb -e install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
/Users/gimmin-u/Library/Android/sdk/platform-tools/adb -e shell am start --user 10 -W -n com.team.hyuncha/.MainActivity
```

샌드박스 안의 adb는 소켓 접근 실패로 잘못된 daemon 재시작을 시도할 수 있다. 도구에서는 adb를 권한 확장으로 실행한다.

단위 테스트 및 기존 미러 계측 테스트:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --offline --no-daemon --max-workers=1
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team.hyuncha.ui.DriverViewRegressionTest --offline --no-daemon --max-workers=1
```

관련 기존 안정성 테스트는 `Parking3DTest#realFramesSurviveDrivingViewChangesAndBackground`다. 무거운 전체 계측을 무작정 반복하지 말고 변경에 맞춰 실행한다.

## 화면 검증 시 주의

- TextureView 레이아웃 크기와 내부 렌더 버퍼 크기를 혼동해 viewport를 지정하던 버그를 수정했다. UiHelper desired buffer 크기를 사용한다.
- 정면 yaw0, pitch2도, 수직FOV56도, 고개 전환 smoothing .14초. 조향만으로 고개를 돌리지 않는다.
- `3D 화면 표시 중`은 TextureView에 실제 프레임이 표시된 뒤 설정된다.
- 기존 Compose 계측은 무한 애니메이션 때문에 mainClock.autoAdvance=false를 사용한다. 클릭 후 clock을 전진시켜야 상태가 갱신된다. 느린 GPU의 실제 프레임도 기다려야 한다.
- DriverViewRegressionTest 캡처는 DriverShot 로그에 JPEG를 Base64 청크로 남긴다. 테스트 종료 후 앱 파일이 정리될 수 있어 로그에서 추출한다.
- 테스트 로그 경로: `app/build/outputs/androidTest-results/connected/debug/` 아래 장치별 logcat 파일.
- 오프라인 Metal 모델 렌더는 외형 확인용이다. 실제 앱 UI·미러 검증을 대체하지 않는다.

## 이어서 진행할 순서

1. 안내창·세 시점 버튼은 사용자 확인 완료. 다음은 주행 중 전방·미러·후방·다시 보기 통합 검증이다.
2. 사용자가 미러 형태·각도에 추가 피드백을 주면 현재 동일 윤곽/운전자 방향 구조를 기준으로 수정한다.
3. 전진·후진·회전 중 전방/미러/후방 영상의 위치·반전·방향, CarCamera 설정에 따른 기존 탑뷰, Guidance 진행선을 검증한다.
4. 실제 차량 입력과 화면 입력, P단 잠금, 브레이크, 충돌/성공/점수, 재시작·랜덤 배치, 다시 보기·모범 주행, T자·평행·사선, 탑뷰 전환을 회귀 검증한다.
5. 다른 차종 외형 개선과 시점 보정, 성능·메모리·장시간 검증으로 진행한다. 공통 실내 요구는 유지한다.

## 이전 문서의 우선순위

이 문서가 최신 인수인계다. `MODEL_WORK_PAUSED.md`, `docs/realistic-3d.md`, `verification/mirrors/README.md`에는 과거 기록이 섞여 있다. 특히 예전의 “에뮬레이터 재개 금지”, “정면FOV64”, “Android 미검증”은 후속 사용자 요청·수정으로 갱신되었다. 취소된 웹 작업은 계속 취소 상태다.

## 원본 프로젝트 통합 — 2026-11-09

- 사용자 요청에 따라 원본 `/Users/gimmin-u/StudioProjects/Hyuncha`의 `feature/realistic`에서 작업한다.
- 기존 별도 realistic 작업본은 미커밋 파일을 보존한 detached HEAD 상태이며 더 이상 주 작업 경로가 아니다.
- 통합 커밋: `73cc38f`. 원본에 있던 주석 빈 줄 변경도 보존했다.
- 원본 경로에서 `:app:assembleDebug`, `:app:testDebugUnitTest` 성공. 단위 테스트 70개 통과, 실패·오류·건너뜀 0개.
- 브레이크 안내창과 세 시점 버튼은 사용자 직접 확인으로 정상 동작 확인 완료.
- GitHub push는 아직 하지 않았다. 과거 중간 검증 렌더는 로컬에 보존하고 Git 추적에서 제외했다.
- 원본 경로에서 `Parking3DTest#realFramesSurviveDrivingViewChangesAndBackground` 실행은 실패: `Instrumentation run failed due to Process crashed`. 같은 시간대 에뮬레이터 Car service 및 여러 시스템 프로세스 종료 로그가 있어 환경 문제와 앱 문제를 아직 구분하지 못했다. 통합 검증 완료로 간주하지 않는다. 상세 로그는 `app/build/outputs/androidTest-results/connected/debug/`에 있다.
