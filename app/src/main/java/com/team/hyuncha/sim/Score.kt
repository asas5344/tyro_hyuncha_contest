package com.team.hyuncha.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 점수 항목 하나: 이름, 받은 점수, 만점, 무엇을 보고 매겼는지 */
data class ScoreItem(val name: String, val points: Int, val max: Int, val detail: String)

/** 주차 점수 (100점 만점)와 등급 */
data class ParkingScore(val total: Int, val grade: String, val items: List<ScoreItem>)

/**
 * 주차 성공했을 때 점수 매기기. 6개 항목, 합계 100점.
 *
 * | 항목 | 만점 | 만점 조건 → 최저 |
 * |---|---|---|
 * | 각도 정렬 | 25 | 1° 이하 → 7°(성공 한계)에서 5점 |
 * | 좌우 위치 | 15 | 남는 폭의 절반 안 → 칸 선에 붙으면 3점 (평행주차는 연석 쪽으로 붙은 건 감점 없음) |
 * | 앞뒤 위치 | 10 | 남는 길이의 절반 안 → 칸 끝에 붙으면 2점 |
 * | 전후진 전환 | 20 | 기준 횟수 이하(T자 1, 평행 2, 사선 0) → 1회 넘을 때마다 6점씩 감점 |
 * | 안전 거리 | 15 | 주행 내내 주차된 차와 20cm 이상 → 스치듯 지나가면 3점 (벽은 칸 길이 때문에 가까울 수밖에 없어서 앞뒤 위치에서 봄) |
 * | 시간 | 15 | 모범 주행의 1.2배 이내 → 3배 이상이면 0점 |
 *
 * 등급: S 95+, A 85+, B 70+, C 55+, D
 * 부딪히면 주차 실패라 점수 없음.
 */
object Scoring {

    /** 모드별 전후진 전환 기준 횟수 (사람이 흔히 쓰는 표준 방법 기준) */
    fun baseChanges(mode: ParkingMode) = when (mode) {
        ParkingMode.PERPENDICULAR -> 1   // 지나쳐 멈춤 → 후진
        ParkingMode.PARALLEL -> 2        // 앞차 옆에 멈춤 → 후진 → 앞으로 조금 맞춤
        ParkingMode.DIAGONAL -> 0        // 앞으로 바로 들어감
    }

    /** @param model 같은 주차장의 모범 주행 (없으면 시간은 만점 처리) */
    fun score(sim: ParkingSimulator, model: ParkingSimulator?): ParkingScore? {
        val r = sim.result ?: return null
        val map = sim.map
        val car = sim.car
        val body = sim.bodyRect()
        val (along, across) = map.slot.toLocal(body.center)

        // 1) 각도
        val angle = item("각도 정렬", 25, lerpScore(r.angleErrorDeg, good = 1.0, bad = ParkingSimulator.MAX_ANGLE_ERROR_DEG, min = 5.0, max = 25.0),
            "칸과 ${fmt(r.angleErrorDeg)}° 어긋남")

        // 2) 좌우: 칸 폭에서 차 폭을 뺀 남는 폭(한쪽) 대비 얼마나 치우쳤는지
        val sideMargin = map.slot.halfWidth - car.widthM / 2
        val sideItem = if (map.mode == ParkingMode.PARALLEL) {
            // 평행주차: 칸의 폭 방향 + 쪽이 연석. 연석 쪽으로 붙는 건 좋은 주차라 바깥(도로) 쪽으로 나온 만큼만 봄
            val curbGap = (sideMargin - across).coerceAtLeast(0.0)
            val outward = (-across).coerceAtLeast(0.0)
            item("좌우 위치", 15, ratioScore(outward, sideMargin, min = 3.0, max = 15.0), "연석까지 ${cm(curbGap)}")
        } else {
            item("좌우 위치", 15, ratioScore(abs(across), sideMargin, min = 3.0, max = 15.0), "칸 가운데에서 ${cm(abs(across))} 치우침")
        }

        // 3) 앞뒤
        val endMargin = map.slot.halfLength - car.lengthM / 2
        val endItem = item("앞뒤 위치", 10, ratioScore(abs(along), endMargin, min = 2.0, max = 10.0), "칸 가운데에서 앞뒤로 ${cm(abs(along))}")

        // 4) 전후진 전환
        val base = baseChanges(map.mode)
        val extra = (r.directionChanges - base).coerceAtLeast(0)
        val changes = item("전후진 전환", 20, (20.0 - extra * 6).coerceAtLeast(0.0), "${r.directionChanges}회 (기준 ${base}회)")

        // 5) 안전 거리
        val clearance = minClearance(sim)
        val safety = item("안전 거리", 15, lerpScore(-clearance, good = -0.20, bad = 0.0, min = 3.0, max = 15.0),
            if (clearance >= NO_CAR_NEARBY) "가까이 지나간 주차된 차 없음" else "주차된 차에 가장 가까이 간 거리 ${cm(clearance)}")

        // 6) 시간: 같은 주차장에서 모범 주행과 비교 (출발 전 기다린 시간은 빼고 처음 움직인 때부터)
        val mine = movingTime(sim)
        val ref = model?.takeIf { it.result != null }?.let(::movingTime)
        val time = if (ref == null || ref <= 0.0) {
            item("시간", 15, 15.0, "${fmt(mine, 0)}초")
        } else {
            item("시간", 15, lerpScore(mine / ref, good = 1.2, bad = 3.0, min = 0.0, max = 15.0), "${fmt(mine, 0)}초 (모범 주행 ${fmt(ref, 0)}초)")
        }

        val items = listOf(angle, sideItem, endItem, changes, safety, time)
        val total = items.sumOf { it.points }
        return ParkingScore(total, grade(total), items)
    }

