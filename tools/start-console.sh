#!/bin/zsh
# Virtual Console 시작 준비를 한 번에 해주는 스크립트 (에뮬레이터를 켤 때마다 실행)
#   1) 에뮬레이터 연결 확인
#   2) 8088 포트 연결 (adb forward)
#   3) 키보드 조작 스크립트를 클립보드에 복사
#   4) 브라우저로 Virtual Console 열기
# 사용법: 프로젝트 폴더에서  ./tools/start-console.sh

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SCRIPT_DIR="${0:A:h}"
URL="http://127.0.0.1:8088/"

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

pbcopy < "$SCRIPT_DIR/virtual-console-keyboard.js"
if [[ -n "$(pbpaste)" ]]; then
  echo "✅ 키보드 조작 스크립트를 클립보드에 복사했어요"
else
  # 일부 환경(원격·샌드박스)에서는 클립보드 접근이 안 됨 → 파일을 직접 열어서 복사하도록 안내
  echo "⚠️  클립보드 복사가 안 됐어요. 아래 파일을 열어 전체 선택(Cmd+A) 후 복사(Cmd+C)하세요:"
  echo "   $SCRIPT_DIR/virtual-console-keyboard.js"
  open -e "$SCRIPT_DIR/virtual-console-keyboard.js"
fi

open "$URL"
echo ""
echo "다음 순서로 마무리하세요:"
echo "  1. 열린 브라우저 창에서 Cmd + Option + J (개발자 콘솔)"
echo "  2. Cmd + V 로 붙여넣고 Enter"
echo "  3. 페이지 빈 곳 클릭 후  Space=브레이크, ←→=핸들, 0=가운데"
