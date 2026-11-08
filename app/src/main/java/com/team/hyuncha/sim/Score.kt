package com.team.hyuncha.sim

import kotlin.math.abs
import kotlin.math.roundToInt

/** 점수 항목 하나: 이름, 받은 점수, 만점, 무엇을 보고 매겼는지 */
data class ScoreItem(val name: String, val points: Int, val max: Int, val detail: String)

/** 주차 점수 (1000점 만점)와 등급 */
data class ParkingScore(val total: Int, val grade: String, val items: List<ScoreItem>)

/**
 * 주차 성공했을 때 점수 매기기. 5개 항목, 합계 1000점.
 * 랭킹에서 동점이 잘 안 나오도록 "이 정도면 만점" 같은 평평한 구간 없이, 조금이라도 더 정확하면 점수가 조금 더 높음.
 * 대신 잘한 구간(첫 구간)에서는 천천히 깎고, 그 뒤로 빠르게 깎아서 작은 차이에 너무 큰 감점이 나지 않게 함.
 *
 * | 항목 | 만점 | 기준 |
 * |---|---|---|
 * | 각도 정렬 | 300 | 0°에서 300 → 1°에서 285 → 7°(성공 한계)에서 60 |
 * | 좌우 위치 | 200 | 칸 가운데 200 → 남는 폭 절반만큼 치우치면 180 → 칸 선에 붙으면 40 |
 * |  (평행주차) |  | 연석까지 거리로 봄: 0cm 200 → 30cm 185 → 50cm 100 → 70cm 이상 40 |
 * | 앞뒤 위치 | 100 | 칸 가운데 100 → 남는 길이 절반 90 → 칸 끝에 붙으면 20 |
 * | 전후진 전환 | 200 | 기준 횟수 이하(T자 1, 평행 2, 사선 0) 200 → 1회 넘을 때마다 60점씩 감점 |
 * | 주행 시간 | 200 | 움직인 시간이 모범 주행 이하 200 → 1.2배 185 → 3배 이상 0 (멈춰서 확인한 시간은 뺌) |
 *
 * 등급: S 950+, A 850+, B 700+, C 550+, D
 * 부딪히면 주차 실패라 점수 없음.
 */
object Scoring {

    const val MAX_TOTAL = 1000

    /** 모드별 전후진 전환 기준 횟수 (사람이 흔히 쓰는 표준 방법 기준) */
    fun baseChanges(mode: ParkingMode) = when (mode) {
        ParkingMode.PERPENDICULAR -> 1   // 지나쳐 멈춤 → 후진
        ParkingMode.PARALLEL -> 2        // 앞차 옆에 멈춤 → 후진 → 앞으로 조금 맞춤
        ParkingMode.DIAGONAL -> 0        // 앞으로 바로 들어감
    }

    /** @param model 같은 주차장의 모범 주행 (없으면 주행 시간은 만점 처리) */
    fun score(sim: ParkingSimulator, model: ParkingSimulator?): ParkingScore? {
        val r = sim.result ?: return null
        val map = sim.map
        val car = sim.car
        val (along, across) = map.slot.toLocal(sim.bodyRect().center)

        // 1) 각도
        val angle = item("각도 정렬", 300,
            curve(r.angleErrorDeg, listOf(0.0 to 300.0, 1.0 to 285.0, ParkingSimulator.MAX_ANGLE_ERROR_DEG to 60.0)),
            "칸과 ${fmt(r.angleErrorDeg, 2)}° 어긋남")

        // 2) 좌우: 칸 폭에서 차 폭을 뺀 남는 폭(한쪽) 대비 얼마나 치우쳤는지
        val sideMargin = (map.slot.halfWidth - car.widthM / 2).coerceAtLeast(MIN_MARGIN)
        val sidePoints = listOf(0.0 to 200.0, 0.5 to 180.0, 1.0 to 40.0)
        val sideItem = if (map.mode == ParkingMode.PARALLEL) {
            // 평행주차: 칸의 폭 방향 + 쪽이 연석. 칸 폭(2.3m)이 차 폭보다 40~50cm밖에 안 넓어서 비율로 보면 감점이 너무 큼
            // → 실제로 많이 쓰는 기준인 "연석에서 몇 cm"로 봄 (30cm 이내면 거의 만점)
            val curbGap = (map.slot.halfWidth - car.widthM / 2 - across).coerceAtLeast(0.0)
            item("좌우 위치", 200, curve(curbGap, listOf(0.0 to 200.0, 0.3 to 185.0, 0.5 to 100.0, 0.7 to 40.0)), "연석까지 ${cm(curbGap)}")
        } else {
            item("좌우 위치", 200, curve(abs(across) / sideMargin, sidePoints), "칸 가운데에서 ${cm(abs(across))} 치우침")
        }

        // 3) 앞뒤
        val endMargin = (map.slot.halfLength - car.lengthM / 2).coerceAtLeast(MIN_MARGIN)
        val endItem = item("앞뒤 위치", 100, curve(abs(along) / endMargin, listOf(0.0 to 100.0, 0.5 to 90.0, 1.0 to 20.0)),
            "칸 가운데에서 앞뒤로 ${cm(abs(along))}")

        // 4) 전후진 전환
        val base = baseChanges(map.mode)
        val extra = (r.directionChanges - base).coerceAtLeast(0)
        val changes = item("전후진 전환", 200, (200.0 - extra * 60).coerceAtLeast(0.0), "${r.directionChanges}회 (기준 ${base}회)")

        // 5) 주행 시간: 같은 주차장에서 모범 주행과 비교. 멈춰서 주변을 확인한 시간은 좋은 습관이라 빼고 움직인 시간만 봄
        val mine = r.movingSec
        val ref = model?.result?.movingSec
        val time = if (ref == null || ref <= 0.0) {
            item("주행 시간", 200, 200.0, "움직인 시간 ${fmt(mine, 1)}초")
        } else {
            item("주행 시간", 200, curve(mine / ref, listOf(1.0 to 200.0, 1.2 to 185.0, 3.0 to 0.0)),
                "움직인 시간 ${fmt(mine, 1)}초 (모범 주행 ${fmt(ref, 1)}초)")
        }

        val items = listOf(angle, sideItem, endItem, changes, time)
        val total = items.sumOf { it.points }
        return ParkingScore(total, grade(total), items)
    }

    fun grade(total: Int) = when {
        total >= 950 -> "S"
        total >= 850 -> "A"
        total >= 700 -> "B"
        total >= 550 -> "C"
        else -> "D"
    }

    // 여유가 거의 없는 칸(예: 그랜저는 T자 칸보다 17cm만 짧음)에서 1cm 차이로 점수가 크게 흔들리지 않게 하는 최소 여유
    private const val MIN_MARGIN = 0.1

    /**
     * 꺾은선 점수: (값, 점수) 점들을 직선으로 이음.
     * 첫 점보다 작으면 첫 점수, 마지막 점보다 크면 마지막 점수.
     */
    fun curve(value: Double, points: List<Pair<Double, Double>>): Double {
        if (value <= points.first().first) return points.first().second
        for ((a, b) in points.zipWithNext()) {
            if (value <= b.first) return a.second + (b.second - a.second) * (value - a.first) / (b.first - a.first)
        }
        return points.last().second
    }

    private fun item(name: String, max: Int, points: Double, detail: String) =
        ScoreItem(name, points.roundToInt().coerceIn(0, max), max, detail)

    private fun fmt(v: Double, digits: Int) = "%.${digits}f".format(v)
    private fun cm(m: Double) = "${(m * 100).roundToInt()}cm"
}
