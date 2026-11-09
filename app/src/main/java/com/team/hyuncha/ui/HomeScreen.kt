package com.team.hyuncha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.sim.HardSituation
import com.team.hyuncha.sim.ParkingMode
import androidx.compose.ui.graphics.Color
import com.team.hyuncha.ui.theme.LocalAppTextScale

@Composable
fun HomeScreen(
    car: CarSpec, onChooseCar: () -> Unit, onSettings: () -> Unit, onStart: () -> Unit,
    mode: ParkingMode = ParkingMode.PERPENDICULAR, onModeChange: (ParkingMode) -> Unit = {},
    onRanking: () -> Unit = {},
    hardMode: Boolean = false, onHardModeChange: (Boolean) -> Unit = {},
    hardSituation: HardSituation? = null, onHardSituationChange: (HardSituation?) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val scale = (LocalDensity.current.fontScale * LocalAppTextScale.current).coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_screen")) {
        val wide = maxWidth >= 1100.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(Modifier.width(if (scale > 1.2f) 300.dp else 264.dp).fillMaxHeight()
                    .background(colors.surface).verticalScroll(rememberScrollState()).padding(20.dp, 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Driving\nMate", Modifier.padding(16.dp, 4.dp),
                        style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().heightIn(min = controlHeight(100.dp)).padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlineIcon("parking", Modifier.size(32.dp)); Spacer(Modifier.width(14.dp))
                            Text("주차 연습", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    AppButton("내 차", onChooseCar, Modifier.fillMaxWidth().testTag("choose_car_nav"), icon = "car")
                    AppButton("랭킹", onRanking, Modifier.fillMaxWidth().testTag("open_ranking"), icon = "trophy")
                    AppButton("설정", onSettings, Modifier.fillMaxWidth().testTag("open_settings"), icon = "settings")
                    Spacer(Modifier.height(32.dp))
                    Text("천천히,\n자신 있게.", Modifier.padding(16.dp), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val compactHeader = maxWidth < 850.dp
                val diameter = controlHeight(if (maxHeight < 850.dp) 200.dp else 260.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(if (wide) 44.dp else 24.dp, 24.dp)) {
                    val heading: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("주차 연습", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                                if (!wide) {
                                    AppButton("랭킹", onRanking, Modifier.testTag("open_ranking"), icon = "trophy")
                                    Spacer(Modifier.width(12.dp))
                                    AppButton("설정", onSettings, Modifier.testTag("open_settings"), icon = "settings")
                                }
                            }
                            Text("내 차로, 천천히 익혀요.", Modifier.padding(top = 8.dp),
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    val carWidth = if (scale > 1.2f) 460.dp else 420.dp
                    if (compactHeader) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            heading(Modifier.fillMaxWidth())
                            SelectedCar(car, onChooseCar, Modifier.width(carWidth))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            heading(Modifier.weight(1f))
                            SelectedCar(car, onChooseCar, Modifier.width(carWidth))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HardModeChoice(hardMode, onHardModeChange, hardSituation, onHardSituationChange)
                    Box(Modifier.fillMaxWidth().height(diameter + 28.dp), contentAlignment = Alignment.Center) {
                        EngineStartButton(onStart, Modifier.size(diameter).testTag("start_practice"))
                    }
                    Text("연습할 주차 방식", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    // T자(직각)·평행·사선 주차 모두 선택 가능하다.
                    val perpendicular: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.PERPENDICULAR.label, mode == ParkingMode.PERPENDICULAR, true,
                            { onModeChange(ParkingMode.PERPENDICULAR) }, m.testTag("mode_PERPENDICULAR"))
                    }
                    val parallel: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.PARALLEL.label, mode == ParkingMode.PARALLEL, true,
                            { onModeChange(ParkingMode.PARALLEL) }, m.testTag("mode_PARALLEL"))
                    }
                    val diagonal: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.DIAGONAL.label, mode == ParkingMode.DIAGONAL, true,
                            { onModeChange(ParkingMode.DIAGONAL) }, m.testTag("mode_DIAGONAL"))
                    }
                    if (!compactHeader) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            perpendicular(Modifier.weight(1f))
                            parallel(Modifier.weight(1f))
                            diagonal(Modifier.weight(1f))
                        }
                    } else {
                        perpendicular(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        parallel(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        diagonal(Modifier.fillMaxWidth())
                    }
                    Text("P단을 유지하고, 전진·후진은 앱에서 선택하세요.", Modifier.padding(top = 18.dp),
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * 하드 모드 (T자 주차만): 제한시간 40초 · 8km/h · 클락션 · 좁은 칸 + 랜덤 상황.
 * 켜면 상황을 고르는 버튼이 나옴 — 확인용이라 나중에 뺄 예정 (HardSituationPicker만 지우면 됨)
 */
@Composable
private fun HardModeChoice(
    on: Boolean, onChange: (Boolean) -> Unit,
    situation: HardSituation?, onSituationChange: (HardSituation?) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = { onChange(!on) }, modifier = Modifier.fillMaxWidth().testTag("hard_mode"),
        shape = RoundedCornerShape(24.dp), color = if (on) c.surfaceVariant else c.surface,
        border = if (on) BorderStroke(2.dp, Color(0xFFFF6B6B)) else null) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하드 모드", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color(0xFFFF6B6B) else c.onSurface)
                Text(if (on) "켜짐 · T자 주차" else "꺼짐", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("T자 주차 · 제한시간 40초 · 속도 8km/h · 클락션 3번 · 좁은 칸 + 랜덤 상황(기둥 옆 / 옆 차 침범 / 이중주차)",
                Modifier.padding(top = 6.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (on) HardSituationPicker(situation, onSituationChange)
        }
    }
}

/** 확인용: 하드 모드 상황 직접 고르기 (나중에 뺄 예정) */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HardSituationPicker(situation: HardSituation?, onChange: (HardSituation?) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text("상황 고르기 (확인용)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HardSituation?>(null) + HardSituation.entries).forEach { s ->
                FilterChip(
                    selected = situation == s,
                    onClick = { onChange(s) },
                    label = { Text(s?.label ?: "랜덤") },
                    modifier = Modifier.testTag("hard_pick_${s?.name ?: "RANDOM"}"),
                )
            }
        }
    }
}

@Composable
private fun SelectedCar(car: CarSpec, onChooseCar: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onChooseCar, modifier = modifier.testTag("choose_car"),
        shape = RoundedCornerShape(26.dp), color = c.surface, contentColor = c.onSurface) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("선택한 차", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(car.name, Modifier.padding(top = 6.dp).testTag("home_car_name"),
                    style = MaterialTheme.typography.headlineMedium)
                Text(car.type.label, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            CarArtwork(car, Modifier.width(152.dp).height(110.dp))
            OutlineIcon("chevron", Modifier.size(24.dp), c.onSurfaceVariant)
        }
    }
}

