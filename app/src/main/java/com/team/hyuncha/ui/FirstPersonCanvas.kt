package com.team.hyuncha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.data.CarType
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.LookButtons
import com.team.hyuncha.input.LookSide
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.Guidance
import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingMode
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.sim.Vec2
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

// 1인칭 색
private val Ceiling = Color(0xFF141619)          // 지하 주차장 천장
private val FarFloor = Color(0xFF26292D)         // 주차장 밖 바닥
private val Asphalt3D = Color(0xFF3A3E44)
private val Line3D = Color(0xFFE6E6E2)
private val Curb3D = Color(0xFFD9AE3A)
private val Wall3D = Color(0xFF6A6E75)
private val Stop3D = Color(0xFFC9A646)
private val Target3D = Color(0xFF4CD08A)
private val Glass3D = Color(0xFF1C2631)
private val Dash = Color(0xFF17191C)
private val Pillar = Color(0xFF101113)
private val WheelRim = Color(0xFF2B2E33)
private val Hood = Color(0xFFA9ACB1)              // 흰 차지만 어두운 지하 주차장이라 회색빛으로
private val GuideRed3D = Color(0xFFFF5A52)
private val GuideYellow3D = Color(0xFFFFD54F)
private val GuideGreen3D = Color(0xFF5BD68E)

/** 3D 점(m). x·y는 탑뷰와 같은 바닥 좌표(y가 아래로 양수), z는 위쪽 */
internal data class V3(val x: Double, val y: Double, val z: Double)

private const val NEAR = 0.1   // 카메라 바로 앞 이 거리보다 가까운 부분은 잘라냄 (m)

/**
 * 원근 카메라: eye에서 heading 방향을 보고 pitch만큼 아래를 내려다봄.
 * 카메라 좌표는 (앞 거리, 오른쪽, 위쪽)이고, 앞 거리로 나눠서 멀수록 작게 그림.
 * @param mirror 좌우를 뒤집어서 그림 (후방 카메라 화면은 거울처럼 보여줌)
 */
internal class PerspectiveCam(
    val eye: V3,
    heading: Double,
    pitchDeg: Double,
    vFovDeg: Double,
    val area: Rect,
    val mirror: Boolean = false,
) {
    private val pitch = Math.toRadians(pitchDeg)
    private val fx = cos(heading) * cos(pitch)
    private val fy = sin(heading) * cos(pitch)
    private val fz = -sin(pitch)
    private val rx = -sin(heading)                 // 오른쪽 (탑뷰에서 차의 오른쪽과 같음)
    private val ry = cos(heading)
    private val ux = cos(heading) * sin(pitch)
    private val uy = sin(heading) * sin(pitch)
    private val uz = cos(pitch)
    val focal = (area.height / 2) / tan(Math.toRadians(vFovDeg / 2))
    private val cx = area.center.x
    private val cy = area.center.y

    /** 월드 → 카메라 좌표 (x = 앞 거리, y = 오른쪽, z = 위쪽) */
    fun toCam(w: V3): V3 {
        val dx = w.x - eye.x
        val dy = w.y - eye.y
        val dz = w.z - eye.z
        return V3(dx * fx + dy * fy + dz * fz, dx * rx + dy * ry, dx * ux + dy * uy + dz * uz)
    }

    fun project(c: V3): Offset = Offset(
        cx + (if (mirror) -1f else 1f) * (c.y / c.x * focal).toFloat(),
        cy - (c.z / c.x * focal).toFloat(),
    )

    /** 지평선(눈높이의 아주 먼 곳)의 화면 y */
    val horizonY: Float get() = (cy - tan(pitch) * focal).toFloat()
}

/** 카메라 앞쪽(앞 거리 NEAR 이상)만 남기도록 다각형을 자름 */
private fun clipNear(poly: List<V3>): List<V3> {
    val out = ArrayList<V3>(poly.size + 2)
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val ain = a.x >= NEAR
        val bin = b.x >= NEAR
        if (ain) out += a
        if (ain != bin) {
            val t = (NEAR - a.x) / (b.x - a.x)
            out += V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
        }
    }
    return out
}

private fun DrawScope.poly3(cam: PerspectiveCam, pts: List<V3>, color: Color) {
    val clipped = clipNear(pts.map(cam::toCam))
    if (clipped.size < 3) return
    val path = Path()
    clipped.forEachIndexed { i, c ->
        val o = cam.project(c)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, color)
}

/** 바닥에 그린 두께 있는 선 (원근에 맞게 멀수록 가늘어짐) */
private fun DrawScope.groundStrip(cam: PerspectiveCam, a: Vec2, b: Vec2, widthM: Double, color: Color, z: Double = 0.01) {
    val len = hypot(b.x - a.x, b.y - a.y)
    if (len < 1e-6) return
    val nx = -(b.y - a.y) / len * widthM / 2
    val ny = (b.x - a.x) / len * widthM / 2
    poly3(cam, listOf(V3(a.x + nx, a.y + ny, z), V3(b.x + nx, b.y + ny, z), V3(b.x - nx, b.y - ny, z), V3(a.x - nx, a.y - ny, z)), color)
}

private fun DrawScope.groundRect(cam: PerspectiveCam, r: OrientedRect, color: Color, z: Double = 0.005) =
    poly3(cam, r.corners().map { V3(it.x, it.y, z) }, color)

/** 바닥에 놓인 상자 (주차된 차·벽·바퀴 멈춤턱) */
private class Box(val foot: OrientedRect, val z0: Double, val z1: Double, val side: Color, val top: Color)

private fun shade(c: Color, k: Double) = Color((c.red * k).toFloat().coerceIn(0f, 1f), (c.green * k).toFloat().coerceIn(0f, 1f), (c.blue * k).toFloat().coerceIn(0f, 1f), c.alpha)

