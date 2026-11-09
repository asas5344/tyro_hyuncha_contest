package com.team.hyuncha.render3d

import com.google.android.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Rear-facing local-space screen. Surface layer 2 is excluded from all camera feeds. */
internal class TexturedPanel(
    private val engine: Engine,scene: Scene,material: Material,texture: Texture,
    center: Point3,width: Double,height: Double,visible: Boolean=true,flipV: Boolean=false,
    renderTarget: Boolean=false,rounded: Boolean=false,
    surfaceYaw: Double=0.0,
) : AutoCloseable {
    val entity=EntityManager.get().create()
    private val instance=material.createInstance().apply {
        setParameter("cameraFeed",texture,TextureSampler(TextureSampler.MinFilter.LINEAR,TextureSampler.MagFilter.LINEAR,TextureSampler.WrapMode.CLAMP_TO_EDGE))
        setParameter("renderTarget",renderTarget)
        setParameter("rounded",rounded)
    }
    private val vertices=VertexBuffer.Builder().bufferCount(1).vertexCount(4)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,20)
        .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,12,20).build(engine)
    private val indices=IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    init {
        val a=width/2;val b=height/2
        val bottomV=if(flipV) 1f else 0f;val topV=1f-bottomV
        val sx=-kotlin.math.sin(surfaceYaw);val sz=kotlin.math.cos(surfaceYaw)
        val data=floatArrayOf(
            (center.x-sx*a).toFloat(),(center.y-b).toFloat(),(center.z-sz*a).toFloat(),0f,bottomV,
            (center.x+sx*a).toFloat(),(center.y-b).toFloat(),(center.z+sz*a).toFloat(),1f,bottomV,
            (center.x+sx*a).toFloat(),(center.y+b).toFloat(),(center.z+sz*a).toFloat(),1f,topV,
            (center.x-sx*a).toFloat(),(center.y+b).toFloat(),(center.z-sz*a).toFloat(),0f,topV,
        )
        vertices.setBufferAt(engine,0,ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(data) })
        indices.setBuffer(engine,ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).apply { asShortBuffer().put(shortArrayOf(0,1,2,0,2,3)) })
        RenderableManager.Builder(1).boundingBox(Box(center.x.toFloat(),center.y.toFloat(),center.z.toFloat(),(kotlin.math.abs(sx)*a+.015).toFloat(),b.toFloat(),(kotlin.math.abs(sz)*a+.015).toFloat()))
            .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vertices,indices).material(0,instance)
            .castShadows(false).receiveShadows(false).culling(false).layerMask(0xff,if(visible) 2 else 0).build(engine,entity)
        if(!engine.transformManager.hasComponent(entity)) engine.transformManager.create(entity)
        scene.addEntity(entity)
    }
    fun setVisible(visible: Boolean) = engine.renderableManager.setLayerMask(engine.renderableManager.getInstance(entity),0xff,if(visible) 2 else 0)
    fun setBodyTransform(transform: FloatArray) = engine.transformManager.setTransform(engine.transformManager.getInstance(entity),transform)
    override fun close() {
        engine.destroyEntity(entity);EntityManager.get().destroy(entity)
        engine.destroyVertexBuffer(vertices);engine.destroyIndexBuffer(indices);engine.destroyMaterialInstance(instance)
    }
}
