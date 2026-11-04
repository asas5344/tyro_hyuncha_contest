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

@Composable
fun SettingsScreen(textSize: AppTextSize, dark: Boolean,
                   onTextSizeChange: (AppTextSize) -> Unit,
                   onDarkThemeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().testTag("settings_screen").verticalScroll(rememberScrollState()).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AppButton("홈으로", onBack, Modifier.testTag("settings_back"), icon = "back")
            Text("설정", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(8.dp))
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