/** 상자의 카메라 쪽 면만 그림. 볼록한 상자라 보이는 면끼리는 겹치지 않음 */
private fun DrawScope.box3(cam: PerspectiveCam, b: Box) {
    val c = b.foot.corners()
    val center = b.foot.center
    for (i in 0 until 4) {
        val p = c[i]
        val q = c[(i + 1) % 4]
        val mx = (p.x + q.x) / 2
        val my = (p.y + q.y) / 2
        val nx = mx - center.x
        val ny = my - center.y
        if (nx * (cam.eye.x - mx) + ny * (cam.eye.y - my) <= 0) continue   // 카메라 반대쪽 면
        // 빛은 위·앞쪽에서: 면 방향에 따라 조금씩 밝기를 다르게
        val n = hypot(nx, ny).coerceAtLeast(1e-6)
        val light = 0.62 + 0.3 * max(0.0, (nx * 0.4 - ny * 0.6) / n)
        poly3(cam, listOf(V3(p.x, p.y, b.z0), V3(q.x, q.y, b.z0), V3(q.x, q.y, b.z1), V3(p.x, p.y, b.z1)), shade(b.side, light))
    }
    if (cam.eye.z > b.z1) poly3(cam, c.map { V3(it.x, it.y, b.z1) }, b.top)
}

/**
 * 원근 카메라로 본 주차장: 천장·바닥·주차선·목표 칸·예상 진행선·벽·주차된 차.
 * 내 차는 그리지 않음 (운전석·후방 카메라에서는 내 차가 안 보이므로).
 */
private fun DrawScope.drawPerspectiveScene(scene: Scene, assets: TopAssets, cam: PerspectiveCam, guideDirection: Direction?, ownCar: Boolean = false) {
    val map = scene.sim.map
    val a = cam.area
    // 하늘(천장)과 먼 바닥
    val hy = cam.horizonY.coerceIn(a.top, a.bottom)
    drawRect(Brush.verticalGradient(listOf(Ceiling, Color(0xFF22252A)), startY = a.top, endY = hy), topLeft = a.topLeft, size = Size(a.width, hy - a.top))
    drawRect(FarFloor, topLeft = Offset(a.left, hy), size = Size(a.width, a.bottom - hy))

    // 주차장 바닥, 연석, 주차선
    poly3(cam, listOf(V3(0.0, 0.0, 0.0), V3(map.widthM, 0.0, 0.0), V3(map.widthM, map.heightM, 0.0), V3(0.0, map.heightM, 0.0)), Asphalt3D)
    val corners = listOf(Vec2(0.0, 0.0), Vec2(map.widthM, 0.0), Vec2(map.widthM, map.heightM), Vec2(0.0, map.heightM))
    corners.zip(corners.drop(1) + corners.first()).forEach { (p, q) -> groundStrip(cam, p, q, 0.14, Curb3D) }
    map.lines.forEach { (p, q) -> groundStrip(cam, p, q, 0.12, Line3D) }

    // 목표 칸: 바닥을 초록으로 + 테두리
    groundRect(cam, map.slot, Target3D.copy(alpha = 0.28f))
    val sc = map.slot.corners()
    sc.zip(sc.drop(1) + sc.first()).forEach { (p, q) -> groundStrip(cam, p, q, 0.1, Target3D, z = 0.012) }

    // 주차된 차 그림자
    map.parkedCars.forEach { groundRect(cam, it.copy(halfLength = it.halfLength + 0.15, halfWidth = it.halfWidth + 0.12), Color.Black.copy(alpha = 0.35f), z = 0.008) }

    // 예상 진행선 (탑뷰와 같은 색: 1m 빨강 → 2m 노랑 → 초록)
    if (guideDirection != null && scene.showGuide) {
        val pts = Guidance.predictPath(scene.sim.car, scene.pose, scene.wheelAngleDeg, guideDirection)
        fun colorAt(d: Double) = when { d < 1.0 -> GuideRed3D; d < 2.0 -> GuideYellow3D; else -> GuideGreen3D }
        pts.zipWithNext().forEach { (p, q) ->
            poly3(cam, listOf(V3(p.left.x, p.left.y, 0.015), V3(q.left.x, q.left.y, 0.015), V3(q.right.x, q.right.y, 0.015), V3(p.right.x, p.right.y, 0.015)), GuideYellow3D.copy(alpha = 0.10f))
            val c = colorAt(p.distanceM)
            groundStrip(cam, p.left, q.left, 0.09, c, z = 0.02)
            groundStrip(cam, p.right, q.right, 0.09, c, z = 0.02)
        }
        listOf(1.0, 2.0, 3.0).forEach { mark ->
            pts.minByOrNull { kotlin.math.abs(it.distanceM - mark) }?.let { p -> groundStrip(cam, p.left, p.right, 0.07, colorAt(mark - 0.01), z = 0.02) }
        }
    }

    // 벽은 항상 맨 뒤(주차장 바깥 테두리)라 먼저 그림
    map.walls.forEach { box3(cam, Box(it, 0.0, 2.6, Wall3D, shade(Wall3D, 1.1))) }

    // 주차된 차·바퀴 멈춤턱: 먼 것부터 그려서 가까운 것이 위에 오게
    val boxes = ArrayList<Pair<Double, List<Box>>>()
    map.parkedCars.forEachIndexed { i, r ->
        val tint = assets.parkedStyles.getOrNull(i)?.tint ?: Color.Gray
        val bodyColor = shade(tint, 1.15)
        val cabin = r.copy(halfLength = r.halfLength * 0.52, halfWidth = r.halfWidth * 0.86)
        val parts = listOf(
            Box(r, 0.18, 0.82, bodyColor, shade(bodyColor, 1.12)),
            Box(cabin, 0.82, 1.42, Glass3D, shade(bodyColor, 1.05)),
        )
        boxes += hypot(r.center.x - cam.eye.x, r.center.y - cam.eye.y) to parts
    }
    if (map.mode != ParkingMode.PARALLEL) {
        map.bays.forEach { bay ->
            val stop = bay.center + Vec2.unit(bay.angle) * (bay.halfLength - 0.55)
            val foot = OrientedRect(stop, halfLength = 0.09, halfWidth = 0.75, angle = bay.angle)
            boxes += hypot(stop.x - cam.eye.x, stop.y - cam.eye.y) to listOf(Box(foot, 0.0, 0.12, Stop3D, shade(Stop3D, 1.15)))
        }
    }
    // 사이드미러에서는 내 차 옆면이 보임 (흰 차)
    if (ownCar) {
        val r = scene.body
        val cabin = r.copy(halfLength = r.halfLength * 0.52, halfWidth = r.halfWidth * 0.86)
        boxes += hypot(r.center.x - cam.eye.x, r.center.y - cam.eye.y) to listOf(
            Box(r, 0.18, BELT_Z, OwnCarBody, shade(OwnCarBody, 1.08)),
            Box(cabin, BELT_Z, 1.42, Glass3D, shade(OwnCarBody, 1.0)),
        )
    }
    boxes.sortedByDescending { it.first }.forEach { (_, parts) -> parts.forEach { box3(cam, it) } }
}

