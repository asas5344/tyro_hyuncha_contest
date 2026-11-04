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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.ui.theme.LocalAppTextScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarSelectionSheet(selected: CarSpec, onSelect: (CarSpec) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 1320.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 1000.dp && LocalDensity.current.fontScale * LocalAppTextScale.current <= 1.25f) 4 else 2
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("연습할 내 차", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    AppButton("닫기", onDismiss, Modifier.testTag("close_car_picker"))
                }
                CarPresets.all.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { car ->
                            Surface(onClick = { onSelect(car) }, shape = RoundedCornerShape(24.dp),
                                color = if (car == selected) c.surfaceVariant else c.background,
                                contentColor = c.onSurface,
                                border = if (car == selected) BorderStroke(2.dp, c.primary) else null,
                                modifier = Modifier.weight(1f).testTag("select_car_${car.name}")) {
                                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CarArtwork(car, Modifier.fillMaxWidth().height(150.dp))
                                    Text(car.name, style = MaterialTheme.typography.titleLarge)
                                    Text(car.type.label, Modifier.padding(top = 6.dp),
                                        color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
