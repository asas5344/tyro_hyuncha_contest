package com.team.hyuncha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.hyuncha.sim.ParkingScore
import com.team.hyuncha.sim.Scoring

/** 등급별 색: S 금색, A 초록, B 파랑, C 주황, D 빨강 */
internal fun gradeColor(grade: String) = when (grade) {
    "S" -> Color(0xFFFFC83D)
    "A" -> Color(0xFF4CD08A)
    "B" -> Color(0xFF4FA3F7)
    "C" -> Color(0xFFFF9F43)
    else -> Color(0xFFFF6B6B)
}

/** 총점과 등급 배지 */
@Composable
fun ScoreHeader(score: ParkingScore) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(64.dp).background(gradeColor(score.grade), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(score.grade, color = Color.Black, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${score.total}", fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("score_total"))
            Text(" / ${Scoring.MAX_TOTAL}점", fontSize = 20.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

/** 항목별 점수: 이름 · 막대 · 점수, 아래에 무엇을 보고 매겼는지 */
@Composable
fun ScoreBreakdown(score: ParkingScore, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("항목별 점수", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        score.items.forEach { item ->
            val ratio = item.points.toFloat() / item.max
            val barColor = when {
                ratio >= 0.9f -> Color(0xFF4CD08A)
                ratio >= 0.6f -> Color(0xFFFFC83D)
                else -> Color(0xFFFF6B6B)
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, modifier = Modifier.width(130.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        modifier = Modifier.weight(1f).height(10.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Text("${item.points}/${item.max}", modifier = Modifier.width(120.dp).padding(start = 12.dp), fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false)
                }
                Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 130.dp))
            }
        }
    }
}
