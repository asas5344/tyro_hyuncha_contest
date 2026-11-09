package com.team.hyuncha.render3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import com.google.android.filament.*
import com.google.android.filament.android.TextureHelper
import com.team.hyuncha.input.Direction
import kotlin.math.abs
import kotlin.math.roundToInt

/** Two reusable upload bitmaps; busy buffers are never drawn into while the renderer reads them. */
internal class DashboardDisplay(private val engine: Engine,scene: Scene,material: Material,visual: VehicleVisualProfile) : AutoCloseable {
    private val texture=Texture.Builder().width(512).height(128).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.SRGB8_A8).usage(Texture.Usage.SAMPLEABLE or Texture.Usage.UPLOADABLE).build(engine)
    private val panel=TexturedPanel(engine,scene,material,texture,Point3(visual.instrumentForwardM,visual.instrumentHeightM,-.37),.27,.06)
    val entity get()=panel.entity
    private val bitmaps=Array(2) { Bitmap.createBitmap(512,128,Bitmap.Config.ARGB_8888) }
    private val busy=BooleanArray(2)
    private var closed=false
    private val handler=Handler(Looper.getMainLooper())
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD) }
    var needsRefresh=true
        private set
    private var lastLabel=""
    private var lastUploadNanos=0L
    fun update(state: RenderState,transform: FloatArray) {
        if(closed) return
        panel.setBodyTransform(transform)
        val speed=(abs(state.speedMps)*36).roundToInt()/10.0
        val gear=if(state.direction==Direction.FORWARD) "D" else "R"
        val label="$gear:$speed:${state.braking}"
        if(label==lastLabel) { needsRefresh=false;return }
        needsRefresh=true
        val now=System.nanoTime()
        if(now-lastUploadNanos<100_000_000L) return
        val index=busy.indexOfFirst { !it }
        if(index<0) return
        val canvas=Canvas(bitmaps[index]);canvas.drawColor(Color.rgb(5,15,20))
        paint.color=Color.rgb(102,230,182);paint.textSize=76f
        canvas.drawText(gear,22f,90f,paint)
        paint.color=Color.WHITE;paint.textSize=72f
        canvas.drawText(String.format(java.util.Locale.US,"%.1f",speed),132f,88f,paint)
        paint.textSize=27f;paint.color=Color.LTGRAY;canvas.drawText("km/h",370f,84f,paint)
        paint.color=if(state.braking) Color.rgb(255,184,110) else Color.rgb(102,230,182)
        canvas.drawRect(20f,111f,492f,117f,paint)
        busy[index]=true
        TextureHelper.setBitmap(engine,texture,0,bitmaps[index],handler,Runnable { busy[index]=false;if(closed && !bitmaps[index].isRecycled) bitmaps[index].recycle() })
        lastLabel=label;lastUploadNanos=now;needsRefresh=false
    }
    override fun close() {
        if(closed) return
        closed=true
        engine.flushAndWait()
        panel.close();engine.destroyTexture(texture)
        bitmaps.indices.filter { !busy[it] }.forEach { bitmaps[it].recycle() }
    }
}
