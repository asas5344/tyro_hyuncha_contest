# 새 세션 인수인계 — 2026-11-09

## 개발 기준

- 작업 브랜치: `feature/realistic`. 기준 커밋은 `feature/first-person`의 `611de13`이며, 3D 구현 통합 커밋은 `73cc38f`다.
- 기존 `ParkingSimulator`의 이동·조향·충돌·주차 판정을 활용한다. 1단위=1m, 차량 기준은 뒷바퀴 축 중심이다.
- 사용자는 별도 운전 메커니즘을 원하지 않는다. 기존 운전석 동작을 유지하며 차량 내외부와 주차장 표현을 3D로 개선하는 것이 목적이다.
- 그랜저 외형은 곡면 범퍼를 적용한 기존 버전으로 승인했다. Astra 비교안은 채택하지 않았다. 홈 차 선택 이미지가 품질 기준이다.
- 모든 차종은 공통 실내를 사용한다. 차종별 눈높이·장착 위치는 달라야 한다.
- 좌우 버튼은 사이드미러 확인용이다. 운전자 기준 좌우 미러가 비슷한 형태로 보여야 하며 오른쪽 미러는 운전자 쪽으로 더 회전한다.

## 현재 마일스톤과 검증 한계

1. 실제 Filament 3D 실행, 시뮬레이터 좌표 연결, 맵 기반 주차장: 구현됨.
2. 승인한 그랜저 외형, 공통 실내·핸들, 차종별 운전자 시점 높이: 구현됨. 정면 viewport 왜곡과 계기판 UV도 수정했다.
3. 좌우 미러·후방 화면·다시 보기: 연결 코드가 있으며 현재 미러 품질과 실제 화면 통합 검증을 진행 중이다.
4. 세 주차 방식·모든 차종 회귀 검증, 다른 차종 외형 품질 개선, 성능·장시간 안정성 측정은 남아 있다.

30fps는 목표일 뿐 측정으로 확인한 성능이 아니다. 전체 완성 또는 모든 차종 검증 완료로 표현하지 않는다.

## 직전 수정(사용자 확인 완료): 안내창이 시점 버튼을 가리는 문제

- 사용자 실제 차량 모드 스크린샷에서 브레이크 대기 안내창이 BottomCenter의 왼쪽·정면·오른쪽 버튼을 가렸다.
- 사용자 요청대로 버튼은 그대로 두고 안내창만 위로 옮겼다.
- `app/src/main/java/com/team/hyuncha/ui/ParkingScreen.kt`의 `waitingForBrake -> NoticeCard`:
  `Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)`.
- 버튼은 `RealisticParkingView.kt` BottomCenter, bottom=24.dp 그대로다.
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

## 실행 및 검증

환경 설정과 기본 빌드·설치는 [README.md](README.md)를 따른다. 계측 테스트는 부팅이 완료된 Pleos 에뮬레이터에서 실행한다.

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.team.hyuncha.ui.DriverViewRegressionTest
```

관련 안정성 테스트는 `Parking3DTest#realFramesSurviveDrivingViewChangesAndBackground`다. 변경 범위에 맞춰 계측 대상을 선택한다.

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

## 문서 관리

README는 앱 소개·설정·조작·빌드 안내를 담당하고, 작업 상태·설계·검증·자산 제작 기록은 이 문서에 유지한다. 개인 환경 경로나 에이전트 실행 권한 설정은 저장소 문서에 포함하지 않는다.

## 최신 검증 — 2026-11-09

- `:app:assembleDebug`, `:app:testDebugUnitTest` 성공. 단위 테스트 70개 통과, 실패·오류·건너뜀 0개.
- 실제 차량 모드의 브레이크 안내창과 세 시점 버튼은 수동 확인으로 정상 동작 확인 완료.
- `Parking3DTest#realFramesSurviveDrivingViewChangesAndBackground`는 `Instrumentation run failed due to Process crashed`로 실패했다. 같은 시간대 에뮬레이터 Car service 및 여러 시스템 프로세스 종료 로그가 있어 환경 문제와 앱 문제를 구분해야 한다. 통합 검증 완료로 간주하지 않는다.
- 계측 로그는 실행 후 `app/build/outputs/androidTest-results/connected/debug/`에서 확인한다. 생성 로그와 중간 렌더는 Git 추적에서 제외하며, 최종 미러 캡처는 `verification/mirrors/*-assembly-final.jpg`에 포함한다.

## 통합한 구현·자산 제작 상세

### 장면·카메라·다시 보기

