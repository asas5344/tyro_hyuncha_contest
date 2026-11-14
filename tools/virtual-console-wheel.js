// Pleos Virtual Console 레이싱 휠 조작 스크립트 (Thrustmaster T300 등)
//
// 맥에 꽂은 휠의 핸들·브레이크 페달 값을 크롬이 읽어서(Gamepad API) 콘솔에 넣어 줌.
// 앱은 평소처럼 Pleos SDK로 차량 신호를 받으므로 앱 코드는 바뀌지 않음.
//   휠 → (USB) → 맥 → 크롬의 Virtual Console 페이지 → 에뮬레이터 차량 신호 → 앱
//
// 사용법: ./tools/start-console.sh wheel → 열린 크롬 창에서 Cmd + Option + J → 붙여넣기 → Enter
//   1. 휠의 아무 버튼이나 한 번 누름 (크롬은 버튼을 눌러야 휠을 보여 줌)
//   2. 오른쪽 위 패널에서 [핸들 찾기] → 4초 동안 핸들을 좌우로 돌림
//   3. [브레이크 찾기] → 4초 안에 브레이크 페달을 끝까지 밟았다 뗌
//   4. 핸들을 가운데 두고 [가운데 맞추기]
//   5. [90도 맞추기] → 핸들을 오른쪽 90도로 돌린 채 기다림 (실제 각도와 화면 각도 맞추기)
//   (선택) [전후진 버튼] / [다시 하기 버튼] / [음성 명령 버튼] / [왼쪽 미러 버튼] / [오른쪽 미러 버튼] → 4초 안에 쓸 패들·버튼을 누름
// 찾은 설정은 브라우저에 저장돼서 다음부터는 1번만 하면 됨.
//
// 주의
//   - 키보드 스크립트(virtual-console-keyboard.js)와 같이 쓰지 않음. 그 스크립트는 창을 벗어나면
//     브레이크를 밟아 버려서 휠과 충돌함. 이미 붙여넣었다면 페이지를 새로고침한 뒤 이것만 실행.
//   - 콘솔 창이 화면에 보여야 함 (최소화하거나 다른 탭 뒤로 보내면 크롬이 휠 값을 멈춤).
//   - 휠이 안 보이거나 브레이크를 아직 못 찾았으면 안전하게 브레이크를 밟은 상태로 둠.
//   - 전후진 버튼은 누를 때마다 전진 → 후진 → 전진 순서로 보냄. 화면 버튼이나 음성으로 방향을 바꿨으면
//     휠 쪽 순서와 어긋나서 한 번 더 눌러야 할 수 있음. [다시 하기 버튼]을 누르면 전진부터 다시 셈.
//   - 음성 명령 버튼은 화면의 [음성 명령]을 누른 것과 같음 (한 번 누르면 듣기 시작, 듣는 중에 누르면 취소).
//   - 미러·전후진·다시 하기·음성 명령 버튼은 tools/start-console.sh가 켜 주는 연결 프로그램(steering-buttons-bridge.py)이 필요함.
(() => {
  if (window.__wheelControl) return console.log("휠 조작이 이미 켜져 있어요");
  if (window.__keyboardControl) {
    return console.warn("키보드 조작 스크립트가 켜져 있어요. 페이지를 새로고침한 뒤 휠 스크립트만 실행하세요.");
  }
  window.__wheelControl = true;

  // ── 설정 (브라우저에 저장) ──────────────────────────────────────────────
  // steerAxis: 핸들 축 번호 / center: 가운데일 때 축 값 / rangeDeg: 휠 끝에서 끝까지 회전 각도
  // gap: 핸들 축의 빈 구간 (아래 steerValue 설명) / null = 아직 모름
  // brake: { type: "axis"|"button", index, rest(뗀 값), pressed(밟은 값) } / prev·next: 미러 버튼 번호
  // dir: 전진↔후진을 바꾸는 버튼 번호 / restart: 다시 하기 버튼 번호 / voice: 음성 명령 버튼 번호
  const STORE = "tyroWheelConfig";
  const cfg = Object.assign(
    { steerAxis: 0, invert: false, rangeDeg: 1080, center: 0, gap: null, brake: null, prev: null, next: null, dir: null, restart: null, voice: null },
    JSON.parse(localStorage.getItem(STORE) || "{}"),
  );
  const save = () => localStorage.setItem(STORE, JSON.stringify(cfg));

  const MAX = 540;            // 콘솔 핸들 한계 (좌우 540도)
  const LEARN_SECONDS = 4;
  const brakePad = document.getElementById("brakePad");

  // ── 콘솔에 값 넣기 (키보드 스크립트와 같은 방식) ──────────────────────────
  let brakeSent = null;
  const setBrake = (pressed) => {
    if (pressed === brakeSent) return;
    if (pressed && parkTransitionPending) return;
    brakeSent = pressed;
    pedalControl.brake = true;   // 뗄 때도 true로 둬야 "뗌"이 차량에 전달됨
    pedals.brake = pressed;
    brakePad.classList.toggle("pressed", pressed);
    markLocalControl();
  };

  let angleSent = null;
  const setAngle = (angle) => {
    if (angleSent !== null && Math.abs(angle - angleSent) < 0.3) return;
    angleSent = angle;
    setSteeringAngle(angle, true);   // 오른쪽이 양수
  };

  // 미러 버튼: 누름·뗌 순서가 바뀌지 않도록 앞 요청이 끝난 뒤에 다음 요청을 보냄
  let queue = Promise.resolve();
  const sendButton = (key, action) => {
    queue = queue
      .then(() => fetch(`http://127.0.0.1:8089/button?key=${key}&action=${action}`))
      .catch(() => console.warn("핸들 버튼 연결 프로그램이 꺼져 있어요. 터미널에서 ./tools/start-console.sh wheel 을 다시 실행하세요"));
  };
  const mirrorHeld = { prev: false, next: false };

  // 전후진·다시 하기·음성 명령: 눌렀을 때 한 번만 보냄. 드라이버 없이 잡힌 휠은 버튼 여러 개가 한순간 같이 눌린 것처럼
  // 튀는 일이 있어서(기록에서 확인), 5프레임(약 0.08초) 이상 눌려 있고 동시에 눌린 버튼이 3개 이하일 때만 인정
  const TAP_TICKS = 5;
  const tapTicks = { dir: 0, restart: 0, voice: 0 };
  let reverse = false;   // 휠 쪽에서 세는 방향 (앱은 다시 하기 뒤 전진부터 시작)

  // ── 핸들 축 값 → -1~1 ───────────────────────────────────────────────────
  // 드라이버 없이 잡힌 휠은 가운데 근처 값을 보내지 않음: 0 다음이 바로 ±0.30 (T300 PC 모드에서 확인).
  // 그대로 쓰면 조금만 돌려도 화면 핸들이 160도쯤으로 튀므로, 이 빈 구간(gap)을 빼고 나머지를 0~1로 다시 폄.
  // gap은 지금까지 본 "0이 아닌 가장 작은 값"으로 스스로 찾음. 빈 구간이 없는 휠이면 gap이 0에 가까워져 영향이 없음.
  let gapCandidate = null;
  let gapCandidateTicks = 0;
  const steerValue = (raw) => {
    const x = raw - cfg.center;
    const size = Math.abs(x);
    // 한 프레임짜리 튀는 값에 속지 않도록, 더 작은 값이 3번 연속 보일 때만 gap을 줄임
    if (size > 0.005 && (cfg.gap === null || size < cfg.gap - 0.001)) {
      gapCandidate = gapCandidate === null ? size : Math.max(gapCandidate, size);
      if (++gapCandidateTicks >= 3) {
        cfg.gap = gapCandidate;
        save();
        gapCandidate = null;
        gapCandidateTicks = 0;
      }
    } else {
      gapCandidate = null;
      gapCandidateTicks = 0;
    }
    const gap = Math.min(cfg.gap ?? 0, 0.9);
    if (size <= gap) return 0;
    return Math.sign(x) * Math.min(1, (size - gap) / (1 - gap));
  };

  // ── 휠 찾기 ────────────────────────────────────────────────────────────
  const getPad = () => {
    const pads = [...navigator.getGamepads()].filter(Boolean);
    return pads.find((p) => /thrustmaster|t300|wheel|044f/i.test(p.id)) || pads[0] || null;
  };

  // ── 화면 패널 ──────────────────────────────────────────────────────────
  const panel = document.createElement("div");
  panel.style.cssText =
    "position:fixed;top:12px;right:12px;z-index:99999;width:300px;padding:12px;border-radius:12px;" +
    "background:rgba(20,22,28,.94);color:#fff;font:13px/1.5 -apple-system,sans-serif;box-shadow:0 4px 20px rgba(0,0,0,.4)";
  const line = (bold) => {
    const el = document.createElement("div");
    if (bold) el.style.fontWeight = "600";
    panel.appendChild(el);
    return el;
  };
  const title = line(true);
  const values = line();
  // 휠이 보내는 값 그대로 (문제 확인용): 페달을 밟을 때 어느 숫자가 변하는지 눈으로 볼 수 있음
  const rawLine = line();
  rawLine.style.cssText = "margin-top:4px;color:#9aa4b2;font:11px/1.4 ui-monospace,monospace;word-break:break-all";
  const message = line();
  message.style.cssText = "margin:6px 0;color:#ffd166;min-height:20px";
  const buttonRow = document.createElement("div");
  buttonRow.style.cssText = "display:flex;flex-wrap:wrap;gap:6px";
  panel.appendChild(buttonRow);
  const button = (label, onClick) => {
    const el = document.createElement("button");
    el.textContent = label;
    el.style.cssText = "padding:5px 9px;border-radius:8px;border:1px solid #555;background:#2b2f3a;color:#fff;cursor:pointer;font-size:12px";
    el.addEventListener("click", onClick);
    buttonRow.appendChild(el);
    return el;
  };
  const say = (text) => { message.textContent = text; };

  // ── 축·버튼 찾기: 몇 초 동안 가장 많이 변한 것을 고름 ──────────────────────
  // 주의 1) 크롬은 아직 한 번도 안 건드린 축을 0으로 주다가, 휠의 아무 입력이나 들어오면 -1 같은
  //         진짜 값으로 바꿈. 이 "0에서 튀는 것"을 움직임으로 착각하지 않도록, 처음 값이 정확히 0인
  //         축은 0인 표본을 세지 않음.
  // 주의 2) 드라이버 없이 잡힌 휠은 한 조작(페달·패들)이 축과 버튼 양쪽에 같이 나오기도 함.
  //         그래서 브레이크와 미러 버튼이 같은 신호를 쓰지 않는지 확인함.
  let learning = null;
  const learn = (prompt, done) => {
    const pad = getPad();
    if (!pad) return say("휠이 안 보여요. 휠의 버튼을 한 번 눌러 주세요.");
    learning = {
      prompt, done,
      until: performance.now() + LEARN_SECONDS * 1000,
      baseAxes: [...pad.axes],
      axes: pad.axes.map(() => ({ min: Infinity, max: -Infinity, last: 0 })),
      buttons: pad.buttons.map(() => 0),   // 가장 세게 눌린 값
    };
  };
  const updateLearning = (pad, now) => {
    pad.axes.forEach((v, i) => {
      const a = learning.axes[i];
      if (!a || (learning.baseAxes[i] === 0 && v === 0)) return;
      a.min = Math.min(a.min, v);
      a.max = Math.max(a.max, v);
      a.last = v;
    });
    pad.buttons.forEach((b, i) => { learning.buttons[i] = Math.max(learning.buttons[i] || 0, b.value); });
    const left = Math.ceil((learning.until - now) / 1000);
    if (left > 0) return say(`${learning.prompt} (${left}초)`);
    const result = learning;
    learning = null;
    result.done(result);
  };
  /** 그동안 축이 움직인 폭 */
  const rangeOf = (a) => (a && a.max >= a.min ? a.max - a.min : 0);
  const bestAxis = (r, skip) => {
    let best = -1;
    r.axes.forEach((a, i) => { if (i !== skip && (best < 0 || rangeOf(a) > rangeOf(r.axes[best]))) best = i; });
    return best;
  };
  const bestButton = (r, skip) => {
    let best = -1;
    r.buttons.forEach((v, i) => { if (!skip.includes(i) && (best < 0 || v > r.buttons[best])) best = i; });
    return best;
  };
  /** 브레이크 축이 이번 찾기 동안 같이 움직였는지 (= 방금 누른 것이 브레이크와 같은 신호) */
  const brakeMoved = (r) => {
    const b = cfg.brake;
    if (!b) return false;
    if (b.type === "button") return (r.buttons[b.index] || 0) >= 0.5;
    return rangeOf(r.axes[b.index]) >= Math.abs(b.pressed - b.rest) * 0.5;
  };

  button("핸들 찾기", () => learn("핸들을 좌우로 크게 돌리세요 (페달·버튼은 건드리지 마세요)", (r) => {
    const i = bestAxis(r, -1);
    if (i < 0 || rangeOf(r.axes[i]) < 0.2) return say("핸들 움직임을 못 찾았어요. 다시 눌러서 더 크게 돌려 보세요.");
    cfg.steerAxis = i;
    cfg.gap = null;
    save();
    say(`핸들 = 축 ${i}. 핸들을 가운데 두고 [가운데 맞추기]를 누르세요.`);
  }));
  button("브레이크 찾기", () => learn("브레이크 페달을 끝까지 밟았다 떼세요 (핸들은 가만히)", (r) => {
    const a = bestAxis(r, cfg.steerAxis);
    const b = bestButton(r, []);
    const axisRange = a >= 0 ? rangeOf(r.axes[a]) : 0;
    const buttonValue = b >= 0 ? r.buttons[b] : 0;
    if (axisRange >= 0.1 && axisRange >= buttonValue) {
      // 뗀 값 = 끝났을 때 값, 밟은 값 = 거기서 가장 멀리 간 값
      const { min, max, last } = r.axes[a];
      cfg.brake = { type: "axis", index: a, rest: last, pressed: Math.abs(max - last) >= Math.abs(min - last) ? max : min };
    } else if (buttonValue >= 0.5) {
      cfg.brake = { type: "button", index: b, rest: 0, pressed: buttonValue };
    } else {
      return say("브레이크를 못 찾았어요. 페달을 밟을 때 위 회색 숫자가 변하는지 보세요. 안 변하면 휠의 버튼·패들을 브레이크로 써도 돼요.");
    }
    // 같은 조작이 미러 버튼으로도 잡혀 있으면 미러 쪽을 지움 (브레이크가 우선)
    let cleared = "";
    for (const key of ["prev", "next"]) {
      if (cfg[key] !== null && (r.buttons[cfg[key]] || 0) >= 0.5) {
        cfg[key] = null;
        cleared = " 같은 신호를 쓰던 미러 버튼은 지웠어요.";
      }
    }
    save();
    say(`브레이크 = ${cfg.brake.type === "axis" ? "축" : "버튼"} ${cfg.brake.index}.${cleared}`);
  }));
  button("가운데 맞추기", () => {
    const pad = getPad();
    if (!pad) return say("휠이 안 보여요. 휠의 버튼을 한 번 눌러 주세요.");
    cfg.center = pad.axes[cfg.steerAxis] || 0;
    cfg.gap = null;
    save();
    say("지금 핸들 위치를 가운데(0도)로 맞췄어요.");
  });
  // 실제 핸들 각도와 화면 각도를 맞춤: 실제로 90도 돌렸을 때의 축 값으로 회전 범위와 방향을 계산.
  // 드라이버 없이 잡힌 휠은 축 값이 실제 회전 범위와 달라서(조금만 돌려도 끝까지 감) 직접 재야 함.
  button("90도 맞추기", () => learn("핸들을 오른쪽으로 90도(3시 방향) 돌린 채 기다리세요", () => {
    const pad = getPad();
    const offset = pad ? steerValue(pad.axes[cfg.steerAxis] || 0) : 0;
    if (Math.abs(offset) < 0.02) return say("핸들이 가운데에 있어요. 먼저 [가운데 맞추기]를 하고, 90도 돌린 채로 다시 해 보세요.");
    cfg.rangeDeg = Math.round(180 / Math.abs(offset));   // 축 1 = 휠 끝 = rangeDeg의 절반
    cfg.invert = offset < 0;                             // 오른쪽으로 돌렸는데 값이 음수면 뒤집음
    save();
    say(`맞췄어요. 회전 범위 ${cfg.rangeDeg}도${cfg.invert ? ", 좌우 반전 켬" : ""}. 지금 패널 각도가 90 근처면 정상이에요.`);
  }));
  button("좌우 반전", () => {
    cfg.invert = !cfg.invert;
    save();
    say(cfg.invert ? "좌우 반전 켬" : "좌우 반전 끔");
  });
  button("회전 범위", () => {
    const input = prompt("휠의 끝에서 끝까지 회전 각도 (T300 기본 1080). 화면 핸들이 실제보다 많이 돌면 줄이세요.", cfg.rangeDeg);
    const deg = Number(input);
    if (!input || !(deg >= 90 && deg <= 20000)) return;
    cfg.rangeDeg = deg;
    save();
    say(`회전 범위 ${deg}도`);
  });
  // 버튼 하나를 기능에 연결. 이미 다른 기능에 쓰는 버튼과 브레이크와 같은 신호는 고르지 못하게 함
  const BUTTON_KEYS = ["prev", "next", "dir", "restart", "voice"];
  const learnButton = (key, name) => () => learn(`${name}(으)로 쓸 패들·버튼을 누르세요`, (r) => {
    if (brakeMoved(r)) return say("방금 누른 것은 브레이크와 같은 신호를 보내요. 다른 버튼을 골라 주세요.");
    const taken = BUTTON_KEYS.filter((k) => k !== key && cfg[k] !== null).map((k) => cfg[k]);
    const i = bestButton(r, taken);
    if (i < 0 || r.buttons[i] < 0.5) return say("눌린 버튼을 못 찾았어요. (다른 기능에 이미 쓰는 버튼은 고를 수 없어요)");
    cfg[key] = i;
    save();
    say(`${name} = 버튼 ${i}`);
  });
  button("전후진 버튼", learnButton("dir", "전진↔후진 바꾸기"));
  button("다시 하기 버튼", learnButton("restart", "다시 하기"));
  button("음성 명령 버튼", learnButton("voice", "음성 명령"));
  button("왼쪽 미러 버튼", learnButton("prev", "왼쪽 미러"));
  button("오른쪽 미러 버튼", learnButton("next", "오른쪽 미러"));
  button("설정 지우기", () => {
    localStorage.removeItem(STORE);
    Object.assign(cfg, { steerAxis: 0, invert: false, rangeDeg: 1080, center: 0, gap: null, brake: null, prev: null, next: null, dir: null, restart: null, voice: null });
    say("설정을 지웠어요. [핸들 찾기]부터 다시 하세요.");
  });
  document.body.appendChild(panel);

  // ── 매 프레임: 휠 값을 읽어 콘솔에 넣음 ──────────────────────────────────
  // requestAnimationFrame은 창이 가려지면 멈추므로 setInterval을 씀
  const tick = () => {
    const now = performance.now();
    const pad = getPad();
    if (!pad) {
      setBrake(true);
      title.textContent = "휠 조작: 휠을 찾는 중";
      values.textContent = "휠의 아무 버튼이나 한 번 눌러 주세요.";
      return;
    }
    title.textContent = `휠 조작: ${pad.id.slice(0, 28)}`;
    if (learning) updateLearning(pad, now);

    // 핸들: 축 값(-1~1)을 각도로. 휠 끝이 rangeDeg의 절반
    const raw = pad.axes[cfg.steerAxis] || 0;
    const angle = Math.max(-MAX, Math.min(MAX, steerValue(raw) * (cfg.rangeDeg / 2) * (cfg.invert ? -1 : 1)));
    setAngle(angle);

    // 브레이크: 뗀 값 0 ~ 밟은 값 1로 바꿔서 판단. 모르면 밟은 것으로 둠
    let amount = null;
    if (cfg.brake) {
      const value = cfg.brake.type === "axis" ? pad.axes[cfg.brake.index] : pad.buttons[cfg.brake.index]?.value;
      if (value != null) amount = (value - cfg.brake.rest) / (cfg.brake.pressed - cfg.brake.rest);
    }
    if (amount === null) setBrake(true);
    else if (amount > 0.2) setBrake(true);
    else if (amount < 0.1) setBrake(false);   // 0.1~0.2 사이는 그대로 (경계에서 떨리지 않게)

    // 미러 버튼
    for (const key of ["prev", "next"]) {
      if (cfg[key] === null) continue;
      const down = !!pad.buttons[cfg[key]]?.pressed;
      if (down !== mirrorHeld[key]) {
        mirrorHeld[key] = down;
        sendButton(key, down ? "down" : "up");
      }
    }

    // 전후진·다시 하기·음성 명령 버튼
    const downCount = pad.buttons.filter((b) => b.pressed).length;
    for (const key of ["dir", "restart", "voice"]) {
      // 버튼을 찾는 중에는 보내지 않음 (찾으려고 누른 것이 방향을 바꾸면 안 되므로)
      const down = !learning && cfg[key] !== null && !!pad.buttons[cfg[key]]?.pressed && downCount <= 3;
      tapTicks[key] = down ? tapTicks[key] + 1 : 0;
      if (tapTicks[key] !== TAP_TICKS) continue;   // 딱 한 번만
      if (key === "dir") {
        reverse = !reverse;
        sendButton(reverse ? "reverse" : "forward", "tap");
        say(reverse ? "후진으로 바꿨어요" : "전진으로 바꿨어요");
      } else if (key === "restart") {
        reverse = false;
        sendButton("restart", "tap");
        say("다시 하기");
      } else {
        sendButton("voice", "tap");
        say("음성 명령 버튼을 눌렀어요");
      }
    }

    const pressedButtons = pad.buttons.map((b, i) => (b.value > 0.05 ? i : null)).filter((i) => i !== null);
    rawLine.textContent =
      `축 ${pad.axes.map((v, i) => `${i}:${v.toFixed(2)}`).join(" ")} / 눌린 버튼 ${pressedButtons.join(",") || "없음"}`;

    values.textContent =
      `핸들 ${angle.toFixed(0)}° · 브레이크 ` +
      (amount === null ? "미설정(밟음 유지)" : `${brakeSent ? "밟음" : "뗌"} ${Math.round(Math.max(0, Math.min(1, amount)) * 100)}%`);
  };
  setInterval(tick, 16);

  // 창이 가려지면 크롬이 휠 값을 멈추므로 안전하게 브레이크를 밟은 상태로 둠
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) setBrake(true);
  });

  say(cfg.brake ? "저장된 설정을 불러왔어요." : "[핸들 찾기] → [브레이크 찾기] → [가운데 맞추기] 순서로 설정하세요.");
  console.log("휠 조작 켜짐: 휠 버튼을 한 번 누른 뒤 오른쪽 위 패널을 보세요");
})();