/** 차 기준 좌표(앞 a, 오른쪽 b, 높이 z)를 월드 좌표로 */
private fun local(pose: CarPose, a: Double, b: Double, z: Double): V3 {
    val c = cos(pose.heading)
    val s = sin(pose.heading)
    return V3(pose.x + c * a - s * b, pose.y + s * a + c * b, z)
}

private const val EYE_B = -0.37     // 운전석 눈의 옆 위치 (한국은 왼쪽 운전석)
private const val BELT_Z = 1.0      // 창문 아래 선(문 위쪽)·앞유리 아래쪽 높이

private fun eyeA(car: CarSpec) = car.lengthM * 0.45 - car.rearOverhangM           // 눈의 앞뒤 위치 (뒷바퀴 축 기준)
private fun eyeZ(car: CarSpec) = if (car.type == CarType.SUV) 1.38 else 1.18
private fun windshieldA(car: CarSpec) = eyeA(car) + 0.85                             // 앞유리 아래쪽

/**
 * 운전석 눈 위치: 차 길이의 약 45% 지점, 왼쪽 좌석, 세단·SUV 높이 다르게.
 * 기본은 거의 수평으로 앞을 봄. 보닛 바로 아래부터는 대시보드에 가려 바닥이 안 보이는 실제 운전석과 같게.
 * @param yawDeg 고개를 돌린 각도 (오른쪽 양수)
 */
private fun driverCam(car: CarSpec, pose: CarPose, area: Rect, yawDeg: Double = 0.0, pitchDeg: Double = 2.0, vFovDeg: Double = 56.0) =
    PerspectiveCam(local(pose, eyeA(car), EYE_B, eyeZ(car)), pose.heading + Math.toRadians(yawDeg), pitchDeg = pitchDeg, vFovDeg = vFovDeg, area = area)

/** 후방 카메라: 뒤 범퍼 위에서 뒤쪽 바닥을 넓게 내려다봄. 실제 차처럼 좌우를 거울처럼 뒤집어 보여줌 */
private fun rearCam(car: CarSpec, pose: CarPose, area: Rect): PerspectiveCam {
    val z = if (car.type == CarType.SUV) 1.05 else 0.95
    return PerspectiveCam(local(pose, -car.rearOverhangM + 0.05, 0.0, z), pose.heading + Math.PI, pitchDeg = 34.0, vFovDeg = 72.0, area = area, mirror = true)
}

/** 화면에 보이는 값을 목표값까지 부드럽게 따라가게 함 (차량 데이터는 초당 약 10번 들어와서 그대로 그리면 계단처럼 움직임) */
private class Smoothed {
    private var value = 0f
    private var lastNanos = 0L
    private var started = false

