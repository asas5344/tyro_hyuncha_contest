package com.team.hyuncha.render3d

import com.google.android.filament.*
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.Direction
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fixed native buffers, rebuilt only when wheel angle or driving direction changes. */
internal class DrivingGuides(private val engine: Engine,scene: Scene,material: Material,private val car: CarSpec) : AutoCloseable {
    companion object { private const val CAPACITY=600 }
    val entity=EntityManager.get().create()
    private val vertices=VertexBuffer.Builder().bufferCount(1).vertexCount(CAPACITY)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,28)
        .attribute(VertexBuffer.VertexAttribute.COLOR,0,VertexBuffer.AttributeType.FLOAT4,12,28).build(engine)
    private val indices=IndexBuffer.Builder().indexCount(CAPACITY).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    private val instance=material.createInstance()
    private var angle=Double.NaN
    private var direction: Direction?=null
    init {
        indices.setBuffer(engine,ByteBuffer.allocateDirect(CAPACITY*2).order(ByteOrder.nativeOrder()).apply { asShortBuffer().put(ShortArray(CAPACITY) { it.toShort() }) })
        vertices.setBufferAt(engine,0,ByteBuffer.allocateDirect(CAPACITY*28).order(ByteOrder.nativeOrder()))
        RenderableManager.Builder(1).boundingBox(Box(0f,0f,0f,10f,.1f,10f))
            .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vertices,indices,0,6).material(0,instance)
            .castShadows(false).receiveShadows(false).culling(false).build(engine,entity)
        if(!engine.transformManager.hasComponent(entity)) engine.transformManager.create(entity)
        scene.addEntity(entity)
    }
    fun update(wheelAngle: Double,direction: Direction,transform: FloatArray,visible: Boolean) {
        val rm=engine.renderableManager;val ri=rm.getInstance(entity)
        rm.setLayerMask(ri,0xff,if(visible) 1 else 0)
        engine.transformManager.setTransform(engine.transformManager.getInstance(entity),transform)
        if(this.direction==direction && kotlin.math.abs(angle-wheelAngle)<.001) return
        val data=GuideGeometry.vertices(car,wheelAngle,direction)
        check(data.size/7<=CAPACITY)
        val buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(data) }
        vertices.setBufferAt(engine,0,buffer)
        rm.setGeometryAt(ri,0,RenderableManager.PrimitiveType.TRIANGLES,vertices,indices,0,data.size/7)
        angle=wheelAngle;this.direction=direction
    }
    override fun close() {
        engine.destroyEntity(entity);EntityManager.get().destroy(entity)
        engine.destroyVertexBuffer(vertices);engine.destroyIndexBuffer(indices);engine.destroyMaterialInstance(instance)
    }
}