- 시뮬레이터 `(x, y)`를 glTF `(X, Z)`로 옮기며 `Y`는 높이다. 뒷바퀴 축이 원점이고 +X는 차량 전방, +Z는 우측이다. `sceneOf`가 실시간 주행과 다시 보기의 동일 상태를 전달한다.
- 기존 맵의 주차선·목표 칸·경계벽·주변 차량으로 장면을 생성한다. 충돌 데이터 없는 기둥·연석·주차턱은 새 장애물로 추가하지 않는다. 미러 돌출부도 기존 차체 충돌 범위에 포함하지 않는다.
- 앞바퀴 조향·바퀴 회전·핸들 회전은 별도 노드로 갱신한다. 진행선은 `Guidance.predictPath`를 재사용하고 조향·진행 방향 변경 시 고정 버퍼 메시를 갱신한다.
- 후진 시 `CarCamera` 설정으로 후방 또는 서라운드 화면을 표시하고 확대·운전석 복귀 버튼을 제공한다. 계기판 속도·D/R을 텍스처로 표시하며 다시 보기 기록에도 실제 속도를 저장한다.
- 모델이 없는 차종·변형 제원은 기존 표현으로 대체한다. 기존 운전석 선택 UI를 되살리지는 않는다.
- 전방 버퍼 상한 960×720, 렌더 요청 약 30Hz이며 실제 측정 FPS와 다르다. 시야에 들어오는 미러를 갱신하고 변화가 없을 때 재렌더링을 생략한다. 화면 이탈·백그라운드에서는 렌더 콜백을 중단하고 소유 객체에서 Filament 자원을 해제한다. 최신 전체 수명주기 안정성은 미검증이다.

### 공통 실내·차량 모델

- `cockpit.glb` 하나를 네 차종이 공유한다. `eyeForwardM`, `eyeHeightM`에 맞춰 장착하고 차량 루트의 자식으로 연결해 주행·다시 보기에서 함께 움직인다. 외형 GLB의 구형 실내는 렌더 대상에서 제외한다.
- 눈높이 제작값: 아반떼 1.15m, 그랜저 1.18m, 투싼 1.38m, 싼타페 1.47m. 제조사 실내 측정값이 아니다. 정면은 최신 설정인 수직 FOV 56도를 사용한다.
- 곡면 대시보드, 계기판·센터 화면 하우징, 송풍구, 도어·팔걸이·간접조명, 센터콘솔, 발밑 구조와 핸들이 공통 실내에 있다. 핸들 제작 위치는 눈 기준 전방 0.46m, 아래 0.34m다.
- 승인 그랜저는 `grandeur.glb`와 동일한 `sedan.glb` 별칭이다. 곡면 범퍼, 다이아몬드 그릴·2단 램프, 휠 스포크·중앙 배지, 곡면 창 기둥·문 경계를 반영했다. 전장 5.035m, 전폭 1.880m, 휠베이스 2.895m를 유지한다. Astra 비교안은 미채택이다.
- 전장·전폭·휠베이스·뒤 오버행은 `CarSpec.kt`에서 읽어 생성한다. 눈높이·지붕 높이·미러 위치는 게임 제작값이다. `vehicle_visuals.json` 및 `VehicleVisuals.kt`도 생성 코드에서 출력한다. 주변 차량 GLB는 기존 4.8×1.86m 충돌 사각형에 맞춘다.
- 승인 외형을 다시 만들지 않는다. 명시적으로 재생성이 필요한 경우의 그랜저 한정 명령은 `python3 tools/generate_sedan.py --car GRANDEUR`이며, 이후 `python3 tools/generate_driver_mirrors.py`를 실행해야 한다. 다른 차종까지 일괄 재생성하지 않는다.

### 미러 보정 상세

- 후측방 카메라는 바깥 22도, 아래 왼쪽 14도·오른쪽 18도를 향한다. 내 차 옆면은 안쪽 가장자리, 주차선·뒤쪽 공간이 중심에 오도록 보정했다.
- 실제 유리 크기로 좌우 줌을 계산해 미러가 화면 높이 약 1/3로 보이게 한다. 미러 확인 시 미니맵은 아래로 이동한다.
- 선형 텍스처 샘플링과 FXAA를 사용하고 렌더 타깃 UV 뒤집기를 명시적으로 제어한다. 이미 tone mapping된 피드에 역변환을 적용해 메인 뷰에서의 중복 처리를 보정한다.
- `*-isolatedleft-assembly-final.jpg`, `*-isolatedright-assembly-final.jpg`는 옆 차를 제거한 윤곽 확인 장면이다. `resolution`, `calibrated`, `corrected`, `*-final.jpg` 등은 최종 assembly 이전의 중간 기록이다. 유리 .20×.1125m는 과거 값이고 최신 값은 .25×.140625m다.

### 재생성·오프라인 외형 검수

일반 Android 빌드에는 포함된 GLB·`.filamat`을 사용하므로 Python이나 matc가 필요 없다. 자산 변경 시 필요한 명령만 실행한다.

