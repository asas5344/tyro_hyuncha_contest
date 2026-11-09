package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.LookSide
import com.team.hyuncha.sim.CarPose
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

class CameraRigTest {
    private val car=CarPresets.GRANDEUR
    @Test fun mirrorHousingAndGlassFaceTheDriverOnBothSides() {
        for(vehicle in CarPresets.all) for(side in listOf(-1,1)) {
            val eye=SceneCoordinates.eye(vehicle,CarPose(0.0,0.0,0.0))
            val point=CameraRig.mirrorPoint(vehicle,side)
            val mount=CameraRig.mirrorMount(vehicle,side)
            val dx=eye.x-point.x;val dz=eye.z-point.z;val distance=hypot(dx,dz)
            assertEquals(dx/distance,-mount[0].toDouble(),1e-6)
            assertEquals(dz/distance,-mount[2].toDouble(),1e-6)
            assertEquals(point.x,mount[12].toDouble(),1e-6)
            assertEquals(point.y,mount[13].toDouble(),1e-6)
            assertEquals(point.z,mount[14].toDouble(),1e-6)
        }
    }
    @Test fun passengerMirrorTurnsFurtherTowardDriverAndBothKeepLandscapeGlass() {
        for(vehicle in CarPresets.all) {
            assertTrue(kotlin.math.abs(CameraRig.mirrorYaw(vehicle,1))>kotlin.math.abs(CameraRig.mirrorYaw(vehicle,-1)))
        }
        assertEquals(16.0/9,CameraRig.MIRROR_WIDTH_M/CameraRig.MIRROR_HEIGHT_M,1e-9)
    }
    @Test fun forwardViewStaysAlignedWithEveryVehicleAtDiagonalAndCardinalHeadings() {
        for(vehicle in CarPresets.all) for(heading in listOf(0.0,PI/4,PI/2,-PI/2)) {
            val pose=CarPose(4.0,7.0,heading)
            val head=CameraRig.driverHead(vehicle,LookSide.FRONT)
            val aim=CameraRig.aim(SceneCoordinates.eye(vehicle,pose),heading+Math.toRadians(head.yawDeg),head.pitchDeg)
            val dx=aim.target.x-aim.eye.x;val dz=aim.target.z-aim.eye.z
            assertEquals(0.0,-dx*sin(heading)+dz*cos(heading),1e-9)
            assertTrue(dx*cos(heading)+dz*sin(heading)>0)
            assertTrue((aim.eye.y-aim.target.y)/hypot(dx,dz)<.04)
            assertEquals(Point3(0.0,1.0,0.0),aim.up)
        }
    }
    @Test fun frontHeadLooksAheadAndEachSideTargetsItsMirror() {
        assertEquals(0.0,CameraRig.driverHead(car,LookSide.FRONT).yawDeg,0.0)
        assertEquals(2.0,CameraRig.driverHead(car,LookSide.FRONT).pitchDeg,0.0)
        assertEquals(56.0,CameraRig.driverHead(car,LookSide.FRONT).fovDeg,0.0)
        val left=CameraRig.driverHead(car,LookSide.LEFT)
        val right=CameraRig.driverHead(car,LookSide.RIGHT)
        assertTrue(left.yawDeg<0);assertTrue(right.yawDeg>0)
        assertTrue(left.fovDeg<58);assertTrue(right.fovDeg<58)
    }
    @Test fun sideMirrorCamerasAreOutsideBodyAndLookRearwardOutward() {
        val pose=CarPose(0.0,0.0,0.0)
        for((kind,side) in listOf(FeedKind.LEFT_MIRROR to -1,FeedKind.RIGHT_MIRROR to 1)) {
            val a=CameraRig.feed(car,pose,kind)
            assertTrue(a.eye.z*side>car.widthM/2)
            assertTrue(a.target.x<a.eye.x)
            assertTrue((a.target.z-a.eye.z)*side>0)
        }
    }
    @Test fun rearCameraIsOutsideRearBumperAndPointsDownAndBack() {
        val a=CameraRig.feed(car,CarPose(0.0,0.0,0.0),FeedKind.REAR)
        assertTrue(a.eye.x < -car.rearOverhangM)
        assertTrue(a.target.x<a.eye.x);assertTrue(a.target.y<a.eye.y)
    }
    @Test fun everyCameraRotatesAndTranslatesWithRearAxle() {
        for(kind in FeedKind.entries.filter { it!=FeedKind.SURROUND }) {
            val a=CameraRig.feed(car,CarPose(0.0,0.0,0.0),kind)
            val b=CameraRig.feed(car,CarPose(4.0,7.0,PI/2),kind)
            assertEquals(4.0-a.eye.z,b.eye.x,1e-9)
            assertEquals(7.0+a.eye.x,b.eye.z,1e-9)
            assertEquals(a.eye.y,b.eye.y,0.0)
            assertEquals(4.0-a.target.z,b.target.x,1e-9)
            assertEquals(7.0+a.target.x,b.target.z,1e-9)
        }
    }
    @Test fun mirrorUpdatesIncludeScreensPartlyVisibleInWideDriverViews() {
        val front=CameraRig.driverHead(car,LookSide.FRONT)
        assertTrue(CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,-1),front,16.0/9,.12))
        assertFalse(CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,1),front,16.0/9,.12))
        val right=CameraRig.driverHead(car,LookSide.RIGHT)
        assertTrue(CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,1),right,1.5,.12))
        assertFalse(CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,-1),right,1.5,.12))
    }
    @Test fun surroundCameraHasVehicleForwardAtScreenTop() {
        val a=CameraRig.feed(car,CarPose(4.0,7.0,PI/2),FeedKind.SURROUND)
        assertEquals(a.eye.x,a.target.x,0.0);assertEquals(a.eye.z,a.target.z,0.0)
        assertEquals(10.0,a.eye.y,0.0);assertEquals(0.0,a.target.y,0.0)
        assertEquals(0.0,a.up.x,1e-9);assertEquals(1.0,a.up.z,1e-9)
    }
}
