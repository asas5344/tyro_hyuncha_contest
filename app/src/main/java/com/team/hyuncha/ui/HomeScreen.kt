package com.team.hyuncha.ui

import com.team.hyuncha.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    darkTheme: Boolean = true,
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
                    AppLogo(colors.onSurface, Modifier.padding(16.dp, 4.dp))
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().testTag("home_content")) {
                val compactHeader = maxWidth < 850.dp
                // 넓고 여유 있는 홈에서만 버튼을 더 키워 큰 글자에서 하단 조작이 잘리지 않게 한다.
                val buttonGrowth = if (compactHeader) 0f else
                    ((maxHeight - 1100.dp) / 110.dp).coerceIn(0f, 1f) *
                        ((1.4f - scale) / 0.4f).coerceIn(0f, 1f)
                val maxDiameter = 276.dp + 40.dp * buttonGrowth
                val diameter = minOf(controlHeight(if (maxHeight < 850.dp) 212.dp else maxDiameter), maxDiameter)
                val parkingButtonHeight = controlHeight(104.dp + 32.dp * buttonGrowth)
                val contentPadding = 24.dp
                val headerAreaHeight = ((maxHeight - diameter) / 2 - contentPadding).coerceAtLeast(0.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(if (wide) 44.dp else 24.dp, contentPadding)) {
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
                            Text("P단을 유지하고, 전진·후진은 앱에서 선택하세요.", Modifier.padding(top = 12.dp),
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val carWidth = if (scale > 1.2f) 460.dp else 420.dp
                    // 헤더가 더 많은 공간을 필요로 하는 작은 화면에서는 본문을 스크롤한다.
                    val headerArea = Modifier.fillMaxWidth().heightIn(min = headerAreaHeight)
                    if (compactHeader) {
                        Column(headerArea, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            heading(Modifier.fillMaxWidth())
                            SelectedCar(car, onChooseCar, Modifier.width(carWidth))
                        }
                    } else {
                        Row(headerArea, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            heading(Modifier.weight(1f))
                            SelectedCar(car, onChooseCar, Modifier.width(carWidth))
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EngineStartButton(onStart, Modifier.size(diameter).testTag("start_practice"))
                    }
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.weight(1f))
                    Text("연습할 주차 방식", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    // T자(직각)·평행·사선 주차 모두 선택 가능하다.
                    val perpendicular: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.PERPENDICULAR.label, mode == ParkingMode.PERPENDICULAR, true,
                            { onModeChange(ParkingMode.PERPENDICULAR) }, m.heightIn(min = parkingButtonHeight).testTag("mode_PERPENDICULAR"))
                    }
                    val parallel: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.PARALLEL.label, mode == ParkingMode.PARALLEL, true,
                            { onModeChange(ParkingMode.PARALLEL) }, m.heightIn(min = parkingButtonHeight).testTag("mode_PARALLEL"))
                    }
                    val diagonal: @Composable (Modifier) -> Unit = { m ->
                        ParkingChoice(ParkingMode.DIAGONAL.label, mode == ParkingMode.DIAGONAL, true,
                            { onModeChange(ParkingMode.DIAGONAL) }, m.heightIn(min = parkingButtonHeight).testTag("mode_DIAGONAL"))
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
                    Spacer(Modifier.height(8.dp))
                    HardModeChoice(hardMode, onHardModeChange, hardSituation, onHardSituationChange, darkTheme, buttonGrowth)
                    if (!hardMode) {
                        // 접혀도 펼쳐질 높이를 카드 아래에 남겨 주차 방식과 카드 상단을 고정한다.
                        Layout(
                            content = { HardSituationPicker(hardSituation, onHardSituationChange, buttonGrowth) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = HardModeHorizontalPadding),
                        ) { measurables, constraints ->
                            val picker = measurables.single().measure(constraints)
                            layout(picker.width, picker.height) {}
                        }
                    }
                }
            }
        }
    }
}

private val HardModeHorizontalPadding = 24.dp