```sh
python3 tools/generate_cockpit.py
python3 tools/check_cockpit.py
python3 tools/check_models.py
python3 tools/check_sedan.py
MATC=/path/to/filament-1.66.0/bin/matc tools/build_materials.sh
```

Filament/gltfio/matc는 1.66.0으로 맞춘다. 과거 1.77.3 적용은 Kotlin stdlib 2.4와 프로젝트 Kotlin 2.2.10의 충돌로 채택하지 않았다.

오프라인 외형 검수 도구 `tools/render_vehicle.cpp`는 macOS Metal용이다. Filament 1.66.0 macOS SDK 경로를 `FILAMENT_SDK`로 설정하고 빌드한다.

```sh
sh tools/build_model_renderer.sh
```

렌더러에는 모델 GLB, IBL 조명, 출력 접두사, 선택적인 지면 GLB 경로를 순서대로 전달한다. 입력 경로는 준비한 자산에 맞게 지정한다. `tools/generate_studio_ibl.py`는 검수용 HDR 생성 코드이며 검수 조명은 Android 앱에 포함하지 않는다. 정면·측면·후면 오프라인 렌더에는 동적 계기판·미러 영상이 없으므로 Android 통합 검증을 대체하지 않는다. 생성 결과는 Git 추적 대상이 아니다.