    /** @param tau 따라가는 속도(초). 작을수록 빨리 따라감 */
    fun update(target: Float, nowNanos: Long, tau: Float = 0.09f): Float {
        if (!started) {
            value = target
            started = true
        } else {
            val dt = ((nowNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
            value += (target - value) * (1f - exp(-dt / tau))
        }
        lastNanos = nowNanos
        return value
    }
}

/** 고개 방향: 좌우 각도(오른쪽 양수), 내려다보는 각도, 시야 (사이드미러를 볼 때는 시야를 좁혀서 미러에 집중) */
private class Head(val yawDeg: Double, val pitchDeg: Double, val vFovDeg: Double)

private val FrontHead = Head(0.0, 2.0, 56.0)

/** 사이드미러를 정면으로 보도록 고개를 돌린 방향. 미러가 화면 높이의 약 1/3로 보이게 시야를 좁힘 (주변 창틀도 같이 보이게) */
private fun headFor(car: CarSpec, side: LookSide): Head {
    if (side == LookSide.FRONT) return FrontHead
    val m = mirrorCenter(car, side.step)
    val da = m.x - eyeA(car)
    val db = m.y - EYE_B
    val dz = m.z - eyeZ(car)
    val flat = hypot(da, db)
    val dist = hypot(flat, dz)
    val fov = Math.toDegrees(2 * atan(MIRROR_H / (0.64 * dist))).coerceIn(16.0, 56.0)
    return Head(Math.toDegrees(atan2(db, da)), Math.toDegrees(atan2(-dz, flat)), fov)
}

/** The same driving aid used by the original cockpit, shared with the 3D presentation. */
@Composable
internal fun DriverMinimap(
    sim: ParkingSimulator,frame: () -> Long,direction: () -> Direction,braking: () -> Boolean,
    replay: () -> ReplayView?,modifier: Modifier,
) {
    val assets=rememberTopAssets(sim)
    val textMeasurer=rememberTextMeasurer()
    Canvas(modifier) {
        frame()
        val scene=sceneOf(sim,replay(),direction(),braking())
        val area=Rect(0f,0f,size.width,size.height)
        drawScreenFrame(area,"미니맵",textMeasurer) {
            drawSurround(scene,assets,area,viewM=18.0,textMeasurer=textMeasurer)
        }
    }
}

/**
 * 1인칭(운전석) 주차 화면.
 * - 운전석에서 본 앞유리 너머 + 실내(보닛·와이퍼·앞 기둥·문·룸미러·선바이저·대시보드·송풍구·계기판·핸들)
 * - 핸들의 ◀◀ / ▶▶ 버튼(LookButtons)으로 고개를 돌려 왼쪽·오른쪽 사이드미러를 봄
 * - 후진하면 대시보드 위 차량 화면에 내 차 카메라 종류에 따라 후방 카메라 또는 탑뷰(서라운드 뷰)
 * - 화면 왼쪽에 항상 미니맵 (연습용 도우미)
 */
@Composable
fun FirstPersonCanvas(
    sim: ParkingSimulator,
    frame: () -> Long,
    direction: () -> Direction,
    braking: () -> Boolean,
    camera: CarCamera,
    modifier: Modifier = Modifier,
    replay: () -> ReplayView? = { null },
) {
    val textMeasurer = rememberTextMeasurer()
    val assets = rememberTopAssets(sim)
    val steeringShown = remember { Smoothed() }
    val yawShown = remember { Smoothed() }
    val pitchShown = remember { Smoothed() }
    val fovShown = remember { Smoothed() }

    // 이 화면이 떠 있는 동안만 핸들 버튼을 고개 돌리기로 씀
    DisposableEffect(Unit) {
        LookButtons.active = true
        onDispose { LookButtons.active = false }
    }

    Canvas(modifier) {
        frame()
        val scene = sceneOf(sim, replay(), direction(), braking())
        val car = sim.car
        val main = Rect(0f, 0f, size.width, size.height)
        val now = System.nanoTime()
        val side = LookButtons.side
        val target = headFor(car, side)
        val head = Head(
            yawShown.update(target.yawDeg.toFloat(), now, tau = 0.14f).toDouble(),
            pitchShown.update(target.pitchDeg.toFloat(), now, tau = 0.14f).toDouble(),
            fovShown.update(target.vFovDeg.toFloat(), now, tau = 0.14f).toDouble(),
        )
        val front = driverCam(car, scene.pose, main)
        val cam = driverCam(car, scene.pose, main, head.yawDeg, head.pitchDeg, head.vFovDeg)
        val dashTop = dashboardTop(front, car, scene.pose, main)

        // 1) 창 너머
        clipRect(main.left, main.top, main.right, main.bottom) {
            drawPerspectiveScene(scene, assets, cam, Direction.FORWARD.takeIf { scene.direction == Direction.FORWARD })
        }
        // 2) 차체·실내 중 고개를 돌리면 원근이 바뀌는 것 (3D로 그림)
        drawHood(cam, car, scene.pose)
        drawCabin3D(cam, car, scene.pose)
        listOf(-1, 1).forEach { s -> drawSideMirror(scene, assets, cam, car, s, main) }
        drawPillars(cam, car, scene.pose)

        // 3) 운전자 앞쪽 실내: 정면 기준으로 그린 뒤, 고개를 돌린 만큼 화면에서 밀어내고 확대
        val ahead = local(scene.pose, eyeA(car) + 50.0, EYE_B, eyeZ(car))
        val pFront = front.project(front.toCam(ahead))
        val aheadCam = cam.toCam(ahead)
        val pTurned = if (aheadCam.x > NEAR) cam.project(aheadCam) else Offset(pFront.x - size.width * 3, pFront.y)
        val k = (cam.focal / front.focal).toFloat()
        val steeringDeg = steeringShown.update((scene.wheelAngleDeg * car.steeringRatio).toFloat(), now)
        withTransform({
            translate(pTurned.x - pFront.x, pTurned.y - pFront.y)
            scale(k, k, pivot = pFront)
        }) {
            drawWindshieldFrame(main)
            drawRoomMirror(scene, assets, car, main)
            drawDashboard(main, dashTop)
            drawCluster(scene, main, dashTop, textMeasurer)
            drawSteeringWheel(main, dashTop, steeringDeg)
            if (scene.direction == Direction.REVERSE) drawCarDisplay(scene, assets, camera, main, dashTop, textMeasurer)
        }

        // 4) 미니맵: 연습용 도우미라 실내에 붙이지 않고 화면 왼쪽에 고정 (미러를 볼 때도 보임)
        val mw = size.width * 0.24f
        val mini = Rect(size.width * 0.02f, size.height * 0.35f, size.width * 0.02f + mw, size.height * 0.35f + mw * 0.66f)
        drawScreenFrame(mini, "미니맵", textMeasurer) {
            drawSurround(scene, assets, mini, viewM = 18.0, textMeasurer = textMeasurer)
        }

        // 5) 버튼 안내
        val hint = when (side) {
            LookSide.FRONT -> "◀◀ 왼쪽 미러   ▶▶ 오른쪽 미러"
            LookSide.LEFT -> "왼쪽 사이드미러 보는 중 · ▶▶ 앞으로"
            LookSide.RIGHT -> "오른쪽 사이드미러 보는 중 · ◀◀ 앞으로"
        }
        val strong = side != LookSide.FRONT
        val layout = textMeasurer.measure(hint, TextStyle(color = Color.White.copy(alpha = if (strong) 1f else 0.75f), fontSize = (19f).toSp(),
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal))
        val hx = size.width * 0.42f - layout.size.width / 2f
        val hy = size.height * 0.025f
        drawRoundRect(Color.Black.copy(alpha = if (strong) 0.65f else 0.4f), topLeft = Offset(hx - 18f, hy - 8f),
            size = Size(layout.size.width + 36f, layout.size.height + 16f), cornerRadius = CornerRadius(40f))
        drawText(layout, topLeft = Offset(hx, hy))
    }
}

/** 후진할 때 대시보드 위 차량 화면: 내 차 카메라 종류에 따라 후방 카메라 또는 탑뷰(서라운드 뷰) */
private fun DrawScope.drawCarDisplay(scene: Scene, assets: TopAssets, camera: CarCamera, main: Rect, dashTop: Float, textMeasurer: TextMeasurer) {
    val car = scene.sim.car
    val iw = main.width * 0.42f
    val ih = iw * 0.62f
    val right = main.right - main.width * 0.03f
    val bottom = (dashTop + main.height * 0.08f).coerceAtMost(main.bottom - 150f)
    val inset = Rect(right - iw, bottom - ih, right, bottom)
    val rearCamera = camera == CarCamera.REAR_ONLY
    val label = if (rearCamera) "후방 카메라" else "서라운드 뷰"
    drawScreenFrame(inset, label, textMeasurer) {
        if (rearCamera) {
            drawPerspectiveScene(scene, assets, rearCam(car, scene.pose, inset), Direction.REVERSE)
            drawRearCamOverlay(inset)
        } else {
            drawSurround(scene, assets, inset, viewM = 13.0, textMeasurer = textMeasurer, focusY = 0.36f)
        }
    }
}

/** 화면 속 작은 화면: 그림자·검은 테두리·둥근 모서리, 왼쪽 위에 이름표 */
private fun DrawScope.drawScreenFrame(r: Rect, label: String, textMeasurer: TextMeasurer, content: DrawScope.() -> Unit) {
    drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = r.topLeft + Offset(6f, 10f), size = r.size, cornerRadius = CornerRadius(26f))
    drawRoundRect(Color(0xFF0B0C0E), topLeft = r.topLeft - Offset(10f, 10f), size = Size(r.width + 20f, r.height + 20f), cornerRadius = CornerRadius(28f))
    clipPath(Path().apply { addRoundRect(RoundRect(r, CornerRadius(20f))) }) { content() }
    drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = r.topLeft - Offset(10f, 10f), size = Size(r.width + 20f, r.height + 20f),
        cornerRadius = CornerRadius(28f), style = Stroke(width = 2f))
    val layout = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = (18f).toSp(), fontWeight = FontWeight.Bold))
    drawRoundRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(r.left + 12f, r.top + 12f),
        size = Size(layout.size.width + 22f, layout.size.height + 10f), cornerRadius = CornerRadius(12f))
    drawText(layout, topLeft = Offset(r.left + 23f, r.top + 17f))
}

