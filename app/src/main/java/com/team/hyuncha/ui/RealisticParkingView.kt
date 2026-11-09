package com.team.hyuncha.ui

import android.view.TextureView
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.team.hyuncha.input.*
import com.team.hyuncha.data.CarCamera
import com.team.hyuncha.render3d.*
import com.team.hyuncha.sim.ParkingSimulator
import com.team.hyuncha.ui.theme.CameraOverlay
import com.team.hyuncha.ui.theme.CameraSecondaryText

/** TextureView participates in Compose z-order, so HUDs, dialogs and controls remain on top. */
@Composable
fun RealisticParkingView(
    sim: ParkingSimulator, frame: () -> Long, direction: () -> Direction, braking: () -> Boolean,
    modifier: Modifier = Modifier, replay: () -> ReplayView? = { null },
    carCamera: CarCamera = CarCamera.REAR_ONLY,
) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var renderer by remember(sim) { mutableStateOf<ParkingRenderer?>(null) }
    var focusRear by remember(sim) { mutableStateOf(false) }
    var ready by remember(sim) { mutableStateOf(false) }
    var error by remember(sim) { mutableStateOf<String?>(null) }
    val currentFrame by rememberUpdatedState(frame)
    val currentDirection by rememberUpdatedState(direction)
    val currentBraking by rememberUpdatedState(braking)
    val currentReplay by rememberUpdatedState(replay)
    DisposableEffect(sim,lifecycle) {
        LookButtons.active=true
        val observer=LifecycleEventObserver { _,_ -> renderer?.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer);LookButtons.active=false
            renderer?.close();renderer=null
        }
    }
    BoxWithConstraints(modifier.background(CameraOverlay.copy(alpha = 1f))) {
        key(sim) {
            AndroidView(
                modifier=Modifier.fillMaxSize(),
                factory={ context -> TextureView(context).apply { tag="parking3d_surface" }.also { texture ->
                    try {
                        val created=ParkingRenderer(context,sim.map,sim.car,onReady={ready=true})
                        renderer=created
                        created.attach(texture)
                        created.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    } catch (e: Exception) {
                        renderer?.close();renderer=null
                        Log.e("Parking3D","초기화 실패",e);error=e.message ?: "초기화 실패"
                    }
                } },
            )
        }
        // 미니맵 자리 (화면 크기에 대한 비율). 후진하면 같은 자리에 후방 카메라·서라운드 뷰를 띄움
        val miniTop=if(LookButtons.side==LookSide.FRONT) .35f else .70f
        val miniRect=PipRect(.02f,miniTop,.24f,.24f*.66f*maxWidth.value/maxHeight.value)
        // 후진 중이고 화면 확대를 안 했으면 미니맵 대신 내 차 카메라 화면
        val reverseCamera={
            val reverse=sceneOf(sim,currentReplay(),currentDirection(),currentBraking()).direction==Direction.REVERSE
            if(reverse && !focusRear) carCamera else null
        }
        // Read simulation state in a small scope, not in the AndroidView factory.
        RenderSync(renderer) {
            currentFrame()
            val scene=sceneOf(sim,currentReplay(),currentDirection(),currentBraking())
            RenderState(scene.pose,scene.wheelAngleDeg,LookButtons.side,scene.direction,carCamera,scene.showGuide,focusRear,scene.speedMps,scene.braking,miniRect)
        }
        DriverMinimap(sim,currentFrame,currentDirection,currentBraking,currentReplay,
            Modifier.offset(x=maxWidth*miniRect.left,y=maxHeight*miniRect.top).width(maxWidth*miniRect.width).aspectRatio(1f/.66f),
            reverseCamera=reverseCamera)
        if(error!=null) Surface(Modifier.align(Alignment.Center).padding(24.dp)) {
            Text("3D 화면을 열 수 없어요. 탑뷰로 전환한 뒤 다시 시도해 주세요.",Modifier.padding(20.dp))
        }
        // 화면에는 안 보이고, 자동 테스트가 3D 준비 완료를 확인하는 표시
        if(ready) Spacer(Modifier.size(0.dp).testTag("parking3d_ready"))
        // 시점·차종은 왼쪽 위 정보 상자에 나오므로, 여기는 3D를 불러오는 동안만 안내
        if(!ready) Surface(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            color = CameraOverlay,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("3D 화면 준비 중",Modifier.padding(10.dp),color=CameraSecondaryText)
        }
        RearCameraControl(
            reverse={ currentFrame();sceneOf(sim,currentReplay(),currentDirection(),currentBraking()).direction==Direction.REVERSE },
            expanded=focusRear,onToggle={focusRear=!focusRear},
            modifier=Modifier.align(Alignment.TopEnd).padding(top=96.dp,end=16.dp),
        )
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(LookSide.LEFT to "왼쪽",LookSide.FRONT to "정면",LookSide.RIGHT to "오른쪽").forEach { (side,label) ->
                val isSelected = LookButtons.side == side
                Button(
                    onClick = { LookButtons.select(side) },
                    modifier = Modifier.testTag("look_${side.name.lowercase()}").semantics { selected = isSelected },
                    colors = cameraButtonColors(isSelected),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(label) }
            }
        }
    }
}
@Composable
private fun RenderSync(renderer: ParkingRenderer?,state: () -> RenderState) {
    val value=state()
    SideEffect { renderer?.state=value }
}

@Composable
private fun RearCameraControl(reverse: () -> Boolean,expanded: Boolean,onToggle: () -> Unit,modifier: Modifier) {
    if(reverse()) Button(
        onClick = onToggle,
        modifier = modifier.testTag("expand_rear_camera"),
        colors = cameraButtonColors(expanded),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(if(expanded) "운전석 복귀" else "후방 화면 확대")
    }
}