@Composable
private fun EngineStartButton(onStart: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val factor by animateFloatAsState(if (pressed) .965f else 1f,
        spring(dampingRatio = 1f, stiffness = 700f), label = "engine_start_press")
    Box(modifier.graphicsLayer { scaleX = factor; scaleY = factor }
        .border(3.dp, c.outlineVariant, CircleShape).padding(10.dp)) {
        Button(onClick = onStart, modifier = Modifier.fillMaxSize(), shape = CircleShape,
            border = BorderStroke(2.dp, c.primary.copy(alpha = .7f)),
            colors = ButtonDefaults.buttonColors(containerColor = c.surfaceVariant, contentColor = c.onSurface),
            interactionSource = interaction,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
            contentPadding = PaddingValues(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineIcon("power", Modifier.size(56.dp), c.primary)
                Text("연습 시작", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("START", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParkingChoice(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = controlHeight(120.dp)),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) c.surfaceVariant else c.surface,
        contentColor = if (selected) c.onSurface else c.onSurfaceVariant,
        border = if (selected) BorderStroke(2.dp, c.primary) else null) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                OutlineIcon("check", Modifier.size(28.dp)); Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(
                    when {
                        selected -> "선택됨"
                        enabled -> "눌러서 선택"
                        else -> "준비 중"
                    }, Modifier.padding(top = 6.dp),
                    color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
