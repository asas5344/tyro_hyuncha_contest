package com.team.hyuncha.render3d

import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.Guidance
import com.team.hyuncha.sim.Vec2
import kotlin.math.hypot

/** Local-space guide triangles. Uses the simulator's prediction instead of a second steering formula. */
internal object GuideGeometry {
    fun vertices(car: CarSpec,wheelAngle: Double,direction: Direction): FloatArray {
        val points=Guidance.predictPath(car,CarPose(0.0,0.0,0.0),wheelAngle,direction)
        val data=ArrayList<Float>()
        fun strip(a: Vec2,b: Vec2,distance: Double) {
            val len=hypot(b.x-a.x,b.y-a.y)
            if(len<1e-6) return
            val offset=Vec2(-(b.y-a.y)/len*.045,(b.x-a.x)/len*.045)
            val corners=listOf(a+offset,b+offset,b-offset,a-offset)
            val color=when { distance<1.0 -> floatArrayOf(1f,.1f,.06f,1f);distance<2.0 -> floatArrayOf(1f,.68f,.08f,1f);else -> floatArrayOf(.12f,.85f,.4f,1f) }
            for(i in intArrayOf(0,1,2,0,2,3)) {
                data.addAll(listOf(corners[i].x.toFloat(),.028f,corners[i].y.toFloat()))
                data.addAll(color.toList())
            }
        }
        points.zipWithNext().forEach { (a,b) -> strip(a.left,b.left,a.distanceM);strip(a.right,b.right,a.distanceM) }
        listOf(1.0,2.0,3.0).forEach { mark ->
            val p=points.minBy { kotlin.math.abs(it.distanceM-mark) }
            strip(p.left,p.right,mark-.01)
        }
        return data.toFloatArray()
    }
}
