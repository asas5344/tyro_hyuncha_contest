package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimStatus
import com.team.hyuncha.ui.theme.CameraOverlay
import com.team.hyuncha.ui.theme.CameraSecondaryText
import com.team.hyuncha.ui.theme.LocalAppTextScale
import com.team.hyuncha.ui.theme.PracticeAmber
import kotlin.math.ceil

/**
 * 하드 모드 위쪽 표시: 남은 시간(10초 이하 주의색), 이번 상황.
 * 출발 전(아직 안 움직임)에는 상황 설명과 "움직이면 시간이 흘러요"를 같이 보여줌.
 */
@Composable
fun HardModeBar(sim: ParkingSimulator, frame: () -> Long, modifier: Modifier = Modifier) {
    frame()   // 매 프레임 다시 그려서 남은 시간이 줄어들게
    val rules = sim.map.rules
    val left = sim.timeLeftSec ?: return
    val scale = LocalAppTextScale.current
    val started = sim.startedAtSec != null
    val urgent = left <= 10.0 && sim.status == SimStatus.DRIVING
    Surface(color = CameraOverlay, shape = RoundedCornerShape(18.dp), modifier = modifier.testTag("hard_bar")) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "남은 시간 ${ceil(left).toInt()}초",
                color = if (urgent) PracticeAmber else Color.White,
                fontSize = 28.sp * scale, fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("hard_time"),
            )
            rules.situation?.let { s ->
                Text("하드 모드 · ${s.label}", color = Color.White, fontSize = 16.sp * scale, fontWeight = FontWeight.Bold)
                if (!started) {
                    Text(s.description, color = CameraSecondaryText, fontSize = 15.sp * scale)
                    Text("움직이기 시작하면 ${rules.timeLimitSec?.toInt()}초가 흘러요 · 속도 8km/h · 클랙슨 주의",
                        color = CameraSecondaryText, fontSize = 13.sp * scale)
                }
            }
        }
    }
}

/** 클락션이 울리는 동안 주행 시야와 버튼을 가리지 않는 가장자리 강조. */
@Composable
internal fun HornFlash(strength: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("horn_flash")) {
        val band = minOf(48.dp.toPx(), size.minDimension / 4)
        val glow = Color(0xFFFFAA45).copy(alpha = strength * 0.68f)
        val fade = listOf(glow, glow.copy(alpha = 0f))
        val horizontalBand = Size(size.width, band)
        val verticalBand = Size(band, size.height)
        drawRect(Brush.verticalGradient(fade, endY = band), size = horizontalBand)
        drawRect(Brush.verticalGradient(fade.asReversed(), startY = size.height - band, endY = size.height),
            topLeft = Offset(0f, size.height - band), size = horizontalBand)
        drawRect(Brush.horizontalGradient(fade, endX = band), size = verticalBand)
        drawRect(Brush.horizontalGradient(fade.asReversed(), startX = size.width - band, endX = size.width),
            topLeft = Offset(size.width - band, 0f), size = verticalBand)
        drawRect(Color(0xFFFFD19A).copy(alpha = strength), style = Stroke(width = 6.dp.toPx()))
    }
}
