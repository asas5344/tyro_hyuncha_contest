package com.team.hyuncha.data

import android.content.Context
import com.team.hyuncha.sim.ParkingMode

/** 랭킹 기록 하나: 누가, 어떤 주차 방식으로, 어떤 차로, 몇 점을, 언제 */
data class ScoreRecord(
    val name: String,
    val mode: ParkingMode,
    val carName: String,
    val score: Int,
    val grade: String,
    val timeMillis: Long,
)

/** 이번 기록이 랭킹에서 몇 위인지 */
data class RankResult(val rank: Int, val total: Int, val isBest: Boolean)

/**
 * 랭킹 계산과 저장 형식. 안드로이드 기능을 안 써서 단위 테스트로 확인할 수 있음.
 * - 주차 방식마다 따로 순위 (T자·평행·사선은 난이도가 달라서)
 * - 점수가 높은 순, 같으면 먼저 기록한 사람이 위
 */
object Ranking {
    const val KEEP_PER_MODE = 100        // 주차 방식마다 이만큼만 저장 (낮은 점수부터 버림)
    const val MAX_NAME_LENGTH = 12

    private val order = compareByDescending<ScoreRecord> { it.score }.thenBy { it.timeMillis }

    fun sorted(records: List<ScoreRecord>, mode: ParkingMode): List<ScoreRecord> =
        records.filter { it.mode == mode }.sortedWith(order)

    /** 기록을 더한 뒤 그 기록의 순위와, 주차 방식마다 KEEP_PER_MODE개만 남긴 목록 */
    fun add(records: List<ScoreRecord>, new: ScoreRecord): Pair<List<ScoreRecord>, RankResult> {
        val all = records + new
        val ranked = sorted(all, new.mode)
        val rank = ranked.indexOf(new) + 1
        val kept = ParkingMode.entries.flatMap { m -> sorted(all, m).take(KEEP_PER_MODE) }
        return kept to RankResult(rank, ranked.size, isBest = rank == 1)
    }

    /** 이름 정리: 저장 형식을 깨는 탭·줄바꿈은 공백으로, 앞뒤 공백 제거, 길이 제한. 비면 "나" */
    fun cleanName(name: String): String =
        name.replace(Regex("[\\t\\n\\r]"), " ").trim().take(MAX_NAME_LENGTH).ifEmpty { "나" }

    /** 한 줄에 기록 하나, 칸은 탭으로 구분 */
    fun encode(records: List<ScoreRecord>): String = records.joinToString("\n") {
        listOf(cleanName(it.name), it.mode.name, it.carName, it.score, it.grade, it.timeMillis).joinToString("\t")
    }

    /** 깨진 줄은 건너뜀 (앱 버전이 바뀌어도 랭킹 화면이 죽지 않게) */
    fun decode(text: String): List<ScoreRecord> = text.lineSequence().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size != 6) return@mapNotNull null
        val mode = ParkingMode.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
        ScoreRecord(p[0], mode, p[2], p[3].toIntOrNull() ?: return@mapNotNull null, p[4], p[5].toLongOrNull() ?: return@mapNotNull null)
    }.toList()
}

/** 랭킹 기록을 이 기기(차) 안에 저장. 앱을 지우기 전까지 유지됨 */
class ScoreRecords(context: Context, name: String = "driving_mate_ranking") {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun all(): List<ScoreRecord> = Ranking.decode(prefs.getString(KEY, "") ?: "")

    /** 기록을 저장하고 순위를 돌려줌 */
    fun add(record: ScoreRecord): RankResult {
        val (kept, rank) = Ranking.add(all(), record)
        prefs.edit().putString(KEY, Ranking.encode(kept)).apply()
        return rank
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "records" }
}
