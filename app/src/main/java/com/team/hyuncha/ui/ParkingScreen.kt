package com.team.hyuncha.ui

import ai.pleos.playground.vehicle.Vehicle
import android.util.Log
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.data.RankResult
import com.team.hyuncha.data.Ranking
import com.team.hyuncha.data.ScoreRecord
import com.team.hyuncha.data.ScoreRecords
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import com.team.hyuncha.input.TouchInput
import com.team.hyuncha.input.SteeringUnit
import com.team.hyuncha.input.VehicleConnectionFailure
import com.team.hyuncha.input.VehicleInput
import com.team.hyuncha.input.VehicleSignal
import com.team.hyuncha.input.VehicleStage
import com.team.hyuncha.input.LookButtons
import com.team.hyuncha.input.LookSide
import com.team.hyuncha.input.VoiceCommand
import com.team.hyuncha.input.VoiceCommands
import com.team.hyuncha.input.VoiceInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.team.hyuncha.safety.LockState
import com.team.hyuncha.safety.SafetyLock
import com.team.hyuncha.sim.HardSituation
import com.team.hyuncha.sim.ParkingMaps
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.Coach
import com.team.hyuncha.sim.CoachPlan
import com.team.hyuncha.sim.SimEvent
import com.team.hyuncha.sim.ParkingScore
import com.team.hyuncha.sim.Scoring
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.SimStatus
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import com.team.hyuncha.ui.theme.CameraControl
import com.team.hyuncha.ui.theme.CameraControlOutline
import com.team.hyuncha.ui.theme.CameraControlSelected
import com.team.hyuncha.ui.theme.CameraControlSelectedText
import com.team.hyuncha.ui.theme.CameraOverlay
import com.team.hyuncha.ui.theme.CameraSecondaryText
import com.team.hyuncha.ui.theme.LocalAppTextScale

/** 입력 방식: 화면 조작(개발용) / 실제 차량(Pleos SDK) */
enum class InputMode(val label: String) {
    TOUCH("화면 조작"),
    VEHICLE("실제 차량"),
}

/**
 * 주차 연습 화면: 탑뷰 전체 화면 + 아래쪽 작은 전진/후진 버튼.
 * 실제 차량(Virtual Console) 입력을 사용한다. 개발·테스트에서 vehicle을 전달하지 않으면 화면 조작을 사용한다.
 * @param vehicle Pleos SDK. null이면 화면 조작만 가능
 *
 */
