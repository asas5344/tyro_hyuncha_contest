package com.team.hyuncha.ui

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import com.team.hyuncha.input.TouchInput
import com.team.hyuncha.input.SteeringUnit
import com.team.hyuncha.input.VehicleInput
import com.team.hyuncha.safety.LockState
import com.team.hyuncha.safety.SafetyLock
import com.team.hyuncha.sim.ParkingMaps
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimStatus
import java.util.Locale
import com.team.hyuncha.ui.theme.LocalAppTextScale

/** 입력 방식: 화면 조작(개발·백업용) / 실제 차량(Pleos SDK) */
enum class InputMode(val label: String) {
    TOUCH("화면 조작"),
    VEHICLE("실제 차량"),
}

/**
 * 주차 연습 화면: 탑뷰 전체 화면 + 아래쪽 작은 전진/후진 버튼.
 * 실제 차량(Virtual Console) 입력을 기본으로 쓰고, 오른쪽 조작 패널은 차량 연결이 없을 때(화면 조작 백업)만 보임.
 * @param vehicle Pleos SDK. null이면 화면 조작만 가능
 */
@Composable
fun ParkingScreen(
    vehicle: Vehicle?, modifier: Modifier = Modifier,
    initialCar: CarSpec = CarPresets.GRANDEUR,
    parkingMode: ParkingMode = ParkingMode.PERPENDICULAR,
) {
    val car = initialCar
    val currentCar by rememberUpdatedState(car)
    var mode by remember { mutableStateOf(if (vehicle != null) InputMode.VEHICLE else InputMode.TOUCH) }
    val touchInput = remember { TouchInput() }
    val vehicleInput = remember(vehicle) {
        vehicle?.let { VehicleInput(it) { currentCar.steeringRatio } }
    }
    // 다시 할 때마다 round가 바뀌어 목표 칸·주차된 차가 새로 배치됨
    var round by remember { mutableIntStateOf(0) }
    val sim = remember(car, round, parkingMode) { ParkingSimulator(car, ParkingMaps.random(parkingMode)) }
    val frame = remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }

    val input: DrivingInput = if (mode == InputMode.VEHICLE && vehicleInput != null) vehicleInput else touchInput
    val lock = if (input === vehicleInput) SafetyLock.check(vehicleInput?.gear) else LockState.OK
    val currentInput by rememberUpdatedState(input)
    val currentLock by rememberUpdatedState(lock)

    // 실제 차량 모드일 때만 SDK 리스너 등록
    DisposableEffect(vehicleInput, mode) {
        if (mode == InputMode.VEHICLE) vehicleInput?.start()
        onDispose { if (mode == InputMode.VEHICLE) vehicleInput?.stop() }
    }

    // 게임 루프: 화면이 그려질 때마다(약 60fps) 시뮬레이터를 한 번씩 진행
    LaunchedEffect(sim) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // 프레임이 밀려도 한 번에 너무 멀리 가지 않도록 최대 50ms로 제한
                val dt = ((now - last) / 1e9).coerceAtMost(0.05)
                last = now
                // 안전 잠금이 풀려 있으면 시뮬레이터를 멈춤
                if (currentLock == LockState.OK) sim.step(currentInput, dt)
                frame.longValue++
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun restart() {
        round++
        touchInput.reset()
        vehicleInput?.reset()
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
                ParkingCanvas(
                    sim = sim,
                    frame = { frame.longValue },
                    direction = { input.direction },
                    braking = { input.brakePressed },
                    modifier = Modifier.fillMaxSize(),
                )
                SimHud(
                    sim = sim,
                    frame = { frame.longValue },
                    lock = lock,
                    waitingForBrake = input === vehicleInput && vehicleInput?.armed == false,
                    vehicleStatus = if (input === vehicleInput) vehicleInput?.statusText() else null,
                    onRestart = ::restart,
                    modifier = Modifier.fillMaxSize(),
                )
                // 실제 차량 모드: 메뉴 대신 전진/후진과 다시 하기만 작게 띄움 (기어는 P 고정이라 방향은 앱에서 고름)
                if (input === vehicleInput) {
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
            // 화면 조작 백업 모드에서만 오른쪽 조작 패널을 보여줌
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
    }
}

