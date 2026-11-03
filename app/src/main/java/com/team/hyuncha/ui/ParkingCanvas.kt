package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.SimStatus
import com.team.hyuncha.sim.Vec2
import kotlin.math.min

private val Asphalt = Color(0xFF2E3136)
private val Ground = Color(0xFF3B3F45)
private val Wall = Color(0xFF6B7078)
private val LineWhite = Color(0xFFE8E8E8)
private val SlotGreen = Color(0xFF4CD08A)
private val ParkedCar = Color(0xFF8A9099)
private val MyCar = Color(0xFF3D8BFF)
private val MyCarCrashed = Color(0xFFFF5A5A)
private val Glass = Color(0xFFBFD9FF)
private val Tire = Color(0xFF15171A)
private val TrailColor = Color(0xFFFFD54F)

/** 월드 좌표(m) → 화면 좌표(px) 변환 */
private class Projection(val scale: Float, val ox: Float, val oy: Float) {
    fun toScreen(p: Vec2) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
}

/**
 * 탑뷰 주차장 그리기.
 * @param frame 매 프레임 바뀌는 값. draw 안에서 읽어야 프레임마다 다시 그려짐
 */
@Composable
fun ParkingCanvas(sim: ParkingSimulator, frame: () -> Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        frame()  // 이 값을 읽어둬야 시뮬레이터가 움직일 때 다시 그려짐
        val map = sim.map

        // 주차장 전체가 화면에 들어오도록 축척 계산 (여백 1m)
        val margin = 1.0
        val scale = min(
            size.width / (map.widthM + margin * 2),
            size.height / (map.heightM + margin * 2),
        ).toFloat()
        val proj = Projection(
            scale = scale,
            ox = (size.width - map.widthM.toFloat() * scale) / 2,
            oy = (size.height - map.heightM.toFloat() * scale) / 2,
        )

        drawRect(Asphalt)
        drawRect(
            Ground,
            topLeft = proj.toScreen(Vec2(0.0, 0.0)),
            size = androidx.compose.ui.geometry.Size(
                map.widthM.toFloat() * scale, map.heightM.toFloat() * scale,
            ),
        )

        // 목표 칸: 칸 안에 들어오면 진하게 표시
        val slotAlpha = if (sim.isInsideSlot) 0.45f else 0.18f
        drawOriented(map.slot, proj, SlotGreen.copy(alpha = slotAlpha))
        drawOriented(
            map.slot, proj, SlotGreen,
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
        )

        map.lines.forEach { (a, b) ->
            drawLine(LineWhite, proj.toScreen(a), proj.toScreen(b), strokeWidth = 0.12f * scale)
        }
        map.walls.forEach { drawOriented(it, proj, Wall) }
        map.parkedCars.forEach { drawOriented(it, proj, ParkedCar) }

        // 지나온 경로
        if (sim.trail.size >= 2) {
            val path = Path()
            sim.trail.forEachIndexed { i, p ->
                val o = proj.toScreen(Vec2(p.x, p.y))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, TrailColor.copy(alpha = 0.6f), style = Stroke(width = 2.5f))
        }

        drawMyCar(sim, proj)
    }
}

/** 내 차: 차체 + 앞유리 + 바퀴 4개 (앞바퀴는 조향각만큼 꺾여서 그려짐) */
private fun DrawScope.drawMyCar(sim: ParkingSimulator, proj: Projection) {
    val car = sim.car
    val pose = sim.pose
    val forward = Vec2.unit(pose.heading)
    val side = Vec2.unit(pose.heading + Math.PI / 2)
    val rearAxle = Vec2(pose.x, pose.y)
    val frontAxle = rearAxle + forward * car.wheelBaseM
    val halfTrack = car.widthM / 2 - 0.2

    val wheelHeading = pose.heading + Math.toRadians(sim.wheelAngleDeg)

    val body = sim.bodyRect()
    val bodyColor = if (sim.status == SimStatus.COLLIDED) MyCarCrashed else MyCar
    drawOriented(body.copy(halfWidth = body.halfWidth - 0.08), proj, bodyColor.copy(alpha = 0.9f))

    // 앞유리: 어느 쪽이 앞인지 보이게
    val glassCenter = body.center + forward * (car.lengthM * 0.18)
    drawOriented(OrientedRect(glassCenter, 0.35, car.widthM / 2 - 0.25, pose.heading), proj, Glass)

    // 바퀴: 핸들을 돌리면 앞바퀴가 꺾이는 게 보이도록 차체 위에 그림
    val wheelHalfLen = 0.33
    val wheelHalfWid = 0.12
    for (s in listOf(-1.0, 1.0)) {
        drawOriented(OrientedRect(rearAxle + side * (halfTrack * s), wheelHalfLen, wheelHalfWid, pose.heading), proj, Tire)
        drawOriented(OrientedRect(frontAxle + side * (halfTrack * s), wheelHalfLen, wheelHalfWid, wheelHeading), proj, Tire)
    }

    // 앞바퀴를 크게 보여주는 보조선: 바퀴가 향하는 방향
    val tip = frontAxle + Vec2.unit(wheelHeading) * 1.2
    drawLine(Color.White.copy(alpha = 0.8f), proj.toScreen(frontAxle), proj.toScreen(tip), strokeWidth = 3f)
}

private fun DrawScope.drawOriented(
    rect: OrientedRect,
    proj: Projection,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    val path = Path()
    rect.corners().forEachIndexed { i, c ->
        val o = proj.toScreen(c)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color, style = style)
}
