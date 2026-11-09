package com.team.hyuncha.render3d

import com.team.hyuncha.sim.OrientedRect
import com.team.hyuncha.sim.ParkingMap
import com.team.hyuncha.sim.Vec2
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

/** Small glTF writer for map-derived geometry. Batches all faces of each material into one mesh. */
internal class ParkingMesh {
    private val positions = List(6) { ArrayList<Float>() }
    private val colors = listOf(
        floatArrayOf(.19f,.215f,.24f,1f), floatArrayOf(.78f,.81f,.83f,1f),
        floatArrayOf(.08f,.7f,.35f,1f), floatArrayOf(.42f,.47f,.53f,1f),
        floatArrayOf(.82f,.58f,.12f,1f), floatArrayOf(.85f,.93f,1f,1f),
    )
    fun quad(points: List<Point3>, material: Int) {
        for (i in intArrayOf(0,1,2,0,2,3)) with(points[i]) {
            positions[material].addAll(listOf(x.toFloat(), y.toFloat(), z.toFloat()))
        }
    }
    fun flat(rect: OrientedRect, height: Double, material: Int) = quad(rect.corners().map { Point3(it.x,height,it.y) }, material)
    fun strip(a: Vec2, b: Vec2, width: Double, height: Double, material: Int) {
        val len = hypot(b.x-a.x,b.y-a.y)
        if (len < 1e-6) return
        val offset = Vec2(-(b.y-a.y)/len*width/2,(b.x-a.x)/len*width/2)
        quad(listOf(a+offset,b+offset,b-offset,a-offset).map { Point3(it.x,height,it.y) },material)
    }
    fun box(rect: OrientedRect, bottom: Double, top: Double, material: Int) {
        flat(rect,top,material)
        val p = rect.corners()
        for (i in 0..3) {
            val a=p[i]; val b=p[(i+1)%4]
            quad(listOf(Point3(a.x,bottom,a.y),Point3(b.x,bottom,b.y),Point3(b.x,top,b.y),Point3(a.x,top,a.y)),material)
        }
    }
    fun build(): ByteBuffer {
        val bin = ByteBuffer.allocate(positions.sumOf { it.size }*4).order(ByteOrder.LITTLE_ENDIAN)
        val views=JSONArray(); val accessors=JSONArray(); val primitives=JSONArray()
        positions.forEachIndexed { material, p ->
            if (p.isEmpty()) return@forEachIndexed
            val offset=bin.position(); p.forEach { bin.putFloat(it) }
            val index=views.length()
            views.put(JSONObject().put("buffer",0).put("byteOffset",offset).put("byteLength",p.size*4))
            val min=JSONArray(); val max=JSONArray()
            for (axis in 0..2) {
                val v=(axis until p.size step 3).map { p[it] }
                min.put(v.min().toDouble()); max.put(v.max().toDouble())
            }
            accessors.put(JSONObject().put("bufferView",index).put("componentType",5126).put("count",p.size/3).put("type","VEC3").put("min",min).put("max",max))
            primitives.put(JSONObject().put("attributes",JSONObject().put("POSITION",index)).put("material",material))
        }
        val materials=JSONArray()
        colors.forEach { color -> materials.put(JSONObject().put("pbrMetallicRoughness",JSONObject().put("baseColorFactor",JSONArray(color.toList())).put("roughnessFactor",1))
            .put("doubleSided",true).put("extensions",JSONObject().put("KHR_materials_unlit",JSONObject()))) }
        val root=JSONObject().put("asset",JSONObject().put("version","2.0")).put("scene",0)
            .put("extensionsUsed",JSONArray(listOf("KHR_materials_unlit")))
            .put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("nodes",JSONArray().put(JSONObject().put("mesh",0)))
            .put("meshes",JSONArray().put(JSONObject().put("primitives",primitives)))
            .put("materials",materials).put("buffers",JSONArray().put(JSONObject().put("byteLength",bin.position())))
            .put("bufferViews",views).put("accessors",accessors)
        val json=root.toString().toByteArray(); val padded=(json.size+3)/4*4
        return ByteBuffer.allocateDirect(28+padded+bin.position()).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546c67);putInt(2);putInt(capacity());putInt(padded);putInt(0x4e4f534a);put(json)
            repeat(padded-json.size) { put(32.toByte()) }
            putInt(bin.position());putInt(0x004e4942);put(bin.array(),0,bin.position());flip()
        }
    }
    companion object {
        fun garage(map: ParkingMap): ByteBuffer = ParkingMesh().apply {
            flat(OrientedRect(Vec2(map.widthM/2,map.heightM/2),map.widthM/2,map.heightM/2,0.0),0.0,0)
            map.lines.forEach { (a,b) -> strip(a,b,.095,.012,1) }
            flat(map.slot,.008,2)
            val corners=map.slot.corners()
            for (i in corners.indices) strip(corners[i],corners[(i+1)%4],.1,.016,2)
            map.walls.forEach {
                box(it,0.0,2.8,3)
                box(it,0.0,.2,4)
                box(it,1.1,1.22,4)
            }
            // 하드 모드 "기둥 옆 자리"의 기둥 (부딪히면 실패). 벽과 같은 콘크리트 + 띠
            map.pillars.forEach {
                box(it,0.0,2.8,3)
                box(it,0.0,.2,4)
                box(it,1.1,1.22,4)
            }
            // Overhead fixtures add scale without introducing non-collidable pillars.
            flat(OrientedRect(Vec2(map.widthM/2,map.heightM/2),map.widthM/2,map.heightM/2,0.0),2.85,0)
            for (x in 2 until map.widthM.toInt() step 4) {
                box(OrientedRect(Vec2(x.toDouble(),map.heightM*.3),.9,.08,0.0),2.77,2.8,5)
            }
        }.build()
    }
}