// ───────── 고개를 돌리면 보이는 차체 (3D) ─────────

private val DoorTrim = Color(0xFF1C1E22)
private val DoorSill = Color(0xFF2B2E33)
private val MirrorBody = Color(0xFFB9BCC1)
private val OwnCarBody = Color(0xFFC9CCD0)        // 사이드미러에 비치는 내 차 옆면        // 내 차(흰색) 미러 덮개. 어두운 주차장이라 회색빛
private const val MIRROR_W = 0.24                  // 사이드미러 거울 크기 (m)
private const val MIRROR_H = 0.16

/** 대시보드 윗면·앞면과 양쪽 문 안쪽 면. 정면에서는 화면 밖이지만 고개를 돌리면 보임 */
private fun DrawScope.drawCabin3D(cam: PerspectiveCam, car: CarSpec, pose: CarPose) {
    val hw = car.widthM / 2
    val ws = windshieldA(car)
    val ea = eyeA(car)
    val dashBack = ea + 0.5
    poly3(cam, listOf(local(pose, ws, -(hw - 0.05), BELT_Z), local(pose, ws, hw - 0.05, BELT_Z),
        local(pose, dashBack, hw - 0.05, BELT_Z - 0.02), local(pose, dashBack, -(hw - 0.05), BELT_Z - 0.02)), DashTopColor)
    poly3(cam, listOf(local(pose, dashBack, -(hw - 0.05), BELT_Z - 0.02), local(pose, dashBack, hw - 0.05, BELT_Z - 0.02),
        local(pose, dashBack, hw - 0.05, 0.3), local(pose, dashBack, -(hw - 0.05), 0.3)), Dash)
    for (s in listOf(-1, 1)) {
        val inner = s * (hw - 0.07)
        val outer = s * (hw - 0.01)
        // 문 안쪽 면, 그 위 창틀
        poly3(cam, listOf(local(pose, ea - 1.0, inner, 0.3), local(pose, ws, inner, 0.3), local(pose, ws, inner, BELT_Z), local(pose, ea - 1.0, inner, BELT_Z)), DoorTrim)
        poly3(cam, listOf(local(pose, ea - 1.0, inner, BELT_Z), local(pose, ws, inner, BELT_Z), local(pose, ws, outer, BELT_Z), local(pose, ea - 1.0, outer, BELT_Z)), DoorSill)
    }
}

/** 앞 기둥(A필러) 2개: 앞유리 양쪽에서 천장까지 비스듬히 올라감 */
private fun DrawScope.drawPillars(cam: PerspectiveCam, car: CarSpec, pose: CarPose) {
    val hw = car.widthM / 2
    val ws = windshieldA(car)
    val roofZ = eyeZ(car) + 0.26
    for (s in listOf(-1, 1)) {
        poly3(cam, listOf(
            local(pose, ws + 0.03, s * (hw - 0.02), BELT_Z), local(pose, ws - 0.16, s * (hw - 0.22), BELT_Z),
            local(pose, ws - 0.55, s * (hw - 0.3), roofZ), local(pose, ws - 0.4, s * (hw - 0.08), roofZ),
        ), Pillar)
    }
}

/** 사이드미러 거울 가운데 (차 기준: x = 앞, y = 오른쪽, z = 높이). side: -1 왼쪽, 1 오른쪽 */
private fun mirrorCenter(car: CarSpec, side: Int) = V3(windshieldA(car) - 0.4, side * (car.widthM / 2 + 0.17), BELT_Z + 0.09)

/**
 * 사이드미러: 문 밖에 붙은 덮개 + 거울. 거울에는 차 뒤쪽 옆이 좌우가 뒤집혀 비침.
 * 거울면은 운전자 눈과 "뒤쪽 바깥 8도" 방향의 가운데를 향하게 세움 (반사 법칙).
 * 거울 안쪽 가장자리에는 실제처럼 내 차 옆면이 조금 보임.
 */
