package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import org.junit.Assert.*
import org.junit.Test

class GuideGeometryTest {
    @Test fun straightGuideStartsAtTheCorrectBumperAndStaysOnTheGround() {
        val car=CarPresets.GRANDEUR
        for(direction in Direction.entries) {
            val data=GuideGeometry.vertices(car,0.0,direction)
            val expected=if(direction==Direction.FORWARD) car.lengthM-car.rearOverhangM else -car.rearOverhangM
            assertEquals(expected,data[0].toDouble(),1e-5)
            for(i in data.indices step 7) assertEquals(.028,data[i+1].toDouble(),1e-6)
            val xs=(data.indices step 7).map { data[it].toDouble() }
            if(direction==Direction.FORWARD) assertTrue(xs.max()>expected+3.4) else assertTrue(xs.min()<expected-3.4)
        }
    }
    @Test fun allCarsAndSteeringLimitsFitOneFixedBufferWithoutInvalidTriangles() {
        for(car in CarPresets.all) for(direction in Direction.entries) for(angle in listOf(-car.maxWheelAngleDeg,0.0,car.maxWheelAngleDeg)) {
            val data=GuideGeometry.vertices(car,angle,direction)
            assertTrue(data.size/7 in 1..600)
            assertEquals(0,data.size%(7*3))
            assertTrue(data.all { it.isFinite() })
        }
    }
}
