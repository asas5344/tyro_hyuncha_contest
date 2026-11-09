package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.sim.CarPose
import kotlin.math.cos
import kotlin.math.sin

/** Right-handed glTF world: X = map x, Y = height, Z = map y. Metres, rear-axle origin. */
internal data class Point3(val x: Double, val y: Double, val z: Double)
internal object SceneCoordinates {
    fun local(pose: CarPose, forward: Double, right: Double, height: Double) = Point3(
        pose.x + cos(pose.heading) * forward - sin(pose.heading) * right,
        height,
        pose.y + sin(pose.heading) * forward + cos(pose.heading) * right,
    )
    /** Column-major transform. Positive simulator heading turns toward map +y / glTF +Z. */
    fun transform(pose: CarPose, sx: Double = 1.0, sy: Double = 1.0, sz: Double = 1.0): FloatArray {
        val c = cos(pose.heading); val s = sin(pose.heading)
        return floatArrayOf((c*sx).toFloat(),0f,(s*sx).toFloat(),0f, 0f,sy.toFloat(),0f,0f,
            (-s*sz).toFloat(),0f,(c*sz).toFloat(),0f,pose.x.toFloat(),0f,pose.y.toFloat(),1f)
    }
    fun eye(car: CarSpec, pose: CarPose): Point3 {
        val visual=VehicleVisuals.forCar(car)
        return local(pose,visual.eyeForwardM,-.37,visual.eyeHeightM)
    }
}