private fun DrawScope.drawSideMirror(scene: Scene, assets: TopAssets, cam: PerspectiveCam, car: CarSpec, side: Int, screen: Rect) {
    val pose = scene.pose
    val m = mirrorCenter(car, side)
    val delta = Math.toRadians(8.0)
    val ra = -cos(delta)
    val rb = side * sin(delta)
    val ea = eyeA(car) - m.x
    val eb = EYE_B - m.y
    val el = hypot(ea, eb)
    var na = ea / el + ra
    var nb = eb / el + rb
    val nl = hypot(na, nb)
    na /= nl; nb /= nl
    val ua = -nb                // 거울면의 가로 방향
    val ub = na
    val hw = MIRROR_W / 2
    val hh = MIRROR_H / 2
    fun pt(u: Double, v: Double, n: Double = 0.0) = local(pose, m.x + ua * u + na * n, m.y + ub * u + nb * n, m.z + v)
    // 실제 미러처럼 모서리가 둥글고 바깥쪽이 조금 더 높은 모양 (u 방향으로 +가 바깥쪽인지)
    val outward = if (side < 0) 1.0 else -1.0
    fun outline(scale: Double, n: Double) = (0 until 32).map { i ->
        val t = i / 32.0 * 2 * Math.PI
        val cu = cos(t)
        val sv = sin(t)
        val u = Math.signum(cu) * Math.sqrt(kotlin.math.abs(cu)) * hw * scale
        val v = Math.signum(sv) * Math.sqrt(kotlin.math.abs(sv)) * hh * scale * (1 + 0.15 * outward * u / hw)
        pt(u, v, n)
    }

    // 덮개: 거울 뒤쪽 껍데기와 거울을 감싼 테두리
    poly3(cam, outline(1.12, -0.08), shade(MirrorBody, 0.82))
    poly3(cam, outline(1.1, 0.0), MirrorBody)
    // 미러 받침 (문과 덮개를 이어줌)
    val bodyOuter = side * (car.widthM / 2 - 0.01)
    poly3(cam, listOf(local(pose, m.x + 0.14, bodyOuter, BELT_Z + 0.02), local(pose, m.x + 0.02, bodyOuter, BELT_Z + 0.02),
        pt(-outward * hw * 0.55, -hh * 0.6, -0.05), pt(-outward * hw * 0.15, -hh * 0.6, -0.05)), shade(MirrorBody, 0.7))

    // 거울면
    val glass = outline(1.0, 0.004).map(cam::toCam)
    if (glass.any { it.x < NEAR }) return
    val pts = glass.map(cam::project)
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
    }
    val bounds = Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    if (!bounds.overlaps(screen) || bounds.height < 4f) return
    clipPath(path) {
        // 주차할 때처럼 아래로 기울여서 바닥의 주차선·옆 차·내 차 뒷바퀴 쪽이 보이게 (조수석 쪽은 더 아래로).
        // 실제 사이드미러는 볼록거울이라 평면거울보다 넓게 비춤
        val view = PerspectiveCam(local(pose, m.x, m.y, m.z), pose.heading + atan2(rb, ra), pitchDeg = if (side < 0) 14.0 else 18.0,
            vFovDeg = if (side < 0) 34.0 else 40.0, area = bounds, mirror = true)
        drawPerspectiveScene(scene, assets, view, null, ownCar = true)
        drawRect(Color.White.copy(alpha = 0.07f), topLeft = bounds.topLeft, size = bounds.size)   // 거울 반사광
    }
    drawPath(path, Color(0xFF0E0F11), style = Stroke(width = (bounds.height * 0.025f).coerceIn(2f, 6f)))
}

/**
 * 내 차를 위에서 본 화면. 실제 서라운드 뷰처럼 내 차가 항상 위쪽을 향하게 돌림.
 * @param focusY 내 차를 화면 위에서 몇 % 높이에 둘지 (후진할 때는 위쪽에 둬서 차 뒤쪽을 더 넓게 보여줌)
 */
private fun DrawScope.drawSurround(scene: Scene, assets: TopAssets, area: Rect, viewM: Double, textMeasurer: TextMeasurer, focusY: Float = 0.5f) {
    drawRect(Backdrop, topLeft = area.topLeft, size = area.size)
    val scale = (area.width / viewM).toFloat()
    val c = scene.body.center
    val focus = Offset(area.center.x, area.top + area.height * focusY)
    val proj = Projection(scale, focus.x - c.x.toFloat() * scale, focus.y - c.y.toFloat() * scale)
    withTransform({ rotate(-Math.toDegrees(scene.pose.heading).toFloat() - 90f, pivot = focus) }) {
        drawTopScene(scene, assets, proj, textMeasurer)
    }
}

/** 후방 카메라 화면 아래쪽의 내 차 범퍼 */
private fun DrawScope.drawRearCamOverlay(area: Rect) {
    val bumper = Path().apply {
        moveTo(area.left, area.bottom)
        lineTo(area.left, area.bottom - area.height * 0.07f)
        quadraticTo(area.center.x, area.bottom - area.height * 0.15f, area.right, area.bottom - area.height * 0.07f)
        lineTo(area.right, area.bottom)
        close()
    }
    drawPath(bumper, Color(0xFF2A2C30))
    drawPath(bumper, Color.White.copy(alpha = 0.25f), style = Stroke(width = 3f))
}

// ───────── 실내 구성물 ─────────

private val Headliner = Color(0xFF1B1D21)
private val DashTopColor = Color(0xFF26292E)
private val Ambient = Color(0xFF3FA9F5)          // 대시보드 무드등
private val ClusterBg = Color(0xFF07080A)

/** 앞유리 아래쪽 선(= 보닛이 끝나는 곳)의 화면 y. 대시보드가 여기서 시작함 */
private fun dashboardTop(cam: PerspectiveCam, car: CarSpec, pose: CarPose, area: Rect): Float {
    val windshield = windshieldA(car)
    val ws = cam.toCam(local(pose, windshield, 0.0, 1.0))
    return (if (ws.x > NEAR) cam.project(ws).y else area.bottom - area.height * 0.3f)
        .coerceIn(area.top + area.height * 0.4f, area.bottom - area.height * 0.1f)
}

/** 보닛: 실제 차 크기대로 원근 투영해서 앞 범퍼가 어디쯤인지 감을 잡을 수 있게 함. 앞유리 아래 와이퍼 포함 */
private fun DrawScope.drawHood(cam: PerspectiveCam, car: CarSpec, pose: CarPose) {
    val front = car.lengthM - car.rearOverhangM - 0.05
    val windshield = windshieldA(car)
    val hw = car.widthM / 2
    val hoodPts = listOf(
        local(pose, front, -(hw - 0.2), 0.86), local(pose, front, hw - 0.2, 0.86),
        local(pose, windshield, hw - 0.12, 1.0), local(pose, windshield, -(hw - 0.12), 1.0),
    )
    poly3(cam, hoodPts, Hood)
    // 보닛 가운데 볼록한 면과 앞쪽 가장자리 반사광
    poly3(cam, listOf(local(pose, front, -0.25, 0.87), local(pose, front, 0.25, 0.87),
        local(pose, windshield, 0.35, 1.0), local(pose, windshield, -0.35, 1.0)), shade(Hood, 1.12))
    poly3(cam, listOf(hoodPts[0], hoodPts[1], local(pose, front - 0.12, hw - 0.22, 0.88), local(pose, front - 0.12, -(hw - 0.22), 0.88)), Color.White.copy(alpha = 0.25f))
    // 와이퍼 2개: 앞유리 아래에 비스듬히 누워 있음
    listOf(-0.62 to -0.02, 0.05 to 0.62).forEach { (b0, b1) ->
        val p = local(pose, windshield - 0.04, b0, 1.01)
        val q = local(pose, windshield - 0.12, b1, 1.0)
        poly3(cam, listOf(p, q, local(pose, windshield - 0.11, b1, 1.008), local(pose, windshield - 0.03, b0, 1.012)), Color(0xFF0E0F11))
    }
}