/** 왼쪽 위 상태 표시 + 안내·결과 카드. frame을 읽어서 이 부분만 매 프레임 갱신됨 */
@Composable
private fun SimHud(
    sim: ParkingSimulator,
    frame: () -> Long,
    lock: LockState,
    waitingForBrake: Boolean,
    vehicleStatus: String?,
    onRestart: () -> Unit,
    modifier: Modifier,
) {
    frame()
    Box(modifier.padding(16.dp)) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Column(Modifier.padding(12.dp)) {
                HudText("${sim.map.mode.label} · ${sim.car.name}", bold = true)
                vehicleStatus?.let { HudText(it) }
                HudText("속도 ${fmt(kotlin.math.abs(sim.speed) * 3.6)} km/h")
                HudText("바퀴 각도 ${fmt(sim.wheelAngleDeg)}°")
                HudText("전후진 전환 ${sim.directionChanges}회")
                HudText("칸 안: ${if (sim.isInsideSlot) "O" else "X"} · 각도 오차 ${fmt(sim.angleErrorDeg)}°")
            }
        }

        when {
            lock.message != null -> NoticeCard(lock.message, Modifier.align(Alignment.Center))
            sim.status == SimStatus.COLLIDED -> ResultCard(
                title = "부딪혔어요",
                lines = listOf("천천히, 핸들을 조금 더 일찍 꺾어보세요."),
                onRestart = onRestart,
                modifier = Modifier.align(Alignment.Center),
            )
            sim.status == SimStatus.PARKED -> sim.result?.let { r ->
                ResultCard(
                    title = "주차 성공!",
                    lines = listOf(
                        "각도 오차 ${fmt(r.angleErrorDeg)}°",
                        "칸 중앙에서 ${fmt(r.centerOffsetM * 100, 0)}cm",
                        "전후진 전환 ${r.directionChanges}회",
                        "걸린 시간 ${fmt(r.elapsedSec, 0)}초",
                    ),
                    onRestart = onRestart,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            waitingForBrake -> NoticeCard(
                "브레이크를 한 번 밟으면 시작해요.\n브레이크를 떼면 천천히 움직여요.",
                Modifier.align(Alignment.BottomCenter),
            )
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
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Direction.entries.forEach { d ->
                val selected = direction == d
                Button(
                    onClick = { onDirectionChange(d) },
                    colors = if (selected) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                    modifier = Modifier.height(64.dp),
                ) {
                    Text(if (d == Direction.FORWARD) "전진 (D)" else "후진 (R)", fontSize = 22.sp * LocalAppTextScale.current)
                }
            }
            OutlinedButton(onClick = onRestart, modifier = Modifier.height(64.dp)) {
                Text("다시", color = Color.White, fontSize = 22.sp * LocalAppTextScale.current)
            }
        }
    }
}

/** HUD에 한 줄로 보여줄 기어·브레이크 상태 */
private fun VehicleInput.statusText(): String {
    val gearText = when (gear) {
        null -> "확인 중"
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        else -> gear.toString()
    }
    val brakeText = when (pedalPressed) {
        null -> "확인 중"
        true -> "밟음"
        false -> "뗌"
    }
    return "기어 $gearText · 브레이크 $brakeText"
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
private fun NoticeCard(text: String, modifier: Modifier) {
    Card(modifier) {
        Text(
            text,
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultCard(title: String, lines: List<String>, onRestart: () -> Unit, modifier: Modifier) {
    Card(modifier.width(320.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            lines.forEach { Text(it) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRestart) { Text("다시 하기") }
        }
    }
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
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                if (going) Color(0xFF2E7D32) else Color(0xFFC62828),
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
            color = Color.White,
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
            input.lastError?.let { Text("오류 - $it", color = MaterialTheme.colorScheme.error) }
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