/**
 * 하드 모드 (T자 주차만): 제한시간 40초 · 8km/h · 클락션 · 좁은 칸 + 랜덤 상황.
 * 켜면 상황을 고르는 버튼이 나옴 — 확인용이라 나중에 뺄 예정 (HardSituationPicker만 지우면 됨)
 */
@Composable
private fun HardModeChoice(
    on: Boolean, onChange: (Boolean) -> Unit,
    situation: HardSituation?, onSituationChange: (HardSituation?) -> Unit,
    darkTheme: Boolean, buttonGrowth: Float,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().testTag("hard_mode")
        .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        shape = RoundedCornerShape(18.dp), color = if (on) c.surfaceVariant else c.surface) {
        Column(Modifier.padding(horizontal = HardModeHorizontalPadding, vertical = 12.dp)) {
            Row(Modifier.heightIn(min = 52.dp + 10.dp * buttonGrowth), verticalAlignment = Alignment.CenterVertically) {
                Text("하드 모드", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = c.onSurface)
                Text(if (on) "켜짐 · T자 주차" else "꺼짐", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = on, onCheckedChange = null,
                    modifier = Modifier.testTag("hard_mode_switch").graphicsLayer {
                        scaleX = 1.15f + 0.1f * buttonGrowth
                        scaleY = scaleX
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = if (darkTheme) Color(0xFF00E074) else Color(0xFF02C265),
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = if (darkTheme) Color(0xFF686A72) else Color(0xFFB8B8B9),
                        uncheckedThumbColor = if (darkTheme) Color(0xFFA4A8AE) else Color.White,
                        uncheckedBorderColor = c.outline,
                    ),
                )
            }
            Text("T자 주차 · 40초 · 8km/h · 클랙슨 3번 · 좁은 칸 · 랜덤 상황",
                Modifier.padding(top = 6.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (on) HardSituationPicker(situation, onSituationChange, buttonGrowth)
        }
    }
}

/** 확인용: 하드 모드 상황 직접 고르기 (나중에 뺄 예정) */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HardSituationPicker(situation: HardSituation?, onChange: (HardSituation?) -> Unit, buttonGrowth: Float) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("상황 고르기 (확인용)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<HardSituation?>(null) + HardSituation.entries).forEach { s ->
                FilterChip(
                    selected = situation == s,
                    onClick = { onChange(s) },
                    label = { Text(s?.label ?: "랜덤") },
                    modifier = Modifier.heightIn(min = 48.dp + 10.dp * buttonGrowth)
                        .widthIn(min = 116.dp * buttonGrowth)
                        .testTag("hard_pick_${s?.name ?: "RANDOM"}"),
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
    Box(modifier.graphicsLayer { scaleX = factor; scaleY = factor }.padding(10.dp)) {
        Button(onClick = onStart, modifier = Modifier.fillMaxSize(), shape = CircleShape,
            interactionSource = interaction,
            contentPadding = PaddingValues(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineIcon("power", Modifier.size(56.dp), c.onPrimary)
                Text("연습 시작", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("START", color = c.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParkingChoice(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) c.surfaceVariant else c.surface,
        contentColor = if (selected) c.onSurface else c.onSurfaceVariant,
        border = if (selected) BorderStroke(2.dp, c.outline) else null) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

/**
 * 앱 로고: 주차 칸으로 들어가는 차 마크 + "tyro" 글자 (로고 그림에서 떼어 낸 것).
 * 그림은 흰색 + 투명도라 테마 글자색으로 칠해서 밝은·어두운 테마 모두에서 보이게 함
 */
@Composable
private fun AppLogo(color: Color, modifier: Modifier = Modifier) {
    Row(modifier.testTag("app_logo"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.tyro_mark), null, Modifier.size(64.dp), colorFilter = ColorFilter.tint(color))
        Image(painterResource(R.drawable.tyro_wordmark), "tyro", Modifier.height(42.dp), colorFilter = ColorFilter.tint(color))
    }
}
