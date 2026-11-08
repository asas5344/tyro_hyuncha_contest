# 차량 외형 작업 상태 — 2026-11-07

> 최신 재개 지침은 [SESSION_HANDOFF.md](SESSION_HANDOFF.md)를 먼저 읽는다. 아래는 과거 기록을 포함하며, 에뮬레이터 재개 제한 등은 이후 사용자 요청으로 변경되었다.

사용자가 귀가 후 재개를 명시했다. 사용자가 곡면 범퍼를 적용한 기존 그랜저 외형을 최종 선택·승인했다. Astra 비교안은 채택하지 않았으며, 기존 외형을 기준으로 원래 3D 구현 목표를 이어갈 수 있다. 에뮬레이터와 취소된 웹 작업은 재개하지 않는다.

## 최신 미러 하우징 재제작

- 사용자 지적: 영상과 하우징 윤곽 불일치, 오른쪽이 과도하게 둥글어짐. 실제 오른쪽 미러는 더 운전자 쪽으로 회전해야 좌우가 같은 모양으로 보인다고 명시했다.
- 좌우 공통 16:9 superellipse 거울면/베젤/하우징을 제작했다. CameraRig.mirrorYaw로 운전자 방향을 향하게 전체를 회전하며 오른쪽 회전량이 더 크다. 거울 영상 면도 같은 회전과 크기를 사용한다.
- 차체의 구형 미러/받침을 제거한 `_driver.glb`와 새 `_mirror_stalks.glb`를 별도로 생성한다. 승인된 원본 차체 GLB는 보존했고 드라이버용 차체·보닛·지붕·앞유리 버퍼도 바이트 동일성을 확인했다.
- 장착점은 기존 landmark에서 뒤로 0.18m, 위로 0.08m 조정해 A필러와 도어에 가리지 않게 했다. 미러 확인 시 미니맵은 아래로 이동한다.
- 자기 하우징이 거울 영상에 다시 찍혀 오른쪽을 가리던 문제를 layer8로 분리해 해결했다. 운전석에서는 보이고 미러 카메라에는 제외한다.
- 실제 최종 캡처: `verification/mirrors/*-assembly-final.jpg`. 앞선 suffix 이미지는 중간 결과다.
- 실제 그랜저 주차 화면과 옆 차 없는 비교 장면의 좌우/정면 복귀 계측 테스트 2개, 단위 테스트70개 통과. 네 차종의 방향/장착 계산은 단위 검증, 다른 세 차종의 최신 Android 시각 검수는 별도다.
- 재생성 명령 `python3 tools/generate_driver_mirrors.py`. source vehicle 생성 뒤 이 명령도 실행해야 한다.

## 사이드미러 기능 수정

- 사용자는 좌우 버튼이 미러 확인 역할을 해야 한다고 재확인했다. 낮은 해상도뿐 아니라 렌더 타깃 상하 좌표 및 이중 색상 처리 때문에 거울 영상이 잘못 보이는 것을 실제 에뮬레이터에서 확인했다.
- 좌우 영상은 768×432, FXAA, 둥근 거울면 마스크. 자동 UV 뒤집기를 명시적으로 제어하고 렌더 타깃과 계기판 비트맵 처리를 분리했다. 렌더 타깃은 inverseTonemapSRGB 후 메인 뷰에 표시해 밝기 중복을 줄였다.
- 거울 카메라는 후측방 바깥 22도, 아래 왼쪽 14도/오른쪽 18도. 내 차 옆면은 안쪽 가장자리, 주차선과 뒤쪽 공간이 중심에 오도록 보정했다.
- 실제 거울면 높이로 좌우 줌을 계산해 원래 약 1/3 화면 높이를 유지한다. 선택 미러는 최대20Hz 요청, 정면은10Hz로 제한하며 실제 지속 프레임률 측정값은 아니다.
- `verification/mirrors/*-final.jpg`: 실제 에뮬레이터 최종 캡처. 홈→주차→왼쪽→오른쪽→정면 테스트 및 단위 테스트68개 통과. 수정 APK를 사용자10에 설치·실행했다.

## 운전석 동작 복원 및 실제 에뮬레이터 검증

