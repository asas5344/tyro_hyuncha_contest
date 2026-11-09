package com.team.hyuncha.render3d

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.util.Log
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.team.hyuncha.sim.CarPose
import com.team.hyuncha.sim.ParkingMap
import com.team.hyuncha.data.CarSpec
import com.team.hyuncha.input.LookSide
import com.team.hyuncha.input.Direction
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.data.CarPresets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** All Filament calls run on the main thread. Owns every native resource until close(). */
internal class ParkingRenderer(private val context: Context, private val map: ParkingMap, private val car: CarSpec, private val onReady: () -> Unit = {}) : AutoCloseable {
    companion object {
        init { Filament.init(); Gltfio.init() }
    }
    private val visual = VehicleVisuals.forCar(car)
    private val engine = Engine.create(Engine.Backend.OPENGL)
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val view = engine.createView().apply {
        scene = this@ParkingRenderer.scene
        camera = this@ParkingRenderer.camera
        isPostProcessingEnabled = true
        antiAliasing = View.AntiAliasing.FXAA
    }
    // 후진할 때 미니맵 자리에 띄우는 작은 후방 카메라 화면 (차량 모니터는 운전석 시점에서 화면 밖으로 잘려서).
    // 실제 후방 카메라처럼 좌우 반전. 서라운드 뷰는 화면 쪽(DriverMinimap)이 2D 탑뷰로 그림
    private val pipCameraEntity = EntityManager.get().create()
    private val pipCamera = engine.createCamera(pipCameraEntity).apply { setExposure(16f,1f/125f,100f);setScaling(-1.0,1.0) }
    private val pipView = engine.createView().apply {
        scene = this@ParkingRenderer.scene
        camera = this@ParkingRenderer.pipCamera
        isPostProcessingEnabled = true
        antiAliasing = View.AntiAliasing.FXAA
        isFrontFaceWindingInverted = true
        setVisibleLayers(0xff,1 or 4 or 8 or ParkingMesh.CEILING_LAYER or ParkingMesh.OWN_CAR_LAYER)
    }
    private var pipActive = false
    private val sky = Skybox.Builder().color(.055f,.065f,.085f,1f).build(engine)
    private val indirect = IndirectLight.Builder().irradiance(1,floatArrayOf(.7f,.75f,.85f)).intensity(30_000f).build(engine)
    private val light = EntityManager.get().create()
    private val provider = UbershaderProvider(engine)
    private val loader = AssetLoader(engine,provider,EntityManager.get())
    private val resources = ResourceLoader(engine)
    private val assets = mutableListOf<FilamentAsset>()
    private val sceneEntities = mutableListOf<Int>()
    private val paintInstances = mutableListOf<MaterialInstance>()
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var chain: SwapChain? = null
    private var running = false
    private var closed = false
    private var lastFrame = 0L
    private var dirty = true
    private var ready = false
    private var frames = 0
    private var hasSubmittedFrame = false
    private var statsStart = 0L
    private lateinit var own: FilamentInstance
    private val named = mutableMapOf<String,Int>()
    private val initialTransforms = mutableMapOf<Int,FloatArray>()
    private val feeds = mutableListOf<CameraFeed>()
    private var feedMaterial: Material? = null
    private var guideMaterial: Material? = null
    private var guides: DrivingGuides? = null
    private var dashboard: DashboardDisplay? = null
    private var lastFeedFrame = 0L
    private var lastLook = LookSide.FRONT
    private var yaw = 0.0
    private var pitch = 2.0
    private var fov = 56.0
    private var previousPose = map.start
    private var roll = 0.0
    var state: RenderState = RenderState(map.start,0.0,LookSide.FRONT)
        set(value) { if(field!=value) { field=value;dirty=true } }