- 공식 참고: [Filament 1.66.0 도구](https://github.com/google/filament/releases/tag/v1.66.0), [Filament 문서](https://google.github.io/filament/dup/intro.html), [재질·텍스처 규약](https://google.github.io/filament/Materials.md.html).

### 과거 검증 기록의 범위

- 초기 모델·공통 실내 단계: debug/계측 APK 빌드, 단위 테스트 64개, GLB 구조·유효 좌표·차체 치수·축 기준 검사 및 실내 구조 검사 통과 기록이 있다.
- 운전석 viewport·계기판 UV 수정 단계: 단위 테스트 68개, 홈→주차 정면·우측·복귀, 전후진·회전·백그라운드 복귀·3회 재진입 계측 통과 기록이 있다.
- 최종 미러 조립 단계: 단위 테스트 70개와 그랜저 실제 레이아웃·옆 차 없는 윤곽 검사 2개 통과. 다른 세 차종의 Android 시각 승인이나 최신 통합 계측 성공을 의미하지 않는다.
- `Parking3DTest#everyVehicleAndParkingModeDrawsBothReverseCameras`는 네 차종×세 방식의 실제 텍스처와 후방/서라운드 전환을 검사하도록 준비된 테스트이며 최신 통과 기록은 없다.

## 홈 차량 이미지 제작 기록

Built-in image_gen 도구로 생성하고, 같은 도구로 세부 묘사를 줄이는 스타일 편집을 수행했다. 실제 차량 사진 또는 공식 제조사 이미지로 표시하지 않는다. PNG의 투명도를 유지하고 ContentScale.Fit으로 원본 비율을 보존한다.

## 최종 파일

- app/src/main/res/drawable-nodpi/car_avante.png
- app/src/main/res/drawable-nodpi/car_grandeur.png
- app/src/main/res/drawable-nodpi/car_tucson.png
- app/src/main/res/drawable-nodpi/car_santafe.png

## 생성 프롬프트

### avante

Use case: product-mockup. Asset type: transparent PNG vehicle illustration for an Android in-car parking-practice app. Create ONE isolated complete vehicle, facing left, front-left three-quarter view with SIDE dominating about 70 percent of visible body and front fascia 30 percent, eye height near headlights, long-lens perspective (85mm), very mild foreshortening. Professional restrained semi-realistic automotive editorial illustration: smooth clean shapes, subtle limited shading, no photoreal scene, no heavy black outlines, no cartoon/chibi/toy/low-poly/papercraft appearance. Preserve real-world stock body proportions, wheelbase, normal-size wheels and roof height; do not squeeze width, shorten body, inflate cabin or exaggerate perspective. Pearl silver body, charcoal glass, dark stock tires, restrained alloy rims. Consistent illustration series, generous transparent padding around entire car and all wheels, centered landscape composition, car occupies 85% of image width. Fully transparent background, no ground plane, no background rectangle, no captions, no text, no watermark. Subject: 2024 Hyundai Avante / Elantra CN7 facelift standard sedan, NOT N performance version. Actual long low four-door sedan proportions about 4.71m length, 1.825m width. Recognizable sharp slim angular headlights, low wide grille, wedge-like faceted side creases and fastback-like roof. Normal factory wheel sizes. Realistic adult car, not a cute miniature.

### grandeur

Use case: product-mockup. Asset type: transparent PNG vehicle illustration for an Android in-car parking-practice app. Create ONE isolated complete vehicle, facing left, front-left three-quarter view with SIDE dominating about 70 percent of visible body and front fascia 30 percent, eye height near headlights, long-lens perspective (85mm), very mild foreshortening. Professional restrained semi-realistic automotive editorial illustration: smooth clean shapes, subtle limited shading, no photoreal scene, no heavy black outlines, no cartoon/chibi/toy/low-poly/papercraft appearance. Preserve real-world stock body proportions, wheelbase, normal-size wheels and roof height; do not squeeze width, shorten body, inflate cabin or exaggerate perspective. Pearl silver body, charcoal glass, dark stock tires, restrained alloy rims. Consistent illustration series, generous transparent padding around entire car and all wheels, centered landscape composition, car occupies 85% of image width. Fully transparent background, no ground plane, no background rectangle, no captions, no text, no watermark. Subject: 2023-2025 Hyundai Grandeur / Azera GN7 seventh-generation sedan, factory appearance. Actual elegant large long four-door sedan proportions about 5.035m length,1.88m width. Recognizable single thin unbroken horizontal daytime light strip across front, separate lower vertical headlamps, large wide dark grille, long gently curved roof and rear quarter window. Preserve long wheelbase and elegant low-slung proportions. Not boxy or caricatured.

### tucson

Use case: product-mockup. Asset type: transparent PNG vehicle illustration for an Android in-car parking-practice app. Create ONE isolated complete vehicle, facing left, front-left three-quarter view with SIDE dominating about 70 percent of visible body and front fascia 30 percent, eye height near headlights, long-lens perspective (85mm), very mild foreshortening. Professional restrained semi-realistic automotive editorial illustration: smooth clean shapes, subtle limited shading, no photoreal scene, no heavy black outlines, no cartoon/chibi/toy/low-poly/papercraft appearance. Preserve real-world stock body proportions, wheelbase, normal-size wheels and roof height; do not squeeze width, shorten body, inflate cabin or exaggerate perspective. Pearl silver body, charcoal glass, dark stock tires, restrained alloy rims. Consistent illustration series, generous transparent padding around entire car and all wheels, centered landscape composition, car occupies 85% of image width. Fully transparent background, no ground plane, no background rectangle, no captions, no text, no watermark. Subject: 2022-2024 Hyundai Tucson NX4 fourth-generation standard SUV. Actual compact SUV proportions about 4.63m length,1.865m width, tall enough to be clearly an SUV. Recognizable parametric jewel-like daytime running light segments integrated into dark grille, sculptural angular side body panels, gently sloping rear pillar. Normal factory proportions, not lifted, not a sedan.

### santafe

Use case: product-mockup. Asset type: transparent PNG vehicle illustration for an Android in-car parking-practice app. Create ONE isolated complete vehicle, facing left, front-left three-quarter view with SIDE dominating about 70 percent of visible body and front fascia 30 percent, eye height near headlights, long-lens perspective (85mm), very mild foreshortening. Professional restrained semi-realistic automotive editorial illustration: smooth clean shapes, subtle limited shading, no photoreal scene, no heavy black outlines, no cartoon/chibi/toy/low-poly/papercraft appearance. Preserve real-world stock body proportions, wheelbase, normal-size wheels and roof height; do not squeeze width, shorten body, inflate cabin or exaggerate perspective. Pearl silver body, charcoal glass, dark stock tires, restrained alloy rims. Consistent illustration series, generous transparent padding around entire car and all wheels, centered landscape composition, car occupies 85% of image width. Fully transparent background, no ground plane, no background rectangle, no captions, no text, no watermark. Subject: 2024 Hyundai Santa Fe MX5 fifth-generation SUV. Actual long upright boxy family SUV proportions about4.83m length,1.9m width. Recognizable two H-shaped front daytime running lights joined by dark horizontal grille band, flat long roof, squared wheel arches, upright rear hatch and large rectangular rear side windows. Clearly taller and more squared than Tucson, realistic stock ride height. Not a toy or pickup.

## 최종 편집 프롬프트

Edit this vehicle image into a moderately simplified automotive editorial illustration for an in-car app. CHANGE ONLY rendering style, not identity, camera, silhouette, stock proportions, length, roofline, wheelbase or wheel sizes. Keep the same left-facing low front three-quarter view and silver body. Reduce realism one clear step: replace complex photographic reflections with 3 to 5 broad flat tonal regions, soften and simplify surface shading, remove visible cabin/interior detail, simplify the grille to a few clean shapes and wheels to simple restrained spokes, remove fine panel seams and small trim. Keep the model's signature headlights, roofline and main body creases immediately recognizable. Target a polished modern 2D product illustration with subtle depth, not a photograph and not a toy/low-poly/cartoon/chibi/outline drawing. Do NOT compress, stretch, exaggerate or shorten any part of the vehicle. No dramatic highlights, no gloss, no texture grain, no ground or background. Preserve truly transparent background and the complete uncropped car. No text or watermark.
