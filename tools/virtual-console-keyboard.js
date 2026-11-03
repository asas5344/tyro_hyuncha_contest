// Pleos Virtual Console 키보드 조작 보조 스크립트
//
// 콘솔의 BRAKE 버튼은 마우스를 누르고 있는 동안만 밟히기 때문에, 트랙패드로는
// 브레이크를 밟은 채 핸들을 돌릴 수 없음. 이 스크립트를 콘솔 페이지에서 한 번 실행하면
// 키보드로 브레이크와 핸들을 동시에 조작할 수 있음.
//
// 사용법: http://127.0.0.1:8088 열기 → Cmd + Option + J (개발자 콘솔) → 이 파일 내용 붙여넣기 → Enter
//
//   Space (누르고 있기)  브레이크 밟기 / 떼면 브레이크 뗌
//   ← / →               핸들 30도씩 (누르고 있으면 계속 돌아감)
//   Shift + ← / →       핸들 90도씩
//   0                   핸들 가운데로
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

  // 오른쪽이 양수 (콘솔 핸들 그림이 시계 방향으로 돌아감)
  const steerBy = (delta) => setSteeringAngle(vehicle.steering_deg + delta, true);

  addEventListener("keydown", (e) => {
    if (e.code === "Space") {
      e.preventDefault();
      if (!e.repeat) setBrake(true);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
      e.preventDefault();
      const step = e.shiftKey ? 90 : 30;
      steerBy(e.key === "ArrowLeft" ? -step : step);
    } else if (e.key === "0") {
      setSteeringAngle(0, true);
    }
  });

  addEventListener("keyup", (e) => {
    if (e.code === "Space") {
      e.preventDefault();
      setBrake(false);
    }
  });

  // 창 밖으로 포커스가 나가면 키를 뗀 것을 못 받을 수 있으니 안전하게 브레이크를 밟은 상태로 둠
  addEventListener("blur", () => setBrake(true));

  console.log("키보드 조작 켜짐: Space=브레이크, ←→=핸들(Shift=크게), 0=가운데");
})();