    init {
        try { initialize() } catch(e: Exception) { close();throw e }
    }
    private fun addEntities(entities: IntArray) {
        scene.addEntities(entities);sceneEntities.addAll(entities.toList())
    }
    private fun isLegacyInterior(name: String) = listOf(
        "dash", "vent", "instrument", "center_", "steering_", "door_card", "cabin_floor", "room_mirror", "interior_",
    ).any { name.startsWith(it) }
    private fun initialize() {
        scene.skybox = sky
        scene.indirectLight = indirect
        LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(.3f,-1f,.4f).intensity(85_000f).castShadows(false).build(engine,light)
        scene.addEntity(light)
        camera.setExposure(16f,1f/125f,100f)
        load(ParkingMesh.garage(map))
        load(ParkingMesh.ceiling(map),ParkingMesh.CEILING_LAYER)
        val bytes=context.assets.open("models/grandeur.glb").use { it.readBytes() }
        val buffer=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); flip() }
        val instances=arrayOfNulls<FilamentInstance>(map.parkedCars.size.coerceAtLeast(1))
        val cars=checkNotNull(loader.createInstancedAsset(buffer,instances)) { "세단 GLB 로딩 실패" }
        assets+=cars
        resources.loadResources(cars)
        val driverAsset=visual.asset.removeSuffix(".glb")+"_driver.glb"
        val ownBytes=context.assets.open("models/$driverAsset").use { it.readBytes() }
        val ownBuffer=ByteBuffer.allocateDirect(ownBytes.size).order(ByteOrder.nativeOrder()).apply { put(ownBytes);flip() }
        val ownAsset=checkNotNull(loader.createAsset(ownBuffer)).also { assets+=it;resources.loadResources(it) }
        own=ownAsset.instance
        // Keep the rear axle fixed when positioning an instance; parked rects specify body centres.
        map.parkedCars.forEachIndexed { i, r ->
            val instance=checkNotNull(instances[i])
            val parkedVisual=VehicleVisuals.forCar(CarPresets.GRANDEUR)
            val sx=r.halfLength*2/parkedVisual.lengthM; val sz=r.halfWidth*2/parkedVisual.widthM
            val offset=(parkedVisual.lengthM/2-parkedVisual.rearOverhangM)*sx
            setTransform(instance.root,SceneCoordinates.transform(CarPose(r.center.x-cos(r.angle)*offset,r.center.y-sin(r.angle)*offset,r.angle),sx,1.0,sz))
            val exterior=instance.entities.filter { e ->
                val name=cars.getName(e) ?: ""
                !isLegacyInterior(name)
            }.toIntArray()
            val painted=exterior.filter { e ->
                val name=cars.getName(e) ?: ""
                name in listOf("sculpted_body","hood","trunk","roof","exterior_paint") || name.contains("pillar") || name.startsWith("mirror_housing")
            }
            painted.firstOrNull()?.let { e ->
                val rm=engine.renderableManager
                val ri=rm.getInstance(e)
                val paint=MaterialInstance.duplicate(rm.getMaterialInstanceAt(ri,0),"parked_paint_$i")
                paintInstances+=paint
                val colors=listOf(floatArrayOf(.86f,.89f,.92f),floatArrayOf(.26f,.34f,.45f),floatArrayOf(.65f,.2f,.16f),floatArrayOf(.25f,.27f,.29f))
                val c=colors[i%colors.size]
                paint.setParameter("baseColorFactor",Colors.RgbaType.SRGB,c[0],c[1],c[2],1f)
                painted.forEach { rm.setMaterialInstanceAt(rm.getInstance(it),0,paint) }
            }
            addEntities(exterior)
        }
        // Clear glazing from inside; parked instances retain dark exterior window surfaces.
        val ownVisible=own.entities.filter { e ->
            val name=ownAsset.getName(e) ?: ""
            val glass=name=="windshield" || name=="rear_window" || name.startsWith("side_glass")
            val ri=engine.renderableManager.getInstance(e)
            if(glass) engine.renderableManager.setLayerMask(ri,0xff,4)
            else if(ri!=0) engine.renderableManager.setLayerMask(ri,0xff,ParkingMesh.OWN_CAR_LAYER)
            !name.startsWith("mirror_surface") && !isLegacyInterior(name)
        }.toIntArray()
        addEntities(ownVisible)
        for (entity in own.entities) {
            val name=ownAsset.getName(entity) ?: continue
            if(isLegacyInterior(name)) continue
            named[name]=entity
            if (name.endsWith("_steer") || name.endsWith("_spin") || name=="steering_wheel") {
                val matrix=FloatArray(16)
                engine.transformManager.getTransform(engine.transformManager.getInstance(entity),matrix)
                initialTransforms[entity]=matrix
            }
        }
        // One shared cockpit mesh, translated to each vehicle's eye datum.
        // Parenting keeps the cockpit rigidly attached to the same simulated body in driving/replay.
        val cockpitBytes=context.assets.open("models/cockpit.glb").use { it.readBytes() }
        val cockpitBuffer=ByteBuffer.allocateDirect(cockpitBytes.size).order(ByteOrder.nativeOrder()).apply {
            put(cockpitBytes);flip()
        }
        val cockpit=checkNotNull(loader.createAsset(cockpitBuffer)) { "공통 실내 GLB 로딩 실패" }
        assets+=cockpit
        resources.loadResources(cockpit)
        val tm=engine.transformManager
        val cockpitRoot=tm.getInstance(cockpit.root)
        tm.setParent(cockpitRoot,tm.getInstance(own.root))
        val mount=FloatArray(16)
        android.opengl.Matrix.setIdentityM(mount,0)
        android.opengl.Matrix.translateM(mount,0,visual.eyeForwardM.toFloat(),visual.eyeHeightM.toFloat(),0f)
        tm.setTransform(cockpitRoot,mount)
        addEntities(cockpit.entities)
        cockpit.entities.forEach { e ->
            val ri=engine.renderableManager.getInstance(e)
            if(ri!=0) engine.renderableManager.setLayerMask(ri,0xff,ParkingMesh.OWN_CAR_LAYER)
        }
        for(entity in cockpit.entities) {
            val name=cockpit.getName(entity) ?: continue
            if(name=="steering_wheel") {
                named[name]=entity
                initialTransforms[entity]=FloatArray(16).also { tm.getTransform(tm.getInstance(entity),it) }
            }
        }
        cockpit.releaseSourceData()
        // The same rounded contour is shared by glass and bezel; each side faces its driver.
        val mirrorBytes=context.assets.open("models/driver_mirror.glb").use { it.readBytes() }
        val mirrorBuffer=ByteBuffer.allocateDirect(mirrorBytes.size).order(ByteOrder.nativeOrder()).apply { put(mirrorBytes);flip() }
        val mirrorInstances=arrayOfNulls<FilamentInstance>(2)
        val mirrorAsset=checkNotNull(loader.createInstancedAsset(mirrorBuffer,mirrorInstances))
        assets+=mirrorAsset;resources.loadResources(mirrorAsset)
        listOf(-1,1).forEachIndexed { index,side ->
            val instance=checkNotNull(mirrorInstances[index])
            tm.setParent(tm.getInstance(instance.root),tm.getInstance(own.root))
            tm.setTransform(tm.getInstance(instance.root),CameraRig.mirrorMount(car,side))
            instance.entities.forEach { entity ->
                val ri=engine.renderableManager.getInstance(entity)
                if(ri!=0) engine.renderableManager.setLayerMask(ri,0xff,8)
            }
            addEntities(instance.entities)
        }
        mirrorAsset.releaseSourceData()
        val stalkBytes=context.assets.open("models/${visual.asset.removeSuffix(".glb")}_mirror_stalks.glb").use { it.readBytes() }
        val stalkBuffer=ByteBuffer.allocateDirect(stalkBytes.size).order(ByteOrder.nativeOrder()).apply { put(stalkBytes);flip() }
        val stalks=checkNotNull(loader.createAsset(stalkBuffer)).also { assets+=it;resources.loadResources(it) }
        tm.setParent(tm.getInstance(stalks.root),tm.getInstance(own.root))
        stalks.entities.forEach { entity ->
            val ri=engine.renderableManager.getInstance(entity)
            if(ri!=0) engine.renderableManager.setLayerMask(ri,0xff,8)
        }
        addEntities(stalks.entities);stalks.releaseSourceData()

        cars.releaseSourceData()
        ownAsset.releaseSourceData()
        feedMaterial=loadMaterial("camera_feed")
        guideMaterial=loadMaterial("guidance")
        dashboard=DashboardDisplay(engine,scene,checkNotNull(feedMaterial),visual).also { sceneEntities.add(it.entity) }
        guides=DrivingGuides(engine,scene,checkNotNull(guideMaterial),car).also { sceneEntities.add(it.entity) }
        for(side in listOf(-1,1)) {
            val kind=if(side<0) FeedKind.LEFT_MIRROR else FeedKind.RIGHT_MIRROR
            feeds+=CameraFeed(engine,scene,checkNotNull(feedMaterial),kind,768,432,CameraRig.mirrorPoint(car,side).let {
                val yaw=CameraRig.mirrorYaw(car,side)
                Point3(it.x-.002*cos(yaw),it.y,it.z-.002*sin(yaw))
            },CameraRig.MIRROR_WIDTH_M,CameraRig.MIRROR_HEIGHT_M,CameraRig.mirrorYaw(car,side))
        }
        feeds+=CameraFeed(engine,scene,checkNotNull(feedMaterial),FeedKind.ROOM_MIRROR,320,112,Point3(visual.eyeForwardM+.78,visual.eyeHeightM+.17,-.10),.26,.075)
        for(kind in listOf(FeedKind.REAR,FeedKind.SURROUND)) {
            feeds+=CameraFeed(engine,scene,checkNotNull(feedMaterial),kind,384,216,Point3(visual.monitorForwardM,visual.monitorHeightM,.18),.25,.13)
        }
        sceneEntities.addAll(feeds.map { it.entity })
        view.setVisibleLayers(0xff,1 or 2 or 8 or ParkingMesh.CEILING_LAYER or ParkingMesh.OWN_CAR_LAYER)
        ui.renderCallback=object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                Log.d("Parking3D","surface ready")
                chain?.let { engine.destroySwapChain(it) }
                chain=engine.createSwapChain(surface);dirty=true;lastFeedFrame=0
            }
            override fun onDetachedFromSurface() {
                chain?.let { engine.destroySwapChain(it); engine.flushAndWait() }; chain=null
            }
            override fun onResized(width: Int,height: Int) {
                val size=RenderSize.surface(width,height,ui.desiredWidth,ui.desiredHeight)
                Log.d("Parking3D","viewport ${size.width}x${size.height} (layout ${width}x${height})")
                view.viewport=Viewport(0,0,size.width,size.height);dirty=true
                camera.setProjection(fov,size.aspect,.035,100.0,Camera.Fov.VERTICAL)
                engine.flushAndWait()
            }
        }
    }
    private fun loadMaterial(name: String): Material {
        val bytes=context.assets.open("materials/$name.filamat").use { it.readBytes() }
        val buffer=ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes);flip() }
        return Material.Builder().payload(buffer,buffer.remaining()).build(engine)
    }
    /** @param layer 0이 아니면 이 층에만 보이게 함 (천장처럼 일부 카메라에서 빼야 하는 것) */
    private fun load(buffer: ByteBuffer,layer: Int=0) {
        val asset=checkNotNull(loader.createAsset(buffer)) { "주차장 GLB 로딩 실패" }
        assets+=asset; resources.loadResources(asset); addEntities(asset.entities); asset.releaseSourceData()
        if(layer!=0) asset.entities.forEach { e ->
            val ri=engine.renderableManager.getInstance(e)
            if(ri!=0) engine.renderableManager.setLayerMask(ri,0xff,layer)
        }
    }
    fun attach(texture: TextureView) {
        texture.addOnLayoutChangeListener { _,left,top,right,bottom,_,_,_,_ ->
            val width=right-left;val height=bottom-top
            if(!closed && width>0 && height>0) {
                val scale=minOf(1.0,960.0/width,720.0/height)
                ui.setDesiredSize((width*scale).toInt().coerceAtLeast(1),(height*scale).toInt().coerceAtLeast(1))
            }
        }
        ui.attachTo(texture)
        val listener=checkNotNull(texture.surfaceTextureListener)
        texture.surfaceTextureListener=object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture,width: Int,height: Int) = listener.onSurfaceTextureAvailable(surface,width,height)
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture,width: Int,height: Int) = listener.onSurfaceTextureSizeChanged(surface,width,height)
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = listener.onSurfaceTextureDestroyed(surface)
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                listener.onSurfaceTextureUpdated(surface)
                // A queued GPU frame is not yet a visible TextureView frame.
                if(!ready && hasSubmittedFrame && !closed) { ready=true;onReady() }
            }
        }
    }
    fun setActive(active: Boolean) {
        Log.d("Parking3D","active=$active running=$running closed=$closed")
        if (closed || active==running) return
        running=active
        if (active) { dirty=true;lastFrame=0;lastFeedFrame=0;statsStart=0;frames=0; Choreographer.getInstance().postFrameCallback(callback) }
        else { Choreographer.getInstance().removeFrameCallback(callback); engine.flushAndWait() }
    }
    private val callback=object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (!running || closed) return
            Choreographer.getInstance().postFrameCallback(this)
            if (lastFrame!=0L && now-lastFrame<32_000_000L) return
            val dt=if(lastFrame==0L) 1.0 else ((now-lastFrame)/1e9).coerceAtMost(.1)
            lastFrame=now
            if (!ui.isReadyToRender) return
            val head=CameraRig.driverHead(car,state.look)
            val headMoving=!state.focusRear && (abs(yaw-head.yawDeg)>.01 || abs(pitch-head.pitchDeg)>.01 || abs(fov-head.fovDeg)>.01)
            if(ready && !dirty && !headMoving && dashboard?.needsRefresh!=true) {
                statsStart=0;frames=0
                return
            }
            update(dt)
            val swap=chain ?: return
            if (renderer.beginFrame(swap,now)) {
                val feedInterval=if(state.look==LookSide.FRONT) 100_000_000L else 50_000_000L
                if(lastFeedFrame==0L || now-lastFeedFrame>=feedInterval) {
                    feeds.filter { it.enabled && (it.needsUpdate || !it.hasRendered) }.forEach { renderer.render(it.view);it.hasRendered=true }
                    lastFeedFrame=now
                }
                renderer.render(view)
                if(pipActive) renderer.render(pipView)
                renderer.endFrame();hasSubmittedFrame=true;frames++;dirty=false
                if(statsStart==0L) statsStart=now
                if(now-statsStart>=5_000_000_000L) {
                    Log.d("Parking3D","rendered fps=%.1f".format(frames*1e9/(now-statsStart)))
                    frames=0;statsStart=now
                }
            }
        }
    }
    private fun setTransform(entity: Int,matrix: FloatArray) {
        engine.transformManager.setTransform(engine.transformManager.getInstance(entity),matrix)
    }
    private fun rotatePart(name: String,degrees: Double,axis: Int) {
        val entity=named[name] ?: return
        val initial=initialTransforms[entity] ?: return
        val rotation=FloatArray(16)
        android.opengl.Matrix.setRotateM(rotation,0,degrees.toFloat(),if(axis==0)1f else 0f,if(axis==1)1f else 0f,if(axis==2)1f else 0f)
        val result=FloatArray(16)
        android.opengl.Matrix.multiplyMM(result,0,initial,0,rotation,0)
        setTransform(entity,result)
    }
    private fun update(dt: Double) {
        val s=state
        val bodyTransform=SceneCoordinates.transform(s.pose)
        setTransform(own.root,bodyTransform)
        dashboard?.update(s,bodyTransform)
        guides?.update(s.wheelAngleDeg,s.direction,bodyTransform,s.showGuides)
        if(lastLook!=s.look) { lastFeedFrame=0;lastLook=s.look }
        feeds.forEach { feed ->
            val aspect=view.viewport.width.toDouble()/view.viewport.height.coerceAtLeast(1)
            val currentHead=DriverHead(yaw,pitch,fov)
            feed.needsUpdate=!s.focusRear && when(feed.kind) {
                FeedKind.LEFT_MIRROR -> s.look==LookSide.LEFT || CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,-1),currentHead,aspect,.12)
                FeedKind.RIGHT_MIRROR -> s.look==LookSide.RIGHT || CameraRig.inDriverView(car,CameraRig.mirrorPoint(car,1),currentHead,aspect,.12)
                FeedKind.ROOM_MIRROR -> CameraRig.inDriverView(car,Point3(visual.eyeForwardM+.78,visual.eyeHeightM+.17,-.10),currentHead,aspect,.15)
                else -> true
            }
            feed.setBodyTransform(bodyTransform)
            feed.setAim(CameraRig.feed(car,s.pose,feed.kind))
            val enabled=when(feed.kind) {
                FeedKind.REAR -> s.direction==Direction.REVERSE && s.carCamera==CarCamera.REAR_ONLY
                FeedKind.SURROUND -> s.direction==Direction.REVERSE && s.carCamera==CarCamera.SURROUND
                else -> true
            }
            if(feed.enabled!=enabled) { feed.enabled=enabled;lastFeedFrame=0 }
        }
        rotatePart("front_left_steer",-s.wheelAngleDeg,1)
        rotatePart("front_right_steer",-s.wheelAngleDeg,1)
        rotatePart("steering_wheel",s.wheelAngleDeg*car.steeringRatio,0)
        val dx=s.pose.x-previousPose.x;val dz=s.pose.y-previousPose.y
        if(hypot(dx,dz)<1.0) roll=(roll-Math.toDegrees((dx*cos(s.pose.heading)+dz*sin(s.pose.heading))/visual.wheelRadiusM))%360.0
        previousPose=s.pose
        listOf("front_left_spin","front_right_spin","rear_left_spin","rear_right_spin").forEach { rotatePart(it,roll,2) }
        if(s.focusRear && s.direction==Direction.REVERSE) {
            val kind=if(s.carCamera==CarCamera.REAR_ONLY) FeedKind.REAR else FeedKind.SURROUND
            applyAim(camera,CameraRig.feed(car,s.pose,kind))
            val aspect=view.viewport.width.toDouble()/view.viewport.height.coerceAtLeast(1)
            if(kind==FeedKind.SURROUND) {
                camera.setProjection(Camera.Projection.ORTHO,-4.2*aspect,4.2*aspect,-4.2,4.2,.1,40.0)
                camera.setScaling(1.0,1.0)
            } else {
                camera.setProjection(72.0,aspect,.04,100.0,Camera.Fov.VERTICAL)
                camera.setScaling(-1.0,1.0)
            }
            view.isFrontFaceWindingInverted=kind!=FeedKind.SURROUND
            view.setVisibleLayers(0xff,if(kind==FeedKind.SURROUND) 1 or 4 or 8 or ParkingMesh.OWN_CAR_LAYER else 1 or 4 or 8 or ParkingMesh.CEILING_LAYER or ParkingMesh.OWN_CAR_LAYER)
        } else {
            val head=CameraRig.driverHead(car,s.look)
            val blend=1-exp(-dt/.14)
            yaw+=(head.yawDeg-yaw)*blend;pitch+=(head.pitchDeg-pitch)*blend;fov+=(head.fovDeg-fov)*blend
            val eye=SceneCoordinates.eye(car,s.pose)
            applyAim(camera,CameraRig.aim(eye,s.pose.heading+Math.toRadians(yaw),pitch))
            camera.setProjection(fov,view.viewport.width.toDouble()/view.viewport.height.coerceAtLeast(1),.035,100.0,Camera.Fov.VERTICAL)
            camera.setScaling(1.0,1.0)
            view.isFrontFaceWindingInverted=false
            view.setVisibleLayers(0xff,1 or 2 or 8 or ParkingMesh.CEILING_LAYER or ParkingMesh.OWN_CAR_LAYER)
        }
        updatePip(s)
    }
    /** 미니맵 자리의 후방 카메라 화면 (후진 중, 내 차 카메라가 후방 카메라일 때만) */
    private fun updatePip(s: RenderState) {
        val rect=s.pip
        pipActive=rect!=null && s.direction==Direction.REVERSE && !s.focusRear && s.carCamera==CarCamera.REAR_ONLY
        if(!pipActive || rect==null) return
        val full=view.viewport
        val w=(full.width*rect.width).toInt().coerceAtLeast(1)
        val h=(full.height*rect.height).toInt().coerceAtLeast(1)
        // Filament 화면 좌표는 아래쪽이 0
        pipView.viewport=Viewport((full.width*rect.left).toInt(),(full.height*(1-rect.top-rect.height)).toInt(),w,h)
        applyAim(pipCamera,CameraRig.feed(car,s.pose,FeedKind.REAR))
        pipCamera.setProjection(72.0,w.toDouble()/h,.04,100.0,Camera.Fov.VERTICAL)
    }
    private fun applyAim(camera: Camera,aim: CameraAim) = camera.lookAt(aim.eye.x,aim.eye.y,aim.eye.z,aim.target.x,aim.target.y,aim.target.z,aim.up.x,aim.up.y,aim.up.z)
    override fun close() {
        if (closed) return
        setActive(false);closed=true
        ui.detach()
        scene.removeEntities(sceneEntities.toIntArray());sceneEntities.clear()
        dashboard?.close();dashboard=null
        guides?.close();guides=null
        feeds.forEach { it.close() };feeds.clear()
        guideMaterial?.let { engine.destroyMaterial(it) };guideMaterial=null
        feedMaterial?.let { engine.destroyMaterial(it) };feedMaterial=null
        assets.asReversed().forEach { loader.destroyAsset(it) };assets.clear()
        paintInstances.forEach { engine.destroyMaterialInstance(it) };paintInstances.clear()
        resources.destroy();loader.destroy();provider.destroyMaterials();provider.destroy()
        scene.indirectLight=null;scene.skybox=null
        engine.destroyIndirectLight(indirect);engine.destroySkybox(sky)
        engine.destroyEntity(light);EntityManager.get().destroy(light)
        engine.destroyView(pipView);engine.destroyCameraComponent(pipCameraEntity);EntityManager.get().destroy(pipCameraEntity)
        engine.destroyView(view);engine.destroyCameraComponent(cameraEntity);EntityManager.get().destroy(cameraEntity)
        engine.destroyScene(scene);engine.destroyRenderer(renderer);engine.destroy()
    }
}
internal data class RenderState(
    val pose: CarPose,val wheelAngleDeg: Double,val look: LookSide,
    val direction: Direction=Direction.FORWARD,val carCamera: CarCamera=CarCamera.REAR_ONLY,
    val showGuides: Boolean=true,val focusRear: Boolean=false,
    val speedMps: Double=0.0,val braking: Boolean=true,
    val pip: PipRect?=null,
)
/** 화면 안의 작은 카메라 화면 위치. 3D 화면 크기에 대한 비율 (위·왼쪽 기준) */
internal data class PipRect(val left: Float,val top: Float,val width: Float,val height: Float)
