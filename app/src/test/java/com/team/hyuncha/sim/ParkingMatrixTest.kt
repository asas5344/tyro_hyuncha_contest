package com.team.hyuncha.sim

import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.DrivingInput
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Check the full car/mode matrix against a reproducible random parking layout. */
class ParkingMatrixTest {
    private class Input(
        override var steeringDeg: Double = 0.0,
        override var direction: Direction = Direction.FORWARD,
        override var brakePressed: Boolean = true,
    ) : DrivingInput

    @Test fun everyCarAndModeSupportsParkingScoreReplayCollisionAndRestart() {
        for(car in CarPresets.all) for(mode in ParkingMode.entries) {
            val label="${car.name} ${mode.name}"
            val map=ParkingMaps.random(mode,Random(7))
            val plan=Coach.solve(car,map)
            assertNotNull("$label model drive",plan)
            val run=plan!!.run
            assertEquals(label,SimStatus.PARKED,run.status)
            assertTrue(label,run.isInsideSlot)
            val score=Scoring.score(run,run)
            assertNotNull(label,score)
            assertTrue(label,score!!.total in 0..1000)
            assertEquals(label,1000,score.items.sumOf { it.max })
            assertTrue(label,run.history.any { it.speedMps!=0.0 })
            assertEquals(label,run.history.first(),run.frameAt(0.0))
            assertEquals(label,run.history.last(),run.frameAt(run.elapsedSec))
            run.reset()
            assertEquals(label,map.start,run.pose)
            assertEquals(label,SimStatus.DRIVING,run.status)
            assertNull(label,run.result)
            assertEquals(label,0.0,run.movingSec,0.0)
            assertEquals(label,1,run.history.size)
            assertTrue(label,run.events.isEmpty())
            val input=Input()
            repeat(60) { run.step(input,1.0/60) }
            assertEquals("$label brake holds",map.start,run.pose)
            input.brakePressed=false
            repeat(12_000) { run.step(input,1.0/60) }
            assertEquals("$label collision",SimStatus.COLLIDED,run.status)
            assertNotNull(label,run.collision)
            assertNull(label,Scoring.score(run,plan.run))
            println("PASS $label: random map, model parking, score, replay, restart, brake, collision")
        }
    }
}