    fun grade(total: Int) = when {
        total >= 95 -> "S"
        total >= 85 -> "A"
        total >= 70 -> "B"
        total >= 55 -> "C"
        else -> "D"
    }

    /** 처음 움직인 때부터 주차 성공까지 걸린 시간(초) */
    fun movingTime(sim: ParkingSimulator): Double {
        val start = sim.history.firstOrNull { it.pose != sim.history.first().pose }?.timeSec ?: 0.0
        return (sim.elapsedSec - start).coerceAtLeast(0.0)
    }

    private const val NO_CAR_NEARBY = 5.0

    /**
     * 주행 기록 전체에서 내 차와 주차된 차 사이가 가장 가까웠던 거리(m). 주차된 차가 없으면 NO_CAR_NEARBY.
     * 벽은 뺌: 그랜저처럼 칸(5.2m)보다 17cm만 짧은 차는 칸 가운데에 반듯이 넣어도 뒤 벽과 8cm라서 감점하면 불공평함
     */
    fun minClearance(sim: ParkingSimulator): Double {
        var best = NO_CAR_NEARBY
        val obstacles = sim.map.parkedCars
        for (f in sim.history) {
            val body = sim.bodyRect(f.pose)
            val bodyReach = hypot(body.halfLength, body.halfWidth)
            for (o in obstacles) {
                // 감싸는 원끼리 떨어진 거리가 지금 최솟값보다 크면 계산할 필요 없음
                val gap = hypot(body.center.x - o.center.x, body.center.y - o.center.y) - bodyReach - hypot(o.halfLength, o.halfWidth)
                if (gap >= best) continue
                best = minOf(best, distance(body, o))
            }
        }
        return best
    }

    /** 두 사각형 사이 거리(m). 겹치면 0. 볼록 다각형끼리는 가장 가까운 곳에 꼭 한쪽의 꼭짓점이 있음 */
    fun distance(a: OrientedRect, b: OrientedRect): Double {
        if (a.intersects(b)) return 0.0
        fun from(p: OrientedRect, q: OrientedRect) = p.corners().minOf { c -> val n = q.closestPoint(c); hypot(c.x - n.x, c.y - n.y) }
        return minOf(from(a, b), from(b, a))
    }

    /** value가 good 이하면 max, bad 이상이면 min, 사이는 직선으로 */
    private fun lerpScore(value: Double, good: Double, bad: Double, min: Double, max: Double): Double {
        if (value <= good) return max
        if (value >= bad) return min
        return max - (max - min) * (value - good) / (bad - good)
    }

    /** 남는 여유(margin) 대비 치우친 비율: 절반 이내면 만점, 끝에 붙으면 최저. 여유가 거의 없는 칸은 들어간 것만으로 만점 */
    private fun ratioScore(offset: Double, margin: Double, min: Double, max: Double): Double =
        if (margin < 0.05) max else lerpScore(offset / margin, good = 0.5, bad = 1.0, min = min, max = max)

    private fun item(name: String, max: Int, points: Double, detail: String) =
        ScoreItem(name, points.roundToInt().coerceIn(0, max), max, detail)

    private fun fmt(v: Double, digits: Int = 1) = "%.${digits}f".format(v)
    private fun cm(m: Double) = "${(m * 100).roundToInt()}cm"
}