@Composable
fun ParkingScreen(
    vehicle: Vehicle?, modifier: Modifier = Modifier,
    initialCar: CarSpec = CarPresets.GRANDEUR,
    parkingMode: ParkingMode = ParkingMode.PERPENDICULAR,
    steeringUnit: SteeringUnit = SteeringUnit.STEERING_WHEEL,
    invertSteering: Boolean = true,
    carCamera: CarCamera = CarCamera.REAR_ONLY,
    driverName: String = "나",
    records: ScoreRecords? = null,
    hardMode: Boolean = false,
    hardSituation: HardSituation? = null,
    vehicleInitializationFailure: Exception? = null,
) {
    val car = initialCar
    val currentCar by rememberUpdatedState(car)
    var mode by remember { mutableStateOf(if (vehicle != null) InputMode.VEHICLE else InputMode.TOUCH) }
    val touchInput = remember { TouchInput() }
    val vehicleInput = remember(vehicle) {
        vehicle?.let { VehicleInput(it) { currentCar.steeringRatio } }
    }
    val startupFailure = remember(vehicleInitializationFailure) {
        vehicleInitializationFailure?.let { VehicleConnectionFailure.from(VehicleStage.INIT, VehicleSignal.VEHICLE, it) }
    }
    val failure = startupFailure ?: vehicleInput?.connectionFailure
    LaunchedEffect(vehicleInput, mode, startupFailure) {
        if (mode == InputMode.VEHICLE && vehicleInput != null && startupFailure == null) {
            try {
                vehicleInput.start()
                if (vehicleInput.ready) awaitCancellation()
            } finally {
                vehicleInput.stop()
            }
        }
    }
    if (failure != null || (mode == InputMode.VEHICLE && vehicleInput?.ready == false)) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NoticeCard(
                text = failure?.message ?: "차량 연결을 확인하고 있어요.",
                modifier = Modifier.padding(16.dp),
                locked = failure != null,
                errorCode = failure?.code,
            )
        }
        return
    }

    // 시점: 운전석(1인칭, 기본) ↔ 탑뷰
    var firstPerson by rememberSaveable { mutableStateOf(true) }

    // 설정 화면의 조향값 해석을 차량 입력에 적용
    SideEffect {
        vehicleInput?.steeringUnit = steeringUnit
        vehicleInput?.invertSteering = invertSteering
    }

    // 다시 할 때마다 round가 바뀌어 목표 칸·주차된 차가 새로 배치됨
    var round by remember { mutableIntStateOf(0) }
    val sim = remember(car, round, parkingMode, hardMode, hardSituation) {
        ParkingSimulator(car, if (hardMode) ParkingMaps.randomHard(situation = hardSituation) else ParkingMaps.random(parkingMode))
    }
    val frame = remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }

    // 모범 주행: 판이 시작되면 뒤에서 미리 계산 (차량 화면에서 1초 안쪽)
    // planDone: 계산이 끝났는지 (못 찾아서 null이어도 true). 점수의 주행 시간이 모범 주행에 달려 있어서 랭킹 저장은 이후에
    var planDone by remember(sim) { mutableStateOf(false) }
    val plan by produceState<CoachPlan?>(initialValue = null, sim) {
        value = withContext(Dispatchers.Default) {
            val started = System.nanoTime()
            Coach.solve(car, sim.map).also {
                // 차량 화면에서 얼마나 걸리는지 확인용 (Logcat에서 "CoachTiming"으로 검색)
                Log.d("CoachTiming", "${sim.map.mode} ${car.name}: ${(System.nanoTime() - started) / 1_000_000}ms, 찾음=${it != null}")
            }
        }
        planDone = true
    }

    // 다시 보기 상태 (새 판이 시작되면 초기화). 내 주행 또는 모범 주행을 재생
    var replaying by remember(sim) { mutableStateOf(false) }
    var replayModel by remember(sim) { mutableStateOf(false) }
    val replayClock = remember(sim) { mutableDoubleStateOf(0.0) }
    var replayPlaying by remember(sim) { mutableStateOf(true) }
    var replaySpeed by remember(sim) { mutableDoubleStateOf(1.0) }

    val input: DrivingInput = if (mode == InputMode.VEHICLE && vehicleInput != null) vehicleInput else touchInput
    val lock = if (input === vehicleInput) SafetyLock.check(vehicleInput?.gear) else LockState.OK
    val currentInput by rememberUpdatedState(input)
    val currentLock by rememberUpdatedState(lock)

    // 새 판 또는 화면 이탈 시 재생 중인 클락션도 정리한다.
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val horn = remember(sim) { if (sim.map.rules.isHard) HornPlayer(context) else null }
    DisposableEffect(horn) { onDispose { horn?.release() } }
    val hornNext = remember(sim) { intArrayOf(0) }
    val hornStartedAt = remember(sim) { mutableLongStateOf(0L) }
    val hornDurationMs = remember(sim) { intArrayOf(0) }   // 방금 울린 경적 파일의 길이
    DisposableEffect(horn, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                horn?.stop()
                hornStartedAt.longValue = 0L
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // 게임 루프: 화면이 그려질 때마다(약 60fps) 시뮬레이터를 한 번씩 진행
    LaunchedEffect(sim) {
        var last = withFrameNanos { it }
        var lastShown: ShownState? = null
        while (true) {
            withFrameNanos { now ->
                // 프레임이 밀려도 한 번에 너무 멀리 가지 않도록 최대 50ms로 제한
                val dt = ((now - last) / 1e9).coerceAtMost(0.05)
                last = now
                if (replaying) {
                    // 다시 보여주기: 시뮬레이터는 멈추고 재생 시각만 앞으로
                    if (replayPlaying) {
                        val source = if (replayModel) plan?.run ?: sim else sim
                        val end = source.history.last().timeSec
                        replayClock.doubleValue = (replayClock.doubleValue + dt * replaySpeed).coerceAtMost(end)
                        if (replayClock.doubleValue >= end) replayPlaying = false
                    }
                } else if (currentLock == LockState.OK) {
                    // 안전 잠금이 풀려 있으면 시뮬레이터를 멈춤
                    sim.step(currentInput, dt)
                    val horns = sim.map.rules.hornTimesSec
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        sim.status == SimStatus.DRIVING && sim.startedAtSec != null &&
                        hornNext[0] < horns.size && sim.clockSec >= horns[hornNext[0]]
                    ) {
                        hornDurationMs[0] = horn?.honk(hornNext[0]) ?: 0
                        hornNext[0]++
                        hornStartedAt.longValue = now
                    }
                }
                // 화면 갱신(frame)은 보이는 값이 바뀌었거나 경적 강조 중일 때만 매번 하고, 그 밖에는 0.1초마다
                // (목표 칸 깜빡임·하드 모드 남은 시간용). 차가 멈춰 있을 때 미니맵·탑뷰를 계속 다시 그리지 않아 CPU를 아낌
                val shown = ShownState(sim.pose, sim.wheelAngleDeg, sim.status, currentInput.brakePressed,
                    currentInput.direction, replaying, replayClock.doubleValue)
                val flashing = hornStartedAt.longValue != 0L && now - hornStartedAt.longValue < hornDurationMs[0] * 1_000_000L
                if (shown != lastShown || flashing || now - frame.longValue >= IDLE_FRAME_NS) {
                    lastShown = shown
                    frame.longValue = now
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun startReplay(model: Boolean) {
        replayModel = model && plan != null
        replayClock.doubleValue = 0.0
        replayPlaying = true
        replaying = true
    }

    fun restart() {
        round++
        touchInput.reset()
        vehicleInput?.reset()
    }

    // 음성 명령 (Pleos SpeechToText). 화면을 떠나면 해제
    val voice = remember { VoiceInput(context) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }   // 알아들은 결과를 잠깐 보여줌
    val onVoice by rememberUpdatedState { cmd: VoiceCommand?, said: String ->
        val finished = sim.status != SimStatus.DRIVING
        voiceMessage = when (cmd) {
            null -> if (said.isBlank()) "잘 못 들었어요. 다시 말해 주세요" else "\"$said\" — 모르는 명령이에요"
            VoiceCommand.SHOW_MODEL, VoiceCommand.SHOW_MINE ->
                if (!finished) "주차가 끝나면 볼 수 있어요"
                else if (cmd == VoiceCommand.SHOW_MODEL && plan == null) "모범 주행을 계산하고 있어요"
                else { startReplay(model = cmd == VoiceCommand.SHOW_MODEL); "\"$said\" → ${cmd.label}" }
            VoiceCommand.CLOSE -> { replaying = false; "\"$said\" → ${cmd.label}" }
            // 앱은 실제 기어를 P에 둔 채로 쓰므로 P로 바꿀 일은 없음 → 안내만
            VoiceCommand.PARK_INFO -> "실제 기어는 P 그대로 두고, 전진·후진은 \"${VoiceCommand.FORWARD.phrase}\" / \"${VoiceCommand.REVERSE.phrase}\"로 바꿔요"
            VoiceCommand.TOGGLE_DIRECTION -> {
                input.direction = if (input.direction == Direction.FORWARD) Direction.REVERSE else Direction.FORWARD
                "\"$said\" → ${input.direction.label}으로"
            }
            else -> {
                when (cmd) {
                    VoiceCommand.FORWARD -> input.direction = Direction.FORWARD
                    VoiceCommand.REVERSE -> input.direction = Direction.REVERSE
                    VoiceCommand.RESTART -> restart()
                    VoiceCommand.LOOK_LEFT -> { firstPerson = true; LookButtons.lookAt(LookSide.LEFT) }
                    VoiceCommand.LOOK_RIGHT -> { firstPerson = true; LookButtons.lookAt(LookSide.RIGHT) }
                    VoiceCommand.LOOK_FRONT -> LookButtons.lookAt(LookSide.FRONT)
                    VoiceCommand.TOP_VIEW -> firstPerson = false
                    VoiceCommand.DRIVER_VIEW -> firstPerson = true
                    else -> Unit
                }
                "\"$said\" → ${cmd.label}"
            }
        }
    }
    DisposableEffect(voice) {
        voice.onCommand = { cmd, said -> onVoice(cmd, said) }
        onDispose {
            voice.release()
        }
    }
    LaunchedEffect(voiceMessage) {
        if (voiceMessage != null) { delay(3000); voiceMessage = null }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth * 0.48f, 440.dp * (LocalDensity.current.fontScale * LocalAppTextScale.current).coerceAtLeast(1f))
        Row(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                // 에뮬레이터에서 키보드로 테스트하기 위한 단축키 (핸들·진행은 화면 조작 모드에서만)
                .onPreviewKeyEvent { e ->
                    val down = e.type == KeyEventType.KeyDown
                    when (e.key) {
                        Key.DirectionLeft -> { if (down) touchInput.steerBy(-STEER_KEY_STEP, car); true }
                        Key.DirectionRight -> { if (down) touchInput.steerBy(STEER_KEY_STEP, car); true }
                        Key.DirectionUp -> { if (down) input.direction = Direction.FORWARD; true }
                        Key.DirectionDown -> { if (down) input.direction = Direction.REVERSE; true }
                        Key.Spacebar -> { touchInput.brakePressed = !down; true }  // 누르는 동안 진행
                        Key.R -> { if (down) restart(); true }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val replayView = {
                    if (!replaying) null
                    else {
                        val model = plan?.run
                        if (replayModel && model != null) ReplayView(model, replayClock.doubleValue, isModel = true, compare = sim)
                        else ReplayView(sim, replayClock.doubleValue, isModel = false, compare = model)
                    }
                }
                if (firstPerson) RealisticParkingView(
                    sim = sim,
                    frame = { frame.longValue },
                    direction = { input.direction },
                    braking = { input.brakePressed },
                    modifier = Modifier.fillMaxSize(),
                    replay = replayView,
                    carCamera = carCamera,
                ) else ParkingCanvas(
                    sim = sim,
                    frame = { frame.longValue },
                    direction = { input.direction },
                    braking = { input.brakePressed },
                    modifier = Modifier.fillMaxSize(),
                    replay = replayView,
                )
                SimHud(
                    sim = sim,
                    frame = { frame.longValue },
                    lock = lock,
                    waitingForBrake = input === vehicleInput && vehicleInput?.armed == false,
                    viewLabel = if (firstPerson) "3D" else "탑뷰",
                    brakeText = { if (input === vehicleInput) vehicleInput?.brakeText() ?: "확인 중" else if (input.brakePressed) "밟음" else "뗌" },
                    replaying = replaying,
                    plan = plan,
                    planDone = planDone,
                    driverName = driverName,
                    records = records,
                    onRestart = ::restart,
                    onReplay = { startReplay(model = false) },
                    onShowModel = { startReplay(model = true) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (replaying) {
                    val model = plan
                    ReplayBar(
                        source = if (replayModel && model != null) model.run else sim,
                        plan = if (replayModel) model else null,
                        canSwitch = model != null,
                        time = replayClock.doubleValue,
                        playing = replayPlaying,
                        speed = replaySpeed,
                        onPlayPause = {
                            // 끝까지 본 상태에서 재생을 누르면 처음부터
                            val source = if (replayModel) plan?.run ?: sim else sim
                            if (!replayPlaying && replayClock.doubleValue >= source.history.last().timeSec) replayClock.doubleValue = 0.0
                            replayPlaying = !replayPlaying
                        },
                        onSwitch = { startReplay(model = !replayModel) },
                        onSeek = { replayClock.doubleValue = it; replayPlaying = false },
                        onSpeedChange = { replaySpeed = it },
                        onClose = { replaying = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp),
                    )
                }
                // 시점 바꾸기 + 음성 명령 (왼쪽 아래)
                if (!replaying) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { firstPerson = !firstPerson },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = CameraControl),
                            border = BorderStroke(1.dp, CameraControlOutline),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(64.dp).testTag("toggle_view"),
                        ) {
                            Text(if (firstPerson) "탑뷰로 보기" else "운전석으로 보기", color = Color.White, fontSize = 22.sp * LocalAppTextScale.current)
                        }
                        VoiceButton(voice, Modifier.height(64.dp))
                    }
                }
                // 하드 모드: 남은 시간·상황, 클락션
                if (sim.map.rules.isHard && !replaying) {
                    HardModeBar(sim, { frame.longValue }, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                }
                // 음성 명령: 듣는 중이면 들린 말, 끝나면 결과를 위쪽 가운데에 잠깐 보여줌
                VoiceBubble(voice, voiceMessage, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
                // 실제 차량 모드: 메뉴 대신 전진/후진과 다시 하기만 작게 띄움 (기어는 P 고정이라 방향은 앱에서 고름)
                if (input === vehicleInput && !replaying) {
                    DirectionBar(
                        direction = input.direction,
                        onDirectionChange = { input.direction = it },
                        onRestart = ::restart,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                    )
                }
            }
            // 개발용 화면 조작 모드에서만 오른쪽 조작 패널을 보여줌
            if (input === touchInput) {
                ControlPanel(
                    car = car,
                    mode = mode,
                    vehicleAvailable = vehicleInput != null,
                    onModeChange = { mode = it; restart() },
                    input = input,
                    touchInput = touchInput,
                    vehicleInput = vehicleInput,
                    onRestart = ::restart,
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                )
            }
        }
        if (sim.map.rules.isHard && !replaying) {
            HornFlashIfOn({ frame.longValue }, hornStartedAt.longValue, hornDurationMs[0], Modifier.matchParentSize())
        }
    }
}

/** 화면에 보이는 값 묶음. 이전 프레임과 같으면 다시 그리지 않음 */
private data class ShownState(
    val pose: CarPose, val wheelAngleDeg: Double, val status: SimStatus, val braking: Boolean,
    val direction: Direction, val replaying: Boolean, val replayClock: Double,
)

/** 바뀐 게 없을 때 화면을 갱신하는 간격 (0.1초) */
private const val IDLE_FRAME_NS = 100_000_000L

/** 왼쪽 위 상태 표시 + 안내·결과 카드. frame을 읽어서 이 부분만 매 프레임 갱신됨 */
@Composable
private fun SimHud(
    sim: ParkingSimulator,
    frame: () -> Long,
    lock: LockState,
    waitingForBrake: Boolean,
    viewLabel: String,
    brakeText: () -> String,
    replaying: Boolean,
    plan: CoachPlan?,
    planDone: Boolean,
    driverName: String,
    records: ScoreRecords?,
    onRestart: () -> Unit,
    onReplay: () -> Unit,
    onShowModel: () -> Unit,
    modifier: Modifier,
) {
    frame()
    // 결과가 나오면(또는 모범 주행 계산이 끝나면) 한 번만 피드백을 만듦
    val feedback = remember(sim, sim.status, plan) {
        if (sim.status == SimStatus.DRIVING) emptyList() else Coach.feedback(sim, plan)
    }
    // 점수: 성공했을 때만. 시간 항목은 같은 주차장의 모범 주행과 비교 (모범 주행은 판 시작 때 미리 계산됨)
    val score = remember(sim, sim.status, plan) {
        if (sim.status == SimStatus.PARKED) Scoring.score(sim, plan?.run) else null
    }
    // 랭킹: 성공한 판마다 한 번만 저장 (모범 주행 계산이 끝나 점수가 확정된 뒤)
    var rank by remember(sim) { mutableStateOf<RankResult?>(null) }
    // 하드 모드는 랭킹에 넣지 않음 (나중에 랭킹 모드를 따로 만들 예정)
    val ready = score != null && planDone && records != null && !sim.map.rules.isHard
    LaunchedEffect(sim, ready) {
        if (ready && rank == null) {
            rank = records!!.add(ScoreRecord(Ranking.cleanName(driverName), sim.map.mode, sim.car.name, score!!.total, score.grade, System.currentTimeMillis()))
        }
    }
    val rankText = when {
        records == null || score == null || sim.map.rules.isHard -> null
        rank == null -> "랭킹 계산 중…"
        rank!!.total == 1 -> "${sim.map.mode.label} 첫 기록! (${Ranking.cleanName(driverName)})"
        rank!!.isBest -> "${sim.map.mode.label} 신기록! 1위 / ${rank!!.total}개 기록"
        else -> "${sim.map.mode.label} 랭킹 ${rank!!.rank}위 / ${rank!!.total}개 기록"
    }
    // 다시 보여주기 중에는 결과 카드·안내를 가리지 않도록 숨김
    if (replaying) return
    Box(modifier.padding(16.dp)) {
        // 화면에는 안 보이고, 자동 테스트가 고른 주차 방식으로 시작됐는지 확인하는 표시
        Spacer(Modifier.size(0.dp).testTag("parking_mode_${sim.map.mode.name}"))
        Surface(
            color = CameraOverlay,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Column(Modifier.padding(12.dp)) {
                HudText("$viewLabel · ${sim.car.name}", bold = true)
                HudText("브레이크 ${brakeText()}")
                HudText("바퀴 각도 ${fmt(sim.wheelAngleDeg)}°")
                // 핸들 각도 = 바퀴 각도 × 조향비 (오른쪽이 +)
                HudText("핸들 각도 ${fmt(sim.wheelAngleDeg * sim.car.steeringRatio)}°")
            }
        }

        when {
            lock.message != null -> NoticeCard(lock.message, Modifier.align(Alignment.Center), locked = true)
            sim.status == SimStatus.TIMEOUT -> ResultCard(
                title = "시간 초과",
                lines = listOf("제한 시간 ${sim.map.rules.timeLimitSec?.toInt() ?: 0}초가 지났어요"),
                feedback = feedback,
                modelReady = plan != null,
                onRestart = onRestart,
                onReplay = onReplay,
                onShowModel = onShowModel,
                modifier = Modifier.align(Alignment.Center),
            )
            sim.status == SimStatus.COLLIDED -> ResultCard(
                title = "부딪혔어요",
                lines = listOfNotNull(sim.collision?.message),
                feedback = feedback,
                modelReady = plan != null,
                onRestart = onRestart,
                onReplay = onReplay,
                onShowModel = onShowModel,
                modifier = Modifier.align(Alignment.Center),
            )
            sim.status == SimStatus.PARKED -> score?.let { sc ->
                ResultCard(
                    title = "주차 성공!",
                    lines = emptyList(),
                    score = sc,
                    rankText = rankText,
                    feedback = feedback,
                    modelReady = plan != null,
                    onRestart = onRestart,
                    onReplay = onReplay,
                    onShowModel = onShowModel,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            waitingForBrake -> NoticeCard(
                "브레이크를 한 번 밟으면 시작해요.\n브레이크를 떼면 천천히 움직여요.",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
            )
        }
    }
}

@Composable
private fun HornFlashIfOn(frame: () -> Long, startedAt: Long, durationMs: Int, modifier: Modifier) {
    if (startedAt == 0L) return
    val elapsedMs = (frame() - startedAt) / 1_000_000f
    if (elapsedMs < durationMs) {
        val strength = ((durationMs - elapsedMs) / 650f).coerceIn(0f, 1f)
        HornFlash(strength, modifier)
    }
}

/** 음성 명령 버튼: 누르면 한 번 듣고, 듣는 중에 다시 누르면 그만 들음 */
@Composable
private fun VoiceButton(voice: VoiceInput, modifier: Modifier) {
    val listening = voice.state == VoiceInput.State.LISTENING
    val usable = voice.state == VoiceInput.State.READY || listening
    val color = when {
        listening -> CameraControlSelectedText
        usable -> Color.White
        else -> CameraSecondaryText
    }
    OutlinedButton(
        onClick = { voice.toggle() },
        enabled = usable,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (listening) CameraControlSelected else CameraControl,
            disabledContainerColor = CameraOverlay,
        ),
        border = BorderStroke(1.dp, CameraControlOutline),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag("voice_button"),
    ) {
        OutlineIcon("mic", Modifier.size(28.dp), color)
        Spacer(Modifier.width(8.dp))
        Text(
            when (voice.state) {
                VoiceInput.State.LISTENING -> "듣는 중…"
                VoiceInput.State.PREPARING -> "음성 준비 중"
                VoiceInput.State.UNAVAILABLE -> "음성 사용 불가"
                VoiceInput.State.READY -> "음성 명령"
            },
            color = color, fontSize = 22.sp * LocalAppTextScale.current,
        )
    }
}

/** 듣는 중: 들린 말과 예시 / 끝난 뒤: 알아들은 결과 */
@Composable
private fun VoiceBubble(voice: VoiceInput, message: String?, modifier: Modifier) {
    val listening = voice.state == VoiceInput.State.LISTENING
    if (!listening && message == null) return
    val textSize = 22.sp * LocalAppTextScale.current
    Surface(color = CameraOverlay, shape = RoundedCornerShape(20.dp), modifier = modifier.testTag("voice_bubble")) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (listening) {
                Text(if (voice.heard.isBlank()) "듣고 있어요…" else "\"${voice.heard}\"", color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold)
                Text("예: " + VoiceCommands.examples.joinToString(" · ") { "\"$it\"" }, color = CameraSecondaryText, fontSize = textSize * 0.75f)
            } else if (message != null) {
                Text(message, color = Color(0xFF8EE6B1), fontSize = textSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 실제 차량 모드의 전진/후진 + 다시 하기 버튼 (오른쪽 메뉴를 대신함) */
@Composable
private fun DirectionBar(
    direction: Direction,
    onDirectionChange: (Direction) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        color = CameraOverlay,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Direction.entries.forEach { d ->
                val isSelected = direction == d
                Button(
                    onClick = { onDirectionChange(d) },
                    colors = cameraButtonColors(isSelected),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(64.dp).semantics { selected = isSelected },
                ) {
                    Text(if (d == Direction.FORWARD) "전진 (D)" else "후진 (R)", fontSize = 22.sp * LocalAppTextScale.current)
                }
            }
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.height(64.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CameraControlOutline),
            ) {
                Text("다시", color = Color.White, fontSize = 22.sp * LocalAppTextScale.current)
            }
        }
    }
}

/** HUD에 보여줄 실제 브레이크 페달 상태 */
private fun VehicleInput.brakeText(): String = when (pedalPressed) {
    null -> "확인 중"
    true -> "밟음"
    false -> "뗌"
}

@Composable
private fun HudText(text: String, bold: Boolean = false) {
    Text(
        text,
        color = Color.White,
        fontSize = 24.sp * LocalAppTextScale.current,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun NoticeCard(text: String, modifier: Modifier, locked: Boolean = false, errorCode: String? = null) {
    val colors = MaterialTheme.colorScheme
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = if (locked) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (locked) colors.onErrorContainer else colors.onSurface,
    )) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (errorCode != null) {
                Text(
                    "오류 코드 $errorCode",
                    modifier = Modifier.padding(top = 6.dp).testTag("vehicle_connection_error"),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    lines: List<String>,
    feedback: List<String>,
    score: ParkingScore? = null,
    rankText: String? = null,
    modelReady: Boolean,
    onRestart: () -> Unit,
    onReplay: () -> Unit,
    onShowModel: () -> Unit,
    modifier: Modifier,
) {
    Card(modifier.widthIn(min = 420.dp, max = if (score != null) 920.dp else 640.dp)) {
        Column(
            Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = if (score == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            lines.forEach { Text(it, textAlign = TextAlign.Center) }
            if (score != null) {
                ScoreHeader(score)
                if (rankText != null) {
                    Text(rankText, Modifier.padding(top = 8.dp).testTag("rank_text"), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ScoreBreakdown(score, Modifier.weight(1f))
                    if (feedback.isNotEmpty()) CoachFeedback(feedback, Modifier.weight(0.8f))
                }
            } else if (feedback.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                CoachFeedback(feedback, Modifier)
            }
            Spacer(Modifier.height(20.dp))
            // 버튼 글자가 두 줄로 꺾이지 않게 한 줄로 고정
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onShowModel, enabled = modelReady) {
                    Text(if (modelReady) "모범 주행 보기" else "모범 주행 계산 중…", maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onReplay) { Text("내 주행 보기", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = onRestart) { Text("다시 하기", maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** 코치 피드백: 무엇이 문제였는지, 어떻게 고치면 되는지 */
@Composable
private fun CoachFeedback(feedback: List<String>, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("코치 피드백", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            feedback.forEach { Text("· $it") }
        }
    }
}

/**
 * 다시 보여주기 조작 바: 재생/일시정지, 배속, 위치 이동, 닫기.
 * 지금 시각까지 지난 마지막 사건(전환·충돌)을 위에 한 줄로 알려줌.
 */
@Composable
private fun ReplayBar(
    source: ParkingSimulator,
    plan: CoachPlan?,
    canSwitch: Boolean,
    time: Double,
    playing: Boolean,
    speed: Double,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onSpeedChange: (Double) -> Unit,
    onClose: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier,
) {
    val end = source.history.last().timeSec.coerceAtLeast(0.1)
    val lastEvent = source.events.lastOrNull { it.timeSec <= time }
    // 모범 주행이면 지금 단계 설명, 내 주행이면 지난 사건
    val caption = if (plan != null) "모범 주행 · " + (plan.stepAt(time)?.text ?: "") else when (lastEvent) {
        is SimEvent.DirectionChanged ->
            "${lastEvent.count}번째 전환: ${if (lastEvent.to == Direction.REVERSE) "후진" else "전진"}으로 바꿨어요"
        is SimEvent.Collided -> "여기서 ${lastEvent.message}"
        null -> "처음부터 다시 보는 중"
    }
    val textSize = 20.sp * LocalAppTextScale.current

    Surface(color = CameraOverlay, shape = RoundedCornerShape(20.dp), modifier = modifier.widthIn(max = 900.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                caption,
                color = when {
                    plan != null -> Color(0xFF8EE6B1)
                    lastEvent is SimEvent.Collided -> Color(0xFFFF8A80)
                    else -> Color.White
                },
                fontSize = textSize, fontWeight = FontWeight.Bold,
            )
            // 모범 주행이 유일한 정답처럼 보이지 않도록 안내
            if (plan != null) {
                Text(
                    "모범 주행은 표준 방법으로 깔끔하게 넣은 예시 하나예요. 유일한 최적 경로는 아니고, 다른 방법으로도 주차할 수 있어요.",
                    color = CameraSecondaryText,
                    fontSize = textSize * 0.75f,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlayPause, modifier = Modifier.height(56.dp)) {
                    Text(if (playing) "일시정지" else "재생", fontSize = textSize)
                }
                OutlinedButton(onClick = { onSpeedChange(if (speed == 1.0) 2.0 else 1.0) }, modifier = Modifier.height(56.dp)) {
                    Text(if (speed == 1.0) "1배속" else "2배속", color = Color.White, fontSize = textSize)
                }
                Slider(
                    value = time.toFloat(),
                    onValueChange = { onSeek(it.toDouble()) },
                    valueRange = 0f..end.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text("${clock(time)} / ${clock(end)}", color = Color.White, fontSize = textSize)
                if (canSwitch) {
                    OutlinedButton(onClick = onSwitch, modifier = Modifier.height(56.dp)) {
                        Text(if (plan != null) "내 주행 보기" else "모범 주행 보기", color = Color.White, fontSize = textSize, maxLines = 1, softWrap = false)
                    }
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.height(56.dp)) {
                    Text("닫기", color = Color.White, fontSize = textSize)
                }
            }
        }
    }
}

/** 초 → "0:12" */
private fun clock(sec: Double): String {
    val s = sec.toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** 오른쪽 조작 패널: 입력 방식, 핸들, 전진/후진, 브레이크. 차종은 홈에서 정한다. */
@Composable
private fun ControlPanel(
    car: CarSpec,
    mode: InputMode,
    vehicleAvailable: Boolean,
    onModeChange: (InputMode) -> Unit,
    input: DrivingInput,
    touchInput: TouchInput,
    vehicleInput: VehicleInput?,
    onRestart: () -> Unit,
    modifier: Modifier,
) {
    val isVehicle = mode == InputMode.VEHICLE && vehicleInput != null
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("입력", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { onModeChange(m) },
                        enabled = m == InputMode.TOUCH || vehicleAvailable,
                        label = { Text(m.label) },
                    )
                }
            }

            Text("핸들", fontWeight = FontWeight.Bold)
            val wheel = (input.steeringDeg / car.steeringRatio)
                .coerceIn(-car.maxWheelAngleDeg, car.maxWheelAngleDeg)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SteeringWheelIcon(input.steeringDeg, Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    if (isVehicle && vehicleInput != null) {
                        // SDK 원래 값과, 해석 설정을 거친 핸들 각도를 같이 보여줌
                        Text("SDK 값 ${fmt(vehicleInput.rawSteering, 0)}°")
                        Text("→ 핸들 ${fmt(input.steeringDeg, 0)}° / 바퀴 ${fmt(wheel)}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("핸들 ${fmt(input.steeringDeg, 0)}°")
                        Text("→ 바퀴 ${fmt(wheel)}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!isVehicle) {
                Slider(
                    value = touchInput.steeringDeg.toFloat(),
                    onValueChange = { touchInput.steeringDeg = it.toDouble() },
                    valueRange = -car.maxSteeringWheelDeg.toFloat()..car.maxSteeringWheelDeg.toFloat(),
                )
                OutlinedButton(onClick = { touchInput.steeringDeg = 0.0 }, Modifier.fillMaxWidth()) {
                    Text("핸들 가운데로")
                }
            }

            Text("방향", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Direction.entries.forEach { d ->
                    FilterChip(
                        selected = input.direction == d,
                        onClick = { input.direction = d },
                        label = { Text(if (d == Direction.FORWARD) "전진 (D)" else "후진 (R)") },
                    )
                }
            }

            if (isVehicle && vehicleInput != null) {
                VehicleStatus(vehicleInput)
            } else {
                BrakeHoldButton(touchInput)
            }

            OutlinedButton(onClick = onRestart, Modifier.fillMaxWidth()) { Text("처음부터 다시") }

            Text(
                if (isVehicle) "실제 브레이크를 떼면 진행, 밟으면 정지해요."
                else "키보드: ←→ 핸들, ↑ 전진, ↓ 후진, Space 진행, R 다시",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 화면 조작: 누르고 있는 동안만 브레이크를 뗌 → 크리프로 천천히 진행 */
@Composable
private fun BrakeHoldButton(input: TouchInput) {
    val going = !input.brakePressed
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                if (going) colors.primaryContainer else colors.primary,
                RoundedCornerShape(16.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    input.brakePressed = false
                    tryAwaitRelease()
                    input.brakePressed = true
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (going) "진행 중 (손 떼면 정지)" else "누르는 동안 브레이크 떼기",
            color = if (going) colors.onPrimaryContainer else colors.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 실제 차량: SDK에서 받은 기어·브레이크 상태 표시 */
@Composable
private fun VehicleStatus(input: VehicleInput) {
    val gearText = when (input.gear) {
        null -> "확인 중"
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        else -> input.gear.toString()
    }
    val brakeText = when (input.pedalPressed) {
        null -> "확인 중"
        true -> "밟음 (정지)"
        false -> "뗌 (진행)"
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text("기어: $gearText", fontWeight = FontWeight.Bold)
            Text("브레이크: $brakeText")
            if (!input.armed) Text("브레이크를 한 번 밟으면 시작", color = MaterialTheme.colorScheme.primary)
            // SDK 조향값 해석 설정 (기본값은 에뮬레이터에서 확인한 값. 실제 차에서 다르면 여기서 바꿈)
            Spacer(Modifier.height(8.dp))
            Text("조향값 해석", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SteeringUnit.entries.forEach { u ->
                    FilterChip(
                        selected = input.steeringUnit == u,
                        onClick = { input.steeringUnit = u },
                        label = { Text(u.label) },
                    )
                }
            }
            FilterChip(
                selected = input.invertSteering,
                onClick = { input.invertSteering = !input.invertSteering },
                label = { Text("좌우 반전") },
            )
        }
    }
}

/** 핸들 모양 아이콘: 핸들 각도만큼 돌아감 */
@Composable
private fun SteeringWheelIcon(angleDeg: Double, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        rotate(angleDeg.toFloat()) {
            val r = size.minDimension / 2 - 4f
            drawCircle(color, radius = r, style = Stroke(width = 8f))
            drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 8f)
            drawLine(color, center, Offset(center.x, center.y + r), strokeWidth = 8f)
            drawCircle(Color(0xFFE53935), radius = 7f, center = Offset(center.x, center.y - r))  // 12시 표시
        }
    }
}

private const val STEER_KEY_STEP = 45.0

private fun TouchInput.steerBy(delta: Double, car: CarSpec) {
    steeringDeg = (steeringDeg + delta).coerceIn(-car.maxSteeringWheelDeg, car.maxSteeringWheelDeg)
}

private fun fmt(v: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)
