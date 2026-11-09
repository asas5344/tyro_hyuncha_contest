package com.team.hyuncha.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimStatus
import com.team.hyuncha.ui.theme.LocalAppTextScale
import kotlin.math.ceil

/**
 * 하드 모드 위쪽 표시: 남은 시간(10초 이하 빨강), 이번 상황.
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
    Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(18.dp()), modifier = modifier.testTag("hard_bar")) {
        Column(Modifier.padding(horizontal = 22.dp(), vertical = 10.dp()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "남은 시간 ${ceil(left).toInt()}초",
                color = if (urgent) Color(0xFFFF6B6B) else Color.White,
                fontSize = 28.sp * scale, fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("hard_time"),
            )
            rules.situation?.let { s ->
                Text("하드 모드 · ${s.label}", color = Color(0xFFFFD54F), fontSize = 16.sp * scale, fontWeight = FontWeight.Bold)
                if (!started) {
                    Text(s.description, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp * scale)
                    Text("움직이기 시작하면 ${rules.timeLimitSec?.toInt()}초가 흘러요 · 속도 8km/h · 클락션 주의",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp * scale)
                }
            }
        }
    }
}

/** 클락션이 울릴 때 화면 가운데 크게 */
@Composable
fun HornFlash(modifier: Modifier = Modifier) {
    Text("빵빵!", color = Color(0xFFFF5252), fontSize = 72.sp, fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.displayLarge, modifier = modifier.testTag("horn_flash"))
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