/** 앞유리 위쪽: 천장 끝과 운전석·조수석 선바이저 (고개를 돌리면 같이 밀려나도록 좌우로 넉넉하게 그림) */
private fun DrawScope.drawWindshieldFrame(area: Rect) {
    val w = area.width
    val h = area.height
    drawPath(Path().apply {
        moveTo(area.left - w, area.top); lineTo(area.right + w, area.top)
        lineTo(area.right + w, area.top + h * 0.12f)
        lineTo(area.right, area.top + h * 0.09f)
        quadraticTo(area.center.x, area.top + h * 0.035f, area.left, area.top + h * 0.1f)
        lineTo(area.left - w, area.top + h * 0.13f)
        close()
    }, Brush.verticalGradient(listOf(Color(0xFF101114), Headliner), startY = area.top, endY = area.top + h * 0.1f))
    // 선바이저 (접혀 있는 상태)
    drawRoundRect(Color(0xFF2A2D32), topLeft = Offset(area.left + w * 0.07f, area.top + h * 0.015f), size = Size(w * 0.24f, h * 0.075f), cornerRadius = CornerRadius(h * 0.03f))
    drawRoundRect(Color(0xFF2A2D32), topLeft = Offset(area.right - w * 0.27f, area.top + h * 0.01f), size = Size(w * 0.2f, h * 0.06f), cornerRadius = CornerRadius(h * 0.03f))
}

/**
 * 룸미러: 차 가운데 위(운전석이 왼쪽이라 화면에서는 오른쪽에 치우침).
 * 실제로 뒤쪽을 비춤 (뒤를 보는 카메라, 거울이라 좌우 반전) + 뒷유리 너머로 보이는 뒷좌석 머리받침.
 */
private fun DrawScope.drawRoomMirror(scene: Scene, assets: TopAssets, car: CarSpec, area: Rect) {
    val w = area.width
    val h = area.height
    val mirror = Rect(area.left + w * 0.55f, area.top + h * 0.05f, area.left + w * 0.86f, area.top + h * 0.18f)
    // 받침대
    drawLine(Color(0xFF16171A), Offset(mirror.center.x, area.top), Offset(mirror.center.x, mirror.top), strokeWidth = w * 0.012f)
    // 거울 테두리
    drawRoundRect(Color(0xFF111215), topLeft = mirror.topLeft - Offset(8f, 8f), size = Size(mirror.width + 16f, mirror.height + 16f), cornerRadius = CornerRadius(mirror.height * 0.5f))
    clipPath(Path().apply { addRoundRect(RoundRect(mirror, CornerRadius(mirror.height * 0.45f))) }) {
        val back = PerspectiveCam(local(scene.pose, eyeA(car) + 0.5, 0.0, eyeZ(car)), scene.pose.heading + Math.PI, pitchDeg = 4.0, vFovDeg = 26.0, area = mirror, mirror = true)
        drawPerspectiveScene(scene, assets, back, null)
        // 뒷유리 테두리와 뒷좌석 머리받침
        drawRect(Color(0xFF15161A), topLeft = mirror.topLeft, size = Size(mirror.width, mirror.height * 0.16f))
        drawRect(Color(0xFF15161A), topLeft = Offset(mirror.left, mirror.bottom - mirror.height * 0.2f), size = Size(mirror.width, mirror.height * 0.2f))
        listOf(0.3f, 0.7f).forEach { fx ->
            drawRoundRect(Color(0xFF1F2125), topLeft = Offset(mirror.left + mirror.width * fx - mirror.width * 0.07f, mirror.bottom - mirror.height * 0.42f),
                size = Size(mirror.width * 0.14f, mirror.height * 0.3f), cornerRadius = CornerRadius(mirror.height * 0.08f))
        }
        drawRect(Color.White.copy(alpha = 0.06f), topLeft = mirror.topLeft, size = mirror.size)   // 거울 반사광
    }
}

