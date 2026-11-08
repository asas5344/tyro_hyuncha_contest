package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.data.CarType
import com.team.hyuncha.sim.CarPose
import org.junit.Assert.*
import org.junit.Test

class VehicleVisualsTest {
    @Test fun everyPresetHasItsOwnModelAndExactCollisionDimensions() {
        val assets=mutableSetOf<String>()
        for(car in CarPresets.all) {
            assertTrue(VehicleVisuals.supports(car))
            val p=VehicleVisuals.forCar(car)
            assertTrue(assets.add(p.asset))
            assertEquals(car.lengthM,p.lengthM,0.0);assertEquals(car.widthM,p.widthM,0.0)
            assertEquals(car.wheelBaseM,p.wheelBaseM,0.0);assertEquals(car.rearOverhangM,p.rearOverhangM,0.0)
            assertFalse(VehicleVisuals.supports(car.copy(lengthM=car.lengthM+.1)))
        }
    }
    @Test fun eyeAndMirrorHeightFollowEachVehicleAndRemainInsideRoof() {
        val sedan=VehicleVisuals.forCar(CarPresets.GRANDEUR)
        for(car in CarPresets.all) {
            val p=VehicleVisuals.forCar(car)
            val eye=SceneCoordinates.eye(car,CarPose(0.0,0.0,0.0))
            assertEquals(p.eyeForwardM,eye.x,0.0);assertEquals(p.eyeHeightM,eye.y,0.0)
            assertTrue(p.eyeHeightM<p.roofHeightM-.15)
            assertTrue(p.mirrorRightM>car.widthM/2)
            assertTrue(p.monitorForwardM>p.eyeForwardM+.4)
            if(car.type==CarType.SUV) {
                assertTrue(p.roofHeightM>sedan.roofHeightM+.15)
                assertTrue(p.eyeHeightM>sedan.eyeHeightM+.15)
            }
        }
    }
}
