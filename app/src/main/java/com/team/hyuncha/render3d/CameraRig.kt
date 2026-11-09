package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.LookSide
import com.team.hyuncha.sim.CarPose
import kotlin.math.*

internal data class CameraAim(val eye: Point3,val target: Point3,val up: Point3=Point3(0.0,1.0,0.0))
internal data class DriverHead(val yawDeg: Double,val pitchDeg: Double,val fovDeg: Double)
internal enum class FeedKind { LEFT_MIRROR, RIGHT_MIRROR, ROOM_MIRROR, REAR, SURROUND }

/** Camera placement is independent of steering: only body heading and explicit head input rotate it. */
internal object CameraRig {
    const val MIRROR_WIDTH_M=.25
    const val MIRROR_HEIGHT_M=MIRROR_WIDTH_M*9/16
    fun mirrorYaw(car: CarSpec,side: Int): Double {
        val p=mirrorPoint(car,side);val eye=SceneCoordinates.eye(car,CarPose(0.0,0.0,0.0))
        return atan2(p.z-eye.z,p.x-eye.x)
    }
    fun mirrorMount(car: CarSpec,side: Int): FloatArray {
        val p=mirrorPoint(car,side)
        return SceneCoordinates.transform(CarPose(p.x,p.z,mirrorYaw(car,side))).also { it[13]=p.y.toFloat() }
    }
    fun aim(eye: Point3,heading: Double,pitchDeg: Double): CameraAim {
        val p=Math.toRadians(pitchDeg)
        return CameraAim(eye,Point3(eye.x+cos(heading)*cos(p),eye.y-sin(p),eye.z+sin(heading)*cos(p)))
    }
    fun driverHead(car: CarSpec,side: LookSide): DriverHead {
        if(side==LookSide.FRONT) return DriverHead(0.0,2.0,56.0)
        val eye=SceneCoordinates.eye(car,CarPose(0.0,0.0,0.0))
        val mirror=mirrorPoint(car,side.step)
        val dx=mirror.x-eye.x;val dz=mirror.z-eye.z;val dy=mirror.y-eye.y
        val flat=hypot(dx,dz)
        // Keep the original ~1/3-screen mirror focus, using the actual 3D glass height.
        val fov=Math.toDegrees(2*atan(MIRROR_HEIGHT_M/(.64*hypot(flat,dy)))).coerceIn(10.0,56.0)
        return DriverHead(Math.toDegrees(atan2(dz,dx)),Math.toDegrees(atan2(-dy,flat)),fov)
    }
    fun inDriverView(car: CarSpec,point: Point3,head: DriverHead,aspect: Double,radius: Double): Boolean {
        val visual=VehicleVisuals.forCar(car)
        val dx=point.x-visual.eyeForwardM;val dz=point.z+.37;val dy=point.y-visual.eyeHeightM
        val yaw=Math.toRadians(head.yawDeg);val pitch=Math.toRadians(head.pitchDeg)
        val forward=dx*cos(yaw)+dz*sin(yaw)
        val right=-dx*sin(yaw)+dz*cos(yaw)
        val depth=forward*cos(pitch)-dy*sin(pitch)
        val up=forward*sin(pitch)+dy*cos(pitch)
        val tanVertical=tan(Math.toRadians(head.fovDeg/2))
        return depth+radius>0 && abs(right)<=depth*tanVertical*aspect+radius && abs(up)<=depth*tanVertical+radius
    }
    fun mirrorPoint(car: CarSpec,side: Int): Point3 {
        val visual=VehicleVisuals.forCar(car)
        return Point3(visual.mirrorForwardM-.18,visual.mirrorHeightM+.08,side*visual.mirrorRightM)
    }
    fun feed(car: CarSpec,pose: CarPose,kind: FeedKind): CameraAim = when(kind) {
        FeedKind.LEFT_MIRROR, FeedKind.RIGHT_MIRROR -> {
            val side=if(kind==FeedKind.LEFT_MIRROR) -1 else 1
            val point=mirrorPoint(car,side)
            val eye=SceneCoordinates.local(pose,point.x,point.z,point.y)
            // Convex-mirror framing: a narrow strip of own body, mostly the adjacent rear area.
            aim(eye,pose.heading+PI-side*Math.toRadians(22.0),if(side<0) 14.0 else 18.0)
        }
        FeedKind.ROOM_MIRROR -> {
            val visual=VehicleVisuals.forCar(car)
            aim(SceneCoordinates.local(pose,visual.eyeForwardM+.78,-.10,visual.eyeHeightM+.17),pose.heading+PI,4.0)
        }
        FeedKind.REAR -> aim(SceneCoordinates.local(pose,-car.rearOverhangM-.06,0.0,VehicleVisuals.forCar(car).rearCameraHeightM),pose.heading+PI,34.0)
        FeedKind.SURROUND -> {
            val target=SceneCoordinates.local(pose,car.lengthM/2-car.rearOverhangM,0.0,0.0)
            CameraAim(target.copy(y=10.0),target,Point3(cos(pose.heading),0.0,sin(pose.heading)))
        }
    }
}