/** 대시보드: 위쪽 면, 무드등, 바느질 선, 송풍구 */
private fun DrawScope.drawDashboard(area: Rect, dashTop: Float) {
    val h = area.height
    val w = area.width
    val top = Path().apply {
        moveTo(area.left, area.bottom)
        lineTo(area.left, dashTop + h * 0.03f)
        quadraticTo(area.center.x, dashTop - h * 0.015f, area.right, dashTop + h * 0.04f)
        lineTo(area.right, area.bottom)
        close()
    }
    drawPath(top, Brush.verticalGradient(listOf(DashTopColor, Dash, Color(0xFF111215)), startY = dashTop - h * 0.02f, endY = area.bottom))
    // 무드등: 대시보드 아래쪽을 가로지르는 얇은 빛
    val ambientY = dashTop + h * 0.16f
    drawLine(Ambient.copy(alpha = 0.25f), Offset(area.left, ambientY + 6f), Offset(area.right, ambientY + 2f), strokeWidth = 14f)
    drawLine(Ambient.copy(alpha = 0.9f), Offset(area.left, ambientY + 6f), Offset(area.right, ambientY + 2f), strokeWidth = 3f)
    // 바느질 선
    val stitch = Path().apply {
        moveTo(area.left, dashTop + h * 0.06f)
        quadraticTo(area.center.x, dashTop + h * 0.02f, area.right, dashTop + h * 0.07f)
    }
    drawPath(stitch, Color.White.copy(alpha = 0.12f), style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
    // 송풍구: 운전석 왼쪽, 가운데
    fun vent(x: Float, width: Float) {
        val y = dashTop + h * 0.085f
        val vh = h * 0.05f
        drawRoundRect(Color(0xFF0B0C0E), topLeft = Offset(x, y), size = Size(width, vh), cornerRadius = CornerRadius(vh * 0.3f))
        for (i in 1..3) drawLine(Color(0xFF2E3136), Offset(x + 6f, y + vh * i / 4f), Offset(x + width - 6f, y + vh * i / 4f), strokeWidth = 3f)
    }
    vent(area.left + w * 0.04f, w * 0.12f)
    vent(area.left + w * 0.47f, w * 0.1f)
}

/**
 * 계기판: 핸들 너머로 보이는 디지털 화면. 속도, 방향(P·R·N·D 중 지금 앱에서 고른 전진/후진), 핸들 각도.
 * 다시 보기 중에는 기록에서 속도를 계산해서 보여줌.
 */
private fun DrawScope.drawCluster(scene: Scene, area: Rect, dashTop: Float, textMeasurer: TextMeasurer) {
    val w = area.width
    val h = area.height
    // 핸들 위로 보이도록 대시보드 위에 솟은 계기판 (위쪽 차양이 덮고 있음)
    val rect = Rect(area.center.x - w * 0.17f, dashTop - h * 0.085f, area.center.x + w * 0.17f, dashTop + h * 0.045f)
    drawPath(Path().apply {
        moveTo(rect.left - w * 0.025f, rect.bottom + h * 0.01f)
        lineTo(rect.left - w * 0.01f, rect.top - h * 0.01f)
        quadraticTo(rect.center.x, rect.top - h * 0.045f, rect.right + w * 0.01f, rect.top - h * 0.01f)
        lineTo(rect.right + w * 0.025f, rect.bottom + h * 0.01f)
        close()
    }, Brush.verticalGradient(listOf(Color(0xFF2C2F34), Color(0xFF16181B)), startY = rect.top - h * 0.04f, endY = rect.bottom))
    drawRoundRect(ClusterBg, topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(h * 0.025f))
    drawRoundRect(Color.White.copy(alpha = 0.1f), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(h * 0.025f), style = Stroke(width = 2f))

    val speedMs = scene.replayTime?.let { t ->
        val a = scene.src.frameAt(t - 0.2).pose
        val b = scene.src.frameAt(t).pose
        hypot(b.x - a.x, b.y - a.y) / 0.2
    } ?: kotlin.math.abs(scene.sim.speed)
    val text = { s: String, size: Float, color: Color, bold: Boolean ->
        textMeasurer.measure(s, TextStyle(color = color, fontSize = size.toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    }
    // 가운데: 속도
    val speed = text("${(speedMs * 3.6).toInt()}", h * 0.06f, Color.White, true)
    val unit = text("km/h", h * 0.022f, Color.White.copy(alpha = 0.6f), false)
    drawText(speed, topLeft = Offset(rect.center.x - speed.size.width / 2f, rect.top + h * 0.012f))
    drawText(unit, topLeft = Offset(rect.center.x - unit.size.width / 2f, rect.top + h * 0.012f + speed.size.height))
    // 왼쪽: P R N D (실제 기어는 P에 두고, 앱에서 고른 전진·후진을 D·R로 표시)
    val active = if (scene.direction == Direction.REVERSE) "R" else "D"
    var x = rect.left + w * 0.02f
    listOf("P", "R", "N", "D").forEach { g ->
        val on = g == active
        val l = text(g, h * 0.03f, if (on) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.3f), on)
        drawText(l, topLeft = Offset(x, rect.center.y - l.size.height / 2f))
        x += l.size.width + w * 0.012f
    }
    // 오른쪽: 핸들 각도
    val steer = (scene.wheelAngleDeg * scene.sim.car.steeringRatio).toInt()
    val dirText = when {
        steer > 10 -> "오른쪽 ${steer}°"
        steer < -10 -> "왼쪽 ${-steer}°"
        else -> "가운데"
    }
    val s1 = text("핸들", h * 0.018f, Color.White.copy(alpha = 0.55f), false)
    val s2 = text(dirText, h * 0.024f, Color.White, true)
    drawText(s1, topLeft = Offset(rect.right - w * 0.02f - s2.size.width, rect.center.y - s2.size.height / 2f - s1.size.height))
    drawText(s2, topLeft = Offset(rect.right - w * 0.02f - s2.size.width, rect.center.y - s2.size.height / 2f))
}

/**
 * 핸들: 화면 아래 가운데, 핸들 각도만큼 돌아감 (오른쪽으로 돌리면 시계 방향).
 * 가운데 에어백 패드, 양쪽 살의 버튼, 12시 표시.
 */
private fun DrawScope.drawSteeringWheel(area: Rect, dashTop: Float, steeringDeg: Float) {
    val h = area.height
    val r = ((area.bottom - dashTop) * 0.95f).coerceAtMost(h * 0.34f)
    val c = Offset(area.center.x, area.bottom + r * 0.12f)
    // 핸들 기둥
    drawRoundRect(Color(0xFF15161A), topLeft = Offset(c.x - r * 0.18f, c.y - r * 0.2f), size = Size(r * 0.36f, r * 0.5f), cornerRadius = CornerRadius(r * 0.08f))
    rotate(steeringDeg, pivot = c) {
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = r + r * 0.09f, center = c + Offset(0f, 6f), style = Stroke(width = r * 0.16f))
        // 테두리: 위쪽이 밝은 그라데이션
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFF3B3F45), WheelRim, Color(0xFF1B1D21)), startY = c.y - r, endY = c.y + r),
            radius = r, center = c, style = Stroke(width = r * 0.16f),
        )
        drawCircle(Color.White.copy(alpha = 0.07f), radius = r - r * 0.055f, center = c, style = Stroke(width = r * 0.025f))
        // 살 3개 (왼쪽·오른쪽·아래)
        listOf(180f, 0f, 90f).forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val end = c + Offset((cos(rad) * r * 0.93).toFloat(), (sin(rad) * r * 0.93).toFloat())
            drawLine(WheelRim, c, end, strokeWidth = r * 0.15f, cap = StrokeCap.Round)
        }
        // 양쪽 살의 버튼 (볼륨·크루즈 등)
        listOf(-1f, 1f).forEach { side ->
            for (i in 0..1) {
                drawCircle(Color(0xFF3A3E44), radius = r * 0.035f, center = c + Offset(side * r * (0.42f + i * 0.13f), 0f))
            }
        }
        // 가운데 에어백 패드
        drawRoundRect(Color(0xFF2F3237), topLeft = Offset(c.x - r * 0.3f, c.y - r * 0.24f), size = Size(r * 0.6f, r * 0.42f), cornerRadius = CornerRadius(r * 0.14f))
        drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(c.x - r * 0.3f, c.y - r * 0.24f), size = Size(r * 0.6f, r * 0.42f), cornerRadius = CornerRadius(r * 0.14f), style = Stroke(width = 2f))
        // 12시 표시: 핸들이 얼마나 돌았는지 바로 보이게
        drawRoundRect(Color(0xFFE0E0E0), topLeft = Offset(c.x - r * 0.05f, c.y - r - r * 0.08f), size = Size(r * 0.1f, r * 0.16f), cornerRadius = CornerRadius(r * 0.03f))
    }
}