- 사용자는 기존 운전석 메커니즘을 유지하면서 표현만 3D로 교체하길 원한다고 재확인했다. 별도 기존/3D 운전석 선택은 제거했다.
- 정면은 원본과 같은 yaw 0도, pitch 2도, 수직 FOV 56도. 좌우 미러 확대 계산과 0.14초 고개 전환도 원본 방식으로 맞췄다. 차종별 눈높이는 유지한다.
- 기존 운전석의 미니맵 함수와 sceneOf 데이터를 새 화면에서 재사용한다. 조향·브레이크·충돌·주차 판정은 이 수정에서 변경하지 않는다.
- TextureView 첫 surface 콜백은 축소 버퍼가 아닌 레이아웃 크기를 전달한다. 820×720 버퍼에 1242×1090 viewport를 설정하던 결함을 확인했고, UiHelper의 desired buffer 크기로 viewport와 카메라 종횡비를 설정하도록 수정했다.
- 사용자 요청으로 터미널에서 Pleos_Connect_v2.1.0을 `-gpu host`로 별도 실행했다. 최신 결과는 `verification/driver-fix/`와 DriverViewRegressionTest 실행 기록에 저장한다.
- 실제 검증: 단위 테스트 68개, 홈→주차 정면/우측/복귀 계측 테스트, 전후진·회전·백그라운드 복귀·3회 재진입 계측 테스트 모두 통과했다. 최초 영상 준비는 TextureView에 실제 프레임이 표시된 뒤 알리도록 수정했고 계기판 텍스처의 상하 방향도 바로잡았다. `verification/driver-fix/*-verified.jpg`는 실제 에뮬레이터 화면이다.
- 검증 대상은 별도 렌더 시안이 아닌 실제 HyunchaApp 홈→주차 레이아웃이다. 에뮬레이터가 사용자 10 프로필로 동작하므로 수동 실행 시 `am start --user 10`이 필요할 수 있다.

## 2차 공통 실내 구현 결과

- `models/cockpit.glb` 한 개를 네 차종이 공유한다. 각 차량의 `eyeForwardM`, `eyeHeightM`에 맞춰 장착하고 차체 루트에 부모 연결해 실시간 주행·다시 보기에서 함께 움직인다.
- 기존 외형 GLB 내부의 구형 실내는 표시하지 않는다. 승인한 외형 GLB와 차종 프로필은 바이트 단위로 보존했다.
- 곡면 대시보드, 계기판/센터 화면 하우징, 송풍구, 도어 패널·팔걸이·간접조명, 센터콘솔, 발밑 구조, 회전 가능한 핸들을 공통 생성 코드로 제작했다.
- 눈높이 제작값: 아반떼 1.15m, 그랜저 1.18m, 투싼 1.38m, 싼타페 1.47m. 제조사 실내 측정값이 아닌 게임 카메라 설정이다.
- 정면 수직 시야각 64도. 핸들은 눈 기준 전방 0.46m, 아래 0.34m로 조정해 계기판 가림과 대시보드 간섭을 줄였다. 룸미러 크기와 화면 면 위치도 맞췄다.
- `verification/cockpit/`에 네 차종 × 정면·좌·우 12장 렌더. Filament Metal 검수용 장면이며 Android 앱 캡처가 아니다. 미러 영상과 동적 계기판 텍스처는 이 렌더에 포함되지 않는다.
- Android debug APK 빌드, 단위 테스트 64개, `tools/check_cockpit.py` 통과. 최신 실내의 실제 Android 주행·화면 재진입·미러 영상·성능 검증은 남아 있다.
- 생성: `python3 tools/generate_cockpit.py`. 공유 GLB 작성기는 `tools/glb_geometry.py`. 실내 변경에 외형 재생성이 필요 없다.

## 재개 후 최신 확인본

- 실제 모델: `app/src/main/assets/models/grandeur.glb` 및 동일한 `sedan.glb`, 83개 노드, 3,360,196바이트.
- 최신 이미지: `verification/models/grandeur-rounded-front.png`, `grandeur-rounded-side.png`, `grandeur-rounded-rear.png`.
- 사용자는 앞뒤가 직육면체인 점을 제외한 기존 외형은 좋다고 평가했다. 그 피드백에 따라 앞뒤 끝면을 가로·세로로 굽히고, 그릴·램프·장식도 동일한 곡면을 따르게 수정했다. 이후 Astra 비교안을 검토한 사용자가 기존 곡면 범퍼 버전으로 확정했다.
- 현재 렌더 조명: 직접 생성한 `verification/studio/env/env_ibl.ktx`. `tools/generate_studio_ibl.py`가 HDR 제작 코드다.
- 전면 다이아몬드 그릴과 2단 램프, 넓은 휠 스포크와 중앙 배지, 실제 곡면을 따르는 창 기둥, 지붕과 유리의 접합, 차체 굴곡과 표면을 따라가는 문 경계를 수정했다.
- `python3 tools/check_sedan.py` 통과. 차체 5.035 × 1.880m 및 축간거리 2.895m 유지. 최신 모델은 Filament 1.66.0 Metal에서 3방향 렌더 완료.
- 기존 곡면 범퍼 외형은 사용자 승인 완료. 외형 승인은 앱 실행 성능이나 Android 화면 검증 완료를 뜻하지 않는다.

