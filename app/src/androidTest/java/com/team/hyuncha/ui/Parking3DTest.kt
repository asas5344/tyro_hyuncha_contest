package com.team.hyuncha.ui

import android.graphics.Bitmap
import android.view.TextureView
import com.team.hyuncha.data.CarCamera
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.team.hyuncha.data.CarPresets
import com.team.hyuncha.input.Direction
import com.team.hyuncha.input.TouchInput
import com.team.hyuncha.sim.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Parking3DTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun realFramesSurviveDrivingViewChangesAndBackground() {
        val sim=ParkingSimulator(CarPresets.GRANDEUR,ParkingMaps.perpendicular())
        val input=TouchInput()
        var frame by mutableLongStateOf(0)
        var visible by mutableStateOf(true)
        compose.setContent {
            if(visible) RealisticParkingView(sim,{frame},{input.direction},{input.brakePressed},Modifier.fillMaxSize())
        }
        fun ready() { compose.waitUntil(30_000) { compose.onAllNodesWithText("3D 화면 표시 중").fetchSemanticsNodes().isNotEmpty() } }
        ready();capture("3d-front")
        compose.runOnIdle {
            input.brakePressed=false
            repeat(100) { sim.step(input,.02) };frame++
            assertTrue(sim.pose.x>sim.map.start.x+1)
        }
        compose.waitForIdle();capture("3d-forward")
        compose.runOnIdle {
            input.brakePressed=true;repeat(50) { sim.step(input,.02) }
            input.direction=Direction.REVERSE;input.steeringDeg=210.0;input.brakePressed=false
            repeat(100) { sim.step(input,.02) };frame++
            assertTrue(sim.pose.heading<0)
        }
        compose.waitForIdle();capture("3d-reverse-turn")
        compose.onNodeWithTag("look_right").performClick();compose.waitForIdle();capture("3d-right")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        repeat(3) {
            compose.runOnIdle { visible=false };compose.waitForIdle()
            compose.runOnIdle { visible=true };ready()
        }
        capture("3d-reentered")
        compose.onNodeWithText("3D 화면 표시 중").assertExists()
    }
    private fun imageSignature(): List<Int> = compose.runOnIdle {
        val texture=compose.activity.window.decorView.findViewWithTag<TextureView>("parking3d_surface")
        val bitmap=texture?.getBitmap(80,45) ?: return@runOnIdle emptyList()
        val colors=ArrayList<Int>()
        for(y in 2 until 45 step 4) for(x in 2 until 80 step 4) colors+=bitmap.getPixel(x,y)
        bitmap.recycle();colors
    }
    @Test fun everyVehicleAndParkingModeDrawsBothReverseCameras() {
        var selectedCar by mutableStateOf(CarPresets.GRANDEUR)
        var selectedMode by mutableStateOf(ParkingMode.PERPENDICULAR)
        var selectedCamera by mutableStateOf(CarCamera.REAR_ONLY)
        compose.setContent {
            val sim=remember(selectedCar,selectedMode) {
                val map=when(selectedMode) {
                    ParkingMode.PERPENDICULAR->ParkingMaps.perpendicular()
                    ParkingMode.PARALLEL->ParkingMaps.parallel()
                    ParkingMode.DIAGONAL->ParkingMaps.diagonal()
                }
                ParkingSimulator(selectedCar,map)
            }
            RealisticParkingView(sim,{0},{Direction.REVERSE},{true},Modifier.fillMaxSize(),carCamera=selectedCamera)
        }
        for(car in CarPresets.all) for(mode in ParkingMode.entries) {
            compose.runOnIdle { selectedCar=car;selectedMode=mode;selectedCamera=CarCamera.REAR_ONLY }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("3D 화면 표시 중").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(15_000) { imageSignature().toSet().size>6 }
            val cockpit=imageSignature()
            compose.onNodeWithTag("expand_rear_camera").performClick()
            compose.waitUntil(15_000) { val image=imageSignature();image.toSet().size>6 && image!=cockpit }
            val rear=imageSignature()
            compose.runOnIdle { selectedCamera=CarCamera.SURROUND }
            compose.waitUntil(15_000) { val image=imageSignature();image.toSet().size>6 && image!=rear }
            compose.onNodeWithTag("expand_rear_camera").performClick()
            compose.waitForIdle()
        }
    }

}
