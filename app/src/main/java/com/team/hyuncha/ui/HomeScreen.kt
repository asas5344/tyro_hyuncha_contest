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
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.ui.theme.LocalAppTextScale

@Composable
fun HomeScreen(
    car: CarSpec, onChooseCar: () -> Unit, onSettings: () -> Unit, onStart: () -> Unit,
    mode: ParkingMode = ParkingMode.PERPENDICULAR, onModeChange: (ParkingMode) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val scale = (LocalDensity.current.fontScale * LocalAppTextScale.current).coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_screen")) {
        val wide = maxWidth >= 1100.dp
        val viewportHeight = maxHeight
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
                    AppButton("설정", onSettings, Modifier.fillMaxWidth().testTag("open_settings"), icon = "settings")
                    Spacer(Modifier.height(32.dp))
                    Text("천천히,\n자신 있게.", Modifier.padding(16.dp), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val compactHeader = maxWidth < 850.dp
                val headerHeight = when {
                    compactHeader -> 360.dp * scale
                    scale > 1.2f -> 260.dp
                    else -> 210.dp
                }
                val diameter = controlHeight(300.dp)
                // 화면 첫 진입 시 원형 버튼 중심을 본문 영역의 세로 중앙에 맞춘다.
                // 큰 글자/낮은 창에서는 아래 주차 방식과 안내를 스크롤할 수 있다.
                val startAreaHeight = maxOf(diameter + 48.dp, viewportHeight - 64.dp - headerHeight * 2)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(if (wide) 44.dp else 24.dp, 32.dp)) {
                    Box(Modifier.fillMaxWidth().height(headerHeight)) {
                        Column(Modifier.align(Alignment.TopStart)
                            .fillMaxWidth(if (compactHeader) 1f else .55f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("주차 연습", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                                if (!wide) AppButton("설정", onSettings, Modifier.testTag("open_settings"), icon = "settings")
                            }
                            Text("내 차로, 천천히 익혀요.", Modifier.padding(top = 8.dp),
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                        SelectedCar(car, onChooseCar, Modifier.align(if (compactHeader) Alignment.BottomEnd else Alignment.TopEnd)
                            .width(if (scale > 1.2f) 460.dp else 420.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(startAreaHeight), contentAlignment = Alignment.Center) {
                        EngineStartButton(onStart, Modifier.size(diameter).testTag("start_practice"))
                    }
                    Text("연습할 주차 방식", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    // 실제 구현된 T자(직각)·평행 주차만 선택 가능하다.
                    val perpendicular: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice("T자 주차", mode == ParkingMode.PERPENDICULAR, true,
                            { onModeChange(ParkingMode.PERPENDICULAR) }, m.testTag("mode_PERPENDICULAR"))
                    }
                    val parallel: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice("평행 주차", mode == ParkingMode.PARALLEL, true,
                            { onModeChange(ParkingMode.PARALLEL) }, m.testTag("mode_PARALLEL"))
                    }
                    if (wide && scale <= 1.25f) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            perpendicular(Modifier.weight(1f))
                            parallel(Modifier.weight(1f))
                            ParkingChoice("사선 주차", false, false, {}, Modifier.weight(1f))
                        }
                    } else {
                        perpendicular(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        parallel(Modifier.fillMaxWidth())
                        Text("사선 주차는 준비 중이에요.", Modifier.padding(top = 12.dp),
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("P단을 유지하고, 전진·후진은 앱에서 선택하세요.", Modifier.padding(top = 18.dp),
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
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
