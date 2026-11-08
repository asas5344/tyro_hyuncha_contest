package com.team.hyuncha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.team.hyuncha.data.AppTextSize
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.Ranking
import com.team.hyuncha.input.SteeringUnit

@Composable
fun SettingsScreen(textSize: AppTextSize, dark: Boolean,
                   onTextSizeChange: (AppTextSize) -> Unit,
                   onDarkThemeChange: (Boolean) -> Unit, onBack: () -> Unit,
                   steeringUnit: SteeringUnit = SteeringUnit.STEERING_WHEEL, invertSteering: Boolean = true,
                   onSteeringUnitChange: (SteeringUnit) -> Unit = {}, onInvertSteeringChange: (Boolean) -> Unit = {},
                   carCamera: CarCamera = CarCamera.REAR_ONLY, onCarCameraChange: (CarCamera) -> Unit = {},
                   driverName: String = "나", onDriverNameChange: (String) -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().testTag("settings_screen").verticalScroll(rememberScrollState()).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AppButton("홈으로", onBack, Modifier.testTag("settings_back"), icon = "back")
            Text("설정", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(8.dp))
        // 랭킹에 올라갈 이름. 한 차를 가족이 같이 쓰면 연습하는 사람 이름으로 바꿔 둠
        Text("운전자 이름", style = MaterialTheme.typography.headlineMedium)
        Text("주차에 성공하면 이 이름으로 랭킹에 기록돼요. 여러 사람이 같이 쓰면 연습하는 사람 이름으로 바꿔 주세요.",
            color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = driverName,
            onValueChange = { onDriverNameChange(it.replace("\n", "").take(Ranking.MAX_NAME_LENGTH)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().testTag("driver_name"),
        )
        Text("글자 크기", style = MaterialTheme.typography.headlineMedium)
        Text("선택한 크기는 바로 반영되고, 앱을 다시 켜도 유지돼요.",
            color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        AppTextSize.entries.forEach { size ->
            SettingChoice(size.label, size == textSize, Modifier.fillMaxWidth().testTag("font_${size.name}")) {
                onTextSizeChange(size)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = c.surface, contentColor = c.onSurface) {
            Column(Modifier.fillMaxWidth().padding(28.dp)) {
                Text("이 크기로 보여요", style = MaterialTheme.typography.titleLarge)
                Text("주차 연습을 시작해볼까요?", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("화면 테마", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingChoice("어둡게", dark, Modifier.weight(1f).testTag("theme_dark")) { onDarkThemeChange(true) }
            SettingChoice("밝게", !dark, Modifier.weight(1f).testTag("theme_light")) { onDarkThemeChange(false) }
        }

        // 내 차에 달린 카메라: 운전석 시점에서 후진할 때 차량 화면에 무엇이 뜨는지
        Text("내 차 카메라", style = MaterialTheme.typography.headlineMedium)
        Text("운전석 시점에서 후진하면, 내 차와 같은 카메라 화면이 떠요.",
            color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CarCamera.entries.forEach { cam ->
                SettingChoice(cam.label, cam == carCamera, Modifier.weight(1f).testTag("camera_${cam.name}")) { onCarCameraChange(cam) }
            }
        }

        // 실차 점검용: SDK 조향값을 어떻게 읽을지. 에뮬레이터 기준 기본값이 실제 차와 다를 때만 바꿈
        Spacer(Modifier.height(8.dp))
        Text("실제 차량 조향값 (실차 점검용)", style = MaterialTheme.typography.headlineMedium)
        Text("기본값은 에뮬레이터에서 확인한 값이에요. 실제 차에서 핸들을 오른쪽으로 돌렸는데 화면 속 바퀴가 " +
            "왼쪽으로 꺾이면 '좌우 반전'을, 조금만 돌려도 바퀴가 끝까지 꺾이면 '앞바퀴 각도'를 바꿔 보세요.",
            color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Text("SDK 값의 단위", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SteeringUnit.entries.forEach { unit ->
                SettingChoice(unit.label, unit == steeringUnit, Modifier.weight(1f).testTag("steer_unit_${unit.name}")) {
                    onSteeringUnitChange(unit)
                }
            }
        }
        Text("좌우 방향", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingChoice("좌우 반전 (에뮬레이터 기본)", invertSteering, Modifier.weight(1f).testTag("steer_invert_on")) { onInvertSteeringChange(true) }
            SettingChoice("그대로", !invertSteering, Modifier.weight(1f).testTag("steer_invert_off")) { onInvertSteeringChange(false) }
        }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onSelect, modifier = modifier.heightIn(min = controlHeight(92.dp)).semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp), color = if (selected) c.surfaceVariant else c.surface,
        contentColor = c.onSurface, border = if (selected) BorderStroke(2.dp, c.primary) else null) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (selected) OutlineIcon("check", Modifier.size(32.dp), c.primary)
        }
    }
}
