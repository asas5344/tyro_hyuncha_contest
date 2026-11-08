package com.team.hyuncha.render3d

import com.google.android.filament.*

/** Offscreen camera plus a textured quad. Layer 2 is reserved for screens, preventing feedback. */
internal class CameraFeed(
    private val engine: Engine, scene: Scene, material: Material,
    val kind: FeedKind, width: Int, height: Int, center: Point3, screenWidth: Double, screenHeight: Double,
    surfaceYaw: Double=0.0,
) : AutoCloseable {
    val cameraEntity=EntityManager.get().create()
    val camera=engine.createCamera(cameraEntity).apply {
        setExposure(16f,1f/125f,100f)
        if(kind==FeedKind.SURROUND) {
            val halfHeight=4.2
            setProjection(Camera.Projection.ORTHO,-halfHeight*width/height,halfHeight*width/height,-halfHeight,halfHeight,.1,40.0)
        } else {
            val fov=when(kind) {
                FeedKind.REAR->72.0;FeedKind.ROOM_MIRROR->26.0
                FeedKind.LEFT_MIRROR->34.0;else->40.0
            }
            setProjection(fov,width.toDouble()/height,.04,100.0,Camera.Fov.VERTICAL)
            setScaling(-1.0,1.0)
        }
    }
    private val color=Texture.Builder().width(width).height(height).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8)
        .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE).build(engine)
    private val depth=Texture.Builder().width(width).height(height).levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.DEPTH24)
        .usage(Texture.Usage.DEPTH_ATTACHMENT).build(engine)
    private val target=RenderTarget.Builder().texture(RenderTarget.AttachmentPoint.COLOR,color)
        .texture(RenderTarget.AttachmentPoint.DEPTH,depth).build(engine)
    val view=engine.createView().apply {
        this.scene=scene;this.camera=this@CameraFeed.camera
        viewport=Viewport(0,0,width,height);renderTarget=target
        isPostProcessingEnabled=true
        antiAliasing=View.AntiAliasing.FXAA
        isFrontFaceWindingInverted=kind!=FeedKind.SURROUND
        setVisibleLayers(0xff,if(kind==FeedKind.ROOM_MIRROR) 1 else 1 or 4)
    }
    private val panel=TexturedPanel(engine,scene,material,color,center,screenWidth,screenHeight,
        kind!=FeedKind.REAR && kind!=FeedKind.SURROUND,renderTarget=true,
        rounded=kind==FeedKind.LEFT_MIRROR || kind==FeedKind.RIGHT_MIRROR,surfaceYaw=surfaceYaw)
    val entity get()=panel.entity
    var hasRendered=false
    var needsUpdate=true
    var enabled=kind!=FeedKind.REAR && kind!=FeedKind.SURROUND
        set(value) { field=value;panel.setVisible(value) }
    fun setBodyTransform(transform: FloatArray) = panel.setBodyTransform(transform)
    fun setAim(aim: CameraAim) = camera.lookAt(aim.eye.x,aim.eye.y,aim.eye.z,aim.target.x,aim.target.y,aim.target.z,aim.up.x,aim.up.y,aim.up.z)
    override fun close() {
        engine.destroyView(view);engine.destroyRenderTarget(target)
        panel.close()
        engine.destroyTexture(color);engine.destroyTexture(depth)
        engine.destroyCameraComponent(cameraEntity);EntityManager.get().destroy(cameraEntity)
    }
}