## 최우선 목표와 확인 절차

- 홈 차량 선택 이미지 `app/src/main/res/drawable-nodpi/car_grandeur.png` 수준의 그랜저 3D 외관 품질에 집중한다.
- 외형 승인에 따라 원래 구현 순서를 재개할 수 있다. 우선 공통 실내와 운전자 시점을 정리한 뒤 미러·후방 화면·회귀 검증을 진행한다.
- 앞·옆·뒤 실제 GLB 렌더 확인 및 Astra 비교를 거쳐 기존 외형 승인 완료. 다음 단계는 공통 실내와 운전자 시점의 품질 확인이다.
- 실내는 모든 차종 공통 모델 하나를 사용하고 눈높이·장착 위치를 맞춘다.
- 초기 외형과 사이트의 프로토타입은 사용자가 품질 미달로 거절했다. 이후 곡면 범퍼를 적용한 기존 모델은 사용자가 승인했다. Astra 안은 비교 후 채택하지 않았다.

## 작업 위치

- `/Users/gimmin-u/Documents/ChatGPT/현car/realistic`
- 브랜치 `feature/realistic`, 기준 `feature/first-person`의 `611de13`.
- 원본 `/Users/gimmin-u/StudioProjects/Hyuncha`는 보존. 작업은 아직 로컬 미커밋 상태다.

## 현재 모델 작업

- 생성 코드: `tools/generate_sedan.py`.
- 그랜저만 생성: `python3 tools/generate_sedan.py --car GRANDEUR`. 다른 차종 재생성 금지.
- 출력: `app/src/main/assets/models/grandeur.glb`, 동일한 `sedan.glb` 별칭.
- 곡면 차체·휠 아치·곡면 유리/루프·멀티 스포크 휠·전면 DRL/그릴/램프·미러·후면 범퍼/번호판/레터링·쿼터글라스를 반복 수정 중이다.
- 초기 박스 형태, 떠 있는 상판/도어카드, 미러 박스, 겹친 보닛/트렁크와 휀더 음영 문제를 수정했다. 이후 실제 렌더와 홈 이미지를 계속 비교해야 한다.

## 외관 검수 렌더

Android 에뮬레이터 없이 같은 Filament 1.66.0의 macOS Metal 렌더러를 사용한다. 실행은 짧은 배치 작업이며 서버를 띄우지 않는다.

- 소스: `tools/render_vehicle.cpp`
- 빌드: `FILAMENT_SDK=/tmp/hyuncha-filament-1.66.0/filament sh tools/build_model_renderer.sh`
- 실행 파일: `verification/bin/render_vehicle`
- 명령:

```sh
verification/bin/render_vehicle app/src/main/assets/models/grandeur.glb /tmp/hyuncha-filament-1.66.0/filament/bin/assets/ibl/lightroom_14b/lightroom_14b_ibl.ktx verification/models/grandeur-review verification/models/ground.glb
```

- 결과: `verification/models/grandeur-review-front.png`, `-side.png`, `-rear.png` (PPM도 있음).
- PPM에서 PNG 변환은 `sips -s format png ... --out ...`.
- 마지막 렌더 로그: `/tmp/grandeur-review.log`.
- SDK와 검수 조명은 공식 Filament 1.66.0 macOS 배포에서 가져온 임시 검수 도구/환경이다. 조명 파일은 Android 앱에 복사하지 않았다.
- `tools/render_model_poses.py` 및 기존 gltf_viewer 검수 파일은 이전 검수 방식이며 현재 실제 카메라를 설정하는 C++ 렌더러가 더 적합하다.

## 기존 Android 구현과 검증 범위

차종별 3D 화면·미러·후방/서라운드 화면·진행선·실제 속도 계기판·다시 보기 연동 코드가 있다. 최신 외관 재작업 전 APK/계측 APK 빌드 및 단위 테스트 64개가 통과했다. 외관 재작업 뒤에는 전체 Android 재빌드를 하지 않았다. 최신 네이티브 미러 방향·화면/메모리/30fps는 실기기 또는 이후 허용된 실행에서 검증해야 한다. 에뮬레이터는 사용자 Mac 부하 문제로 종료했고 재개하지 않는다.

## 취소된 아이패드 웹 작업

사용자는 아이패드에서 Android 에뮬레이터를 실행해 Mac 부하를 줄이려던 것이었고, 별도 웹 미리보기 작업을 취소했다. 서브에이전트 작업과 로컬 서버는 종료됐다. 취소 직전 사용자가 허용한 비공개 배포는 이미 완료됐으나 추가 수정·배포는 하지 않는다. Android 작업은 웹에 의존하지 않는다.
