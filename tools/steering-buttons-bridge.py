#!/usr/bin/env python3
"""
핸들 버튼 연결 프로그램 (에뮬레이터 시험용)

Virtual Console에는 핸들의 이전 곡(◀◀)·다음 곡(▶▶) 버튼이 없어서,
콘솔 페이지의 키보드 스크립트(Q·E 키)가 이 프로그램에 요청을 보내면
adb로 에뮬레이터에 진짜 핸들 버튼과 같은 경로(car_service inject-key)로 신호를 넣어줌.

레이싱 휠 스크립트의 전진·후진·다시 하기·음성 명령 버튼은 앱의 키보드 단축키(↑·↓·R·V)를 눌러 줌.

- 이 컴퓨터 안(127.0.0.1)에서만 받고, 아래 정해진 버튼만 보낼 수 있음
- tools/start-console.sh가 자동으로 켜줌. 직접 켜려면:  python3 tools/steering-buttons-bridge.py
"""
import os
import subprocess
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8089
ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools", "adb")
KEYS = {"prev": "88", "next": "87"}          # KEYCODE_MEDIA_PREVIOUS / NEXT
ACTIONS = {"down", "up"}
# 앱(주차 화면)의 키보드 단축키: ↑ = 전진, ↓ = 후진, R = 다시 하기, V = 음성 명령. 한 번 눌렀다 떼는 것만 보냄
TAPS = {"forward": "19", "reverse": "20", "restart": "46", "voice": "50"}   # KEYCODE_DPAD_UP / DPAD_DOWN / R / V
VERSION = "3"                               # start-console.sh가 옛 버전이 켜져 있는지 확인할 때 씀
CONSOLE = "http://127.0.0.1:8088"           # 요청을 허용할 페이지 (Virtual Console)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        q = parse_qs(url.query)
        key = q.get("key", [""])[0]
        action = q.get("action", [""])[0]
        if url.path == "/version":
            return self.reply(200, VERSION)
        if url.path == "/button" and key in TAPS and action == "tap":
            result = subprocess.run([ADB, "shell", "input", "keyevent", TAPS[key]],
                                    capture_output=True, text=True, timeout=5)
            ok = result.returncode == 0
            return self.reply(200 if ok else 500, "ok" if ok else (result.stdout + result.stderr))
        if url.path != "/button" or key not in KEYS or action not in ACTIONS:
            return self.reply(400, "bad request")
        result = subprocess.run([ADB, "shell", "cmd", "car_service", "inject-key", "-a", action, KEYS[key]],
                                capture_output=True, text=True, timeout=5)
        ok = result.returncode == 0 and "Succeeded" in result.stdout
        self.reply(200 if ok else 500, "ok" if ok else (result.stdout + result.stderr))

    def reply(self, code, text):
        body = text.encode()
        self.send_response(code)
        self.send_header("Access-Control-Allow-Origin", CONSOLE)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):   # 요청마다 찍히는 기본 로그는 끔
        pass


if __name__ == "__main__":
    print(f"핸들 버튼 연결 켜짐 (127.0.0.1:{PORT}). 끄려면 Ctrl+C")
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()   # 한 번에 하나씩 처리해서 누름·뗌 순서 유지
