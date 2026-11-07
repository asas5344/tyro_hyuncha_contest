package com.team.hyuncha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.team.hyuncha.data.Ranking
import com.team.hyuncha.data.ScoreRecord
import com.team.hyuncha.data.ScoreRecords
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.Scoring
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHOW_TOP = 50

/**
 * 랭킹 화면: 이 차에서 주차에 성공한 기록을 주차 방식별로 점수 높은 순서대로 보여줌.
 * 같은 점수면 먼저 기록한 사람이 위.
 */
@Composable
fun RankingScreen(records: ScoreRecords, initialMode: ParkingMode, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var modeName by rememberSaveable { mutableStateOf(initialMode.name) }
    val mode = ParkingMode.valueOf(modeName)
    var version by remember { mutableIntStateOf(0) }          // 기록을 지우면 다시 읽기
    val list = remember(mode, version) { Ranking.sorted(records.all(), mode).take(SHOW_TOP) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().testTag("ranking_screen").padding(40.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AppButton("홈으로", onBack, Modifier.testTag("ranking_back"), icon = "back")
            Text("랭킹", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("ranking_clear")) {
                Text("기록 모두 지우기")
            }
        }
        Text("이 차에서 주차에 성공한 기록이에요. 주차 방식마다 따로 순위를 매기고, 같은 점수면 먼저 기록한 사람이 위예요. " +
            "(${Scoring.MAX_TOTAL}점 만점)", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)

        // 주차 방식 고르기
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ParkingMode.entries.forEach { m ->
                val selected = m == mode
                Surface(
                    onClick = { modeName = m.name },
                    modifier = Modifier.weight(1f).heightIn(min = controlHeight(72.dp)).testTag("ranking_mode_${m.name}"),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) c.surfaceVariant else c.surface,
                    border = if (selected) BorderStroke(2.dp, c.primary) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(m.label, style = MaterialTheme.typography.titleLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("아직 ${mode.label} 기록이 없어요.\n주차에 성공하면 점수가 여기에 올라와요.",
                    color = c.onSurfaceVariant, style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.testTag("ranking_empty"))
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(list) { i, r -> RankRow(i + 1, r) }
            }
        }
    }

    // 모든 기록 삭제는 되돌릴 수 없어서 한 번 더 물어봄
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("랭킹 기록을 모두 지울까요?") },
            text = { Text("T자·평행·사선 주차의 모든 기록이 지워지고, 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { records.clear(); version++; confirmClear = false }, modifier = Modifier.testTag("ranking_clear_confirm")) {
                    Text("지우기", color = c.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
}

/** 1~3위는 금·은·동 */
private fun medalColor(rank: Int) = when (rank) {
    1 -> Color(0xFFFFC83D)
    2 -> Color(0xFFC9CED6)
    3 -> Color(0xFFD98C4A)
    else -> null
}

private val dateFormat = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

@Composable
private fun RankRow(rank: Int, r: ScoreRecord) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = c.surface, modifier = Modifier.fillMaxWidth().testTag("rank_row_$rank")) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 순위
            val medal = medalColor(rank)
            Box(
                Modifier.size(48.dp).let { if (medal != null) it.background(medal, CircleShape) else it.border(2.dp, c.outline, CircleShape) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                    color = if (medal != null) Color.Black else c.onSurface)
            }
            // 이름, 차, 날짜
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${r.carName} · ${dateFormat.format(Date(r.timeMillis))}", color = c.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            // 등급과 점수
            Box(Modifier.size(40.dp).background(gradeColor(r.grade), CircleShape), contentAlignment = Alignment.Center) {
                Text(r.grade, color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
            Text("${r.score}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("점", color = c.onSurfaceVariant, modifier = Modifier.padding(start = 0.dp))
            Spacer(Modifier.size(4.dp))
        }
    }
}
