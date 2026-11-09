package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.ParkingMaps
import com.team.hyuncha.sim.ParkingSimulator
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class SceneCoordinatesTest {
    private fun apply(m: FloatArray,x: Double,z: Double) = Point3(m[0]*x+m[8]*z+m[12],m[1]*x+m[9]*z+m[13],m[2]*x+m[10]*z+m[14])
    @Test fun rearAxleRemainsTheOriginAtEveryHeading() {
        for(h in listOf(0.0,PI/2,PI,-PI/2,.63)) {
            val pose=CarPose(4.0,7.0,h)
            val p=apply(SceneCoordinates.transform(pose),0.0,0.0)
            assertEquals(4.0,p.x,1e-6);assertEquals(0.0,p.y,1e-6);assertEquals(7.0,p.z,1e-6)
        }
    }
    @Test fun positiveQuarterTurnFacesPositiveMapY() {
        val p=apply(SceneCoordinates.transform(CarPose(4.0,7.0,PI/2)),1.0,0.0)
        assertEquals(4.0,p.x,1e-6);assertEquals(8.0,p.z,1e-6)
    }
    @Test fun renderedBodyCornersMatchCollisionCornersInAllDirections() {
        val car=CarPresets.GRANDEUR
        val sim=ParkingSimulator(car,ParkingMaps.perpendicular())
        for(h in listOf(0.0,PI/2,PI,-PI/2,.63)) {
            val pose=CarPose(4.0,7.0,h)
            val m=SceneCoordinates.transform(pose)
            val actual=listOf(car.lengthM-car.rearOverhangM to -car.widthM/2,car.lengthM-car.rearOverhangM to car.widthM/2,-car.rearOverhangM to car.widthM/2,-car.rearOverhangM to -car.widthM/2).map { (x,z)->apply(m,x,z) }
            sim.bodyRect(pose).corners().zip(actual).forEach { (expected,p)->
                assertEquals(expected.x,p.x,1e-6);assertEquals(expected.y,p.z,1e-6)
            }
        }
    }
    @Test fun leftHandDriverEyeRotatesWithBody() {
        val p=SceneCoordinates.eye(CarPresets.GRANDEUR,CarPose(4.0,7.0,PI/2))
        assertEquals(4.37,p.x,1e-6);assertEquals(8.16575,p.z,1e-6);assertEquals(1.18,p.y,1e-6)
    }
}
