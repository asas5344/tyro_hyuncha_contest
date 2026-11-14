#!/bin/zsh
# Virtual Console 시작 준비를 한 번에 해주는 스크립트 (에뮬레이터를 켤 때마다 실행)
#   1) 에뮬레이터 연결 확인
#   2) 8088 포트 연결 (adb forward)
#   3) 핸들 버튼 연결 프로그램 켜기 (Q·E 키 → 핸들의 이전 곡·다음 곡 버튼)
#   4) 조작 스크립트를 클립보드에 복사 (기본 = 키보드, wheel = 레이싱 휠)
#   5) 브라우저로 Virtual Console 열기 (wheel이면 크롬으로)
# 사용법: 프로젝트 폴더에서  ./tools/start-console.sh        (키보드로 조작)
#                          ./tools/start-console.sh wheel  (T300 같은 레이싱 휠로 조작)

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SCRIPT_DIR="${0:A:h}"
URL="http://127.0.0.1:8088/"

# 조작 방식: 키보드(기본) 또는 레이싱 휠
if [[ "$1" == "wheel" ]]; then
  CONTROL_SCRIPT="$SCRIPT_DIR/virtual-console-wheel.js"
  CONTROL_NAME="휠 조작"
else
  CONTROL_SCRIPT="$SCRIPT_DIR/virtual-console-keyboard.js"
  CONTROL_NAME="키보드 조작"
fi

if [[ ! -x "$ADB" ]]; then
  echo "❌ adb를 찾을 수 없어요: $ADB"
  echo "   Android Studio > Settings > Android SDK 경로를 확인하세요."
  exit 1
fi

if ! "$ADB" devices | grep -q "emulator-.*device$"; then
  echo "❌ 실행 중인 에뮬레이터가 없어요. Device Manager에서 Pleos 에뮬레이터를 먼저 켜세요."
  echo "   (켜자마자면 홈 화면이 뜰 때까지 기다렸다가 다시 실행하세요)"
  exit 1
fi
echo "✅ 에뮬레이터 연결됨"

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
if curl -s -o /dev/null --max-time 5 -w "%{http_code}" "$URL" | grep -q 200; then
  echo "✅ Virtual Console 연결됨 ($URL)"
else
  echo "❌ Virtual Console에 접속이 안 돼요. 에뮬레이터가 완전히 부팅됐는지 확인하고 다시 실행하세요."
  exit 1
fi

# 옛 버전 연결 프로그램이 켜져 있으면(전진·후진·음성 버튼을 모름) 끄고 새로 켬
if curl -s -o /dev/null --max-time 2 "http://127.0.0.1:8089/" &&
   [[ "$(curl -s --max-time 2 "http://127.0.0.1:8089/version")" != "3" ]]; then
  lsof -ti tcp:8089 | xargs kill 2>/dev/null
  sleep 0.5
fi

# 핸들 버튼 연결: 이미 켜져 있으면 그대로 둠. 터미널을 닫아도 계속 켜져 있음
if curl -s -o /dev/null --max-time 2 "http://127.0.0.1:8089/" ; then
  echo "✅ 핸들 버튼 연결 이미 켜져 있음 (Q·E 키)"
else
  nohup python3 "$SCRIPT_DIR/steering-buttons-bridge.py" >/dev/null 2>&1 &
  for _ in {1..10}; do   # 켜질 때까지 최대 5초 기다림
    sleep 0.5
    curl -s -o /dev/null --max-time 1 "http://127.0.0.1:8089/" && break
  done
  if curl -s -o /dev/null --max-time 2 "http://127.0.0.1:8089/"; then
    echo "✅ 핸들 버튼 연결 켜짐 (Q·E 키)"
  else
    echo "⚠️  핸들 버튼 연결을 못 켰어요. Q·E 키(사이드미러)는 안 되고 나머지 조작은 됩니다."
  fi
fi

pbcopy < "$CONTROL_SCRIPT"
if [[ -n "$(pbpaste)" ]]; then
  echo "✅ $CONTROL_NAME 스크립트를 클립보드에 복사했어요"
else
  # 일부 환경(원격·샌드박스)에서는 클립보드 접근이 안 됨 → 파일을 직접 열어서 복사하도록 안내
  echo "⚠️  클립보드 복사가 안 됐어요. 아래 파일을 열어 전체 선택(Cmd+A) 후 복사(Cmd+C)하세요:"
  echo "   $CONTROL_SCRIPT"
  open -e "$CONTROL_SCRIPT"
fi

if [[ "$1" == "wheel" ]]; then
  # 휠 값은 크롬이 읽음 (사파리는 휠 지원이 확인되지 않음). 크롬이 없으면 기본 브라우저로 엶
  open -a "Google Chrome" "$URL" 2>/dev/null || open "$URL"
  echo ""
  echo "다음 순서로 마무리하세요:"
  echo "  1. 열린 크롬 창에서 Cmd + Option + J (개발자 콘솔)"
  echo "  2. Cmd + V 로 붙여넣고 Enter"
  echo "  3. 휠의 아무 버튼이나 한 번 누르기 → 오른쪽 위 패널에 휠 이름이 뜸"
  echo "  4. 패널에서 [핸들 찾기] → [브레이크 찾기] → [가운데 맞추기] → [90도 맞추기] (처음 한 번만)"
  echo "     선택: [전후진 버튼] [다시 하기 버튼] [음성 명령 버튼] [왼쪽·오른쪽 미러 버튼]"
  echo "  ※ 콘솔 창은 최소화하지 말고 화면에 보이게 두세요."
else
  open "$URL"
  echo ""
  echo "다음 순서로 마무리하세요:"
  echo "  1. 열린 브라우저 창에서 Cmd + Option + J (개발자 콘솔)"
  echo "  2. Cmd + V 로 붙여넣고 Enter"
  echo "  3. 페이지 빈 곳 클릭 후  Space=브레이크, ←→=핸들, 0=가운데, Q·E=왼쪽·오른쪽 사이드미러"
fi
