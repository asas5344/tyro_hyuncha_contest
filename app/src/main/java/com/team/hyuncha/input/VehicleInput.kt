package com.team.hyuncha.input

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * SDK 조향값을 어떻게 해석할지.
 * SDK 문서는 "앞바퀴 각도, 왼쪽이 음수"라고 하지만, 에뮬레이터 Virtual Console에서 확인해 보니
 * 핸들 각도(±540도)가 그대로 오고 오른쪽으로 돌리면 음수였음 (2026-11-03 확인).
 * 실제 차에서는 다를 수 있어서 화면에서 바꿀 수 있게 둠.
 */
enum class SteeringUnit(val label: String) {
    STEERING_WHEEL("핸들 각도"),
    FRONT_WHEEL("앞바퀴 각도"),
}

/**
 * 실제 브레이크 페달 상태로 "가상 차를 세워 둘지"를 정하는 규칙. SDK 없이 테스트할 수 있게 따로 둠.
 * @param pedal SDK가 준 페달 상태. true = 밟음, false = 뗌, null = 아직 모름
 */
internal object BrakeGate {
    /** 세워 둘지: 아직 준비(브레이크를 한 번 밟음)가 안 됐거나, 페달이 "뗌"으로 확인되지 않았으면 정지 */
    fun holding(armed: Boolean, pedal: Boolean?): Boolean = !armed || pedal != false

    /** 준비 상태: 브레이크를 한 번이라도 밟으면 준비됨. 그 뒤로는 유지 */
    fun armedAfter(armed: Boolean, pedal: Boolean?): Boolean = armed || pedal == true
}

/**
 * 실제 차량(또는 Pleos 에뮬레이터)의 조향각·브레이크·기어를 받는 입력.
 * DrivingInput은 핸들 각도(오른쪽 양수)를 주기로 했으므로 SDK 값을 그 기준으로 바꿔서 줌.
 *
 * @param steeringRatio 지금 선택된 차종의 조향비 (차종을 바꾸면 값도 바뀌므로 람다로 받음)
 */
class VehicleInput(
    private val vehicle: Vehicle,
    private val steeringRatio: () -> Double,
) : DrivingInput {

    /** SDK에서 받은 조향값 그대로 (도) */
    var rawSteering by mutableDoubleStateOf(0.0); private set

    /** 기본값은 에뮬레이터에서 확인한 해석: 핸들 각도, 오른쪽이 음수 */
    var steeringUnit by mutableStateOf(SteeringUnit.STEERING_WHEEL)
    var invertSteering by mutableStateOf(true)

    /** 마지막으로 받은 기어. 아직 못 받았으면 null */
    var gear by mutableStateOf<VehicleGear?>(null); private set

    /** 마지막으로 받은 실제 브레이크 페달 상태. 아직 못 받았으면 null */
    var pedalPressed by mutableStateOf<Boolean?>(null); private set

    /**
     * 브레이크를 한 번 밟아야 연습 시작.
     * P단에서는 보통 브레이크를 안 밟고 있으니, 이게 없으면 화면에 들어오자마자 차가 굴러감.
     */
    var armed by mutableStateOf(false); private set

    /** 마지막 에러 메시지 (권한·서비스 문제 확인용) */
    var lastError by mutableStateOf<String?>(null); private set

    override val steeringDeg: Double
        get() {
            val v = if (invertSteering) -rawSteering else rawSteering
            return when (steeringUnit) {
                SteeringUnit.STEERING_WHEEL -> v
                SteeringUnit.FRONT_WHEEL -> v * steeringRatio()  // 앞바퀴 각도 → 핸들 각도
            }
        }
    override var direction by mutableStateOf(Direction.FORWARD)  // 전진/후진은 화면 버튼
    override val brakePressed: Boolean get() = BrakeGate.holding(armed, pedalPressed)

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            angle?.let { rawSteering = it.toDouble() }  // null이면 이전 값 유지
        }
        override fun onFailed(e: Exception) = fail("조향각", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = updatePedal(status)
        override fun onFailed(e: Exception) = fail("브레이크", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            this@VehicleInput.gear = gear
        }
        override fun onFailed(e: Exception) = fail("기어", e)
    }

    /**
     * 리스너 등록 + 현재 값 한 번 읽기.
     * vehicle.initialize() 직후에 불러도 됨 (SDK가 연결될 때까지 요청을 모아뒀다가 처리함)
     */
    @Suppress("DEPRECATION")  // Gear API는 deprecated 표시지만 2.3.2에서 동작함
    fun start() {
        val steering = vehicle.getSteeringWheel()
        val brake = vehicle.getBrake()
        val gearApi = vehicle.getGear()

        steering.getCurrentSteeringWheelAngle(
            onComplete = { angle -> angle?.let { rawSteering = it.toDouble() } },
            onFailed = { fail("조향각", it) },
        )
        brake.getCurrentBrakePedalStatus(onComplete = ::updatePedal, onFailed = { fail("브레이크", it) })
        gearApi.getCurrentGearSelectionState(onComplete = { gear = it }, onFailed = { fail("기어", it) })

        steering.registerSteeringWheelAngle(steeringListener)
        brake.registerBrakePedalStatus(brakeListener)
        gearApi.registerGearSelection(gearListener)
    }

    @Suppress("DEPRECATION")
    fun stop() {
        vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
        vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
        vehicle.getGear().unregisterGearSelection(gearListener)
    }

    /** 다시 하기: 다시 브레이크를 밟아야 시작 */
    fun reset() {
        direction = Direction.FORWARD
        armed = BrakeGate.armedAfter(false, pedalPressed)
    }

    private fun updatePedal(status: Boolean?) {
        pedalPressed = status
        armed = BrakeGate.armedAfter(armed, status)
    }

    private fun fail(what: String, e: Exception) {
        Log.e(TAG, "$what 읽기 실패: ${e.message}", e)
        lastError = "$what: ${e.message}"
    }

    companion object {
        private const val TAG = "VehicleInput"
    }
}
