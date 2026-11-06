// Pleos Virtual Console 키보드 조작 보조 스크립트
//
// 콘솔의 BRAKE 버튼은 마우스를 누르고 있는 동안만 밟히기 때문에, 트랙패드로는
// 브레이크를 밟은 채 핸들을 돌릴 수 없음. 이 스크립트를 콘솔 페이지에서 한 번 실행하면
// 키보드로 브레이크와 핸들을 동시에 조작할 수 있음.
//
// 사용법: http://127.0.0.1:8088 열기 → Cmd + Option + J (개발자 콘솔) → 이 파일 내용 붙여넣기 → Enter
//
//   Space (누르고 있기)  브레이크 밟기 / 떼면 브레이크 뗌
//   ← / →               누르고 있는 동안 핸들이 부드럽게 계속 돌아감 (초당 약 300도)
//   Shift + ← / →       더 빨리 돌림 (초당 약 600도)
//   0                   핸들 가운데로
//   Q / E               핸들의 이전 곡(◀◀) / 다음 곡(▶▶) 버튼 → 운전석 화면에서 왼쪽 / 오른쪽 사이드미러
//                       (짧게 = 한 칸씩 돌리기, 누르고 있기 = 누르는 동안만 보기)
//                       tools/start-console.sh가 켜주는 연결 프로그램(steering-buttons-bridge.py)이 필요함
//
// 콘솔 페이지 안의 함수(setSteeringAngle, markLocalControl)와 상태(pedals, vehicle)를 그대로 써서
// 마우스로 조작한 것과 똑같이 차량 시스템에 전달됨. 페이지를 새로고침하면 다시 실행해야 함.
(() => {
  if (window.__keyboardControl) return console.log("키보드 조작이 이미 켜져 있어요");
  window.__keyboardControl = true;

  const brakePad = document.getElementById("brakePad");

  // 콘솔의 bindPedal과 같은 방식으로 브레이크 상태를 바꿈
  const setBrake = (pressed) => {
    if (pressed && parkTransitionPending) return;
    if (pressed) pedalControl.brake = true;
    pedals.brake = pressed;
    brakePad.classList.toggle("pressed", pressed);
    markLocalControl();
  };

  // 오른쪽이 양수 (콘솔 핸들 그림이 시계 방향으로 돌아감).
  // 콘솔은 작은 각도를 0으로 맞춰 버리기 때문에, 각도를 스크립트가 따로 들고 있다가 매 프레임 보냄.
  const MAX = 540;
  let angle = vehicle.steering_deg || 0;
  const held = { left: false, right: false, fast: false };
  let last = 0;
  const tick = (now) => {
    const dt = last ? Math.min((now - last) / 1000, 0.05) : 0;
    last = now;
    const dir = (held.right ? 1 : 0) - (held.left ? 1 : 0);
    if (dir !== 0) {
      angle = Math.max(-MAX, Math.min(MAX, angle + dir * (held.fast ? 600 : 300) * dt));
      setSteeringAngle(angle, true);
    } else {
      // 마우스로 콘솔 핸들을 직접 돌린 경우를 따라감
      if (Math.abs((vehicle.steering_deg || 0) - angle) > 6) angle = vehicle.steering_deg || 0;
    }
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);

  // 핸들 버튼: 연결 프로그램에 누름/뗌을 보냄. 한글 입력 상태여도 되도록 e.code로 확인
  const buttons = { KeyQ: "prev", KeyE: "next" };
  const pressedButtons = new Set();
  // 누름·뗌 순서가 바뀌지 않도록 앞 요청이 끝난 뒤에 다음 요청을 보냄
  let queue = Promise.resolve();
  const sendButton = (key, action) => {
    queue = queue
      .then(() => fetch(`http://127.0.0.1:8089/button?key=${key}&action=${action}`))
      .catch(() => console.warn("핸들 버튼 연결 프로그램이 꺼져 있어요. 터미널에서 ./tools/start-console.sh 를 다시 실행하세요"));
  };

  addEventListener("keydown", (e) => {
    held.fast = e.shiftKey;
    if (buttons[e.code]) {
      e.preventDefault();
      if (!e.repeat && !pressedButtons.has(e.code)) {
        pressedButtons.add(e.code);
        sendButton(buttons[e.code], "down");
      }
      return;
    }
    if (e.code === "Space") {
      e.preventDefault();
      if (!e.repeat) setBrake(true);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      held.left = true;
    } else if (e.key === "ArrowRight") {
      e.preventDefault();
      held.right = true;
    } else if (e.key === "0") {
      angle = 0;
      setSteeringAngle(0, true);
    }
  });

  addEventListener("keyup", (e) => {
    held.fast = e.shiftKey;
    if (buttons[e.code]) {
      if (pressedButtons.delete(e.code)) sendButton(buttons[e.code], "up");
      return;
    }
    if (e.code === "Space") {
      e.preventDefault();
      setBrake(false);
    } else if (e.key === "ArrowLeft") {
      held.left = false;
    } else if (e.key === "ArrowRight") {
      held.right = false;
    }
  });

  // 창 밖으로 포커스가 나가면 키를 뗀 것을 못 받을 수 있으니 안전하게 브레이크를 밟은 상태로 둠
  addEventListener("blur", () => {
    held.left = held.right = false;
    pressedButtons.forEach((code) => sendButton(buttons[code], "up"));
    pressedButtons.clear();
    setBrake(true);
  });

  console.log("키보드 조작 켜짐: Space=브레이크, ←→ 누르고 있기=핸들(Shift=빠르게), 0=가운데, Q/E=왼쪽/오른쪽 미러");
})();
