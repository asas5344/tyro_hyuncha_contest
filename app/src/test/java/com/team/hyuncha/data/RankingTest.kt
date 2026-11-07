package com.team.hyuncha.data

import com.team.hyuncha.sim.ParkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    private fun rec(name: String, score: Int, time: Long, mode: ParkingMode = ParkingMode.PERPENDICULAR) =
        ScoreRecord(name, mode, "아반떼", score, "A", time)

    @Test
    fun 점수가_높은_순서_같으면_먼저_기록한_사람이_위() {
        val list = listOf(rec("가", 900, 3), rec("나", 950, 2), rec("다", 900, 1))
        assertEquals(listOf("나", "다", "가"), Ranking.sorted(list, ParkingMode.PERPENDICULAR).map { it.name })
    }

    @Test
    fun 주차_방식마다_따로_순위를_매긴다() {
        val list = listOf(rec("T", 800, 1), rec("평행", 990, 2, ParkingMode.PARALLEL))
        val (_, rank) = Ranking.add(list, rec("새 기록", 850, 3))
        assertEquals(1, rank.rank)          // 평행 990점은 T자 순위에 안 들어감
        assertEquals(2, rank.total)
        assertTrue(rank.isBest)
    }

    @Test
    fun 새_기록의_순위() {
        val list = listOf(rec("가", 990, 1), rec("나", 900, 2), rec("다", 800, 3))
        val (_, rank) = Ranking.add(list, rec("나도", 900, 4))
        assertEquals(3, rank.rank)          // 같은 900점이면 먼저 한 "나"가 위
        assertEquals(4, rank.total)
        assertFalse(rank.isBest)
    }

    @Test
    fun 주차_방식마다_정해진_개수만_남긴다() {
        val many = (1..Ranking.KEEP_PER_MODE + 5).map { rec("$it", it, it.toLong()) }
        val (kept, _) = Ranking.add(many, rec("평행", 500, 999, ParkingMode.PARALLEL))
        assertEquals(Ranking.KEEP_PER_MODE, kept.count { it.mode == ParkingMode.PERPENDICULAR })
        assertTrue("낮은 점수부터 버림", kept.none { it.mode == ParkingMode.PERPENDICULAR && it.score <= 5 })
        assertEquals(1, kept.count { it.mode == ParkingMode.PARALLEL })
    }

    @Test
    fun 저장했다_다시_읽으면_같다() {
        val list = listOf(rec("가", 990, 1), rec("나", 900, 2, ParkingMode.DIAGONAL))
        assertEquals(list, Ranking.decode(Ranking.encode(list)))
    }

    @Test
    fun 이름의_탭과_줄바꿈은_저장_형식을_깨지_않는다() {
        val back = Ranking.decode(Ranking.encode(listOf(rec("가\t나\n다", 900, 1))))
        assertEquals(1, back.size)
        assertEquals("가 나 다", back[0].name)
    }

    @Test
    fun 깨진_줄은_건너뛴다() {
        val text = Ranking.encode(listOf(rec("가", 900, 1))) + "\n이상한 줄\n\n나\tUNKNOWN\t차\t1\tA\t1"
        assertEquals(1, Ranking.decode(text).size)
    }

    @Test
    fun 이름_정리() {
        assertEquals("나", Ranking.cleanName("   "))
        assertEquals("아빠", Ranking.cleanName(" 아빠 "))
        assertEquals(Ranking.MAX_NAME_LENGTH, Ranking.cleanName("가".repeat(30)).length)
    }
}
