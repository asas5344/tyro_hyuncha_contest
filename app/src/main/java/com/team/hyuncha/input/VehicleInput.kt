package com.team.hyuncha.input

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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
internal class VehicleInput(
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

    var connectionFailure by mutableStateOf<VehicleConnectionFailure?>(null); private set
    var ready by mutableStateOf(false); private set

    private val main = Handler(Looper.getMainLooper())
    private val unregisterListeners = mutableListOf<() -> Unit>()
    private var listening = false

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

    /** Compose의 메인 코루틴에서 호출하며, 지원 확인 후 리스너를 등록하고 초기값을 조회한다. */
    @Suppress("DEPRECATION")
    suspend fun start() {
        try {
            val steering = vehicle.getSteeringWheel()
            val brake = vehicle.getBrake()

            val steeringAccess = awaitVehicle<AccessType>(VehicleStage.CAP, VehicleSignal.STEER) { complete, fail ->
                steering.checkSteeringWheelAngleCapabilities({ config, _ -> complete(config.accessType) }, fail)
            }
            requireReadAccess(VehicleSignal.STEER, steeringAccess)
            val brakeAccess = awaitVehicle<AccessType>(VehicleStage.CAP, VehicleSignal.BRAKE) { complete, fail ->
                brake.checkBrakePedalStatusCapabilities({ config -> complete(config.accessType) }, fail)
            }
            requireReadAccess(VehicleSignal.BRAKE, brakeAccess)

            val gearApi = vehicle.getGear()
            var steeringEventReceived = false
            var brakeEventReceived = false
            var gearEventReceived = false
            listening = true
            val steeringListener = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) = postUpdate {
                    angle?.let {
                        steeringEventReceived = true
                        rawSteering = it.toDouble()
                    }
                }
                override fun onFailed(e: Exception) = subscriptionFailed(VehicleSignal.STEER, e)
            }
            val brakeListener = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) = postUpdate {
                    brakeEventReceived = true
                    updatePedal(status)
                }
                override fun onFailed(e: Exception) = subscriptionFailed(VehicleSignal.BRAKE, e)
            }
            val gearListener = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) = postUpdate {
                    gearEventReceived = true
                    this@VehicleInput.gear = gear
                }
                override fun onFailed(e: Exception) = subscriptionFailed(VehicleSignal.GEAR, e)
            }
            register(VehicleSignal.STEER, { steering.registerSteeringWheelAngle(steeringListener) }) {
                steering.unregisterSteeringWheelAngle(steeringListener)
            }
            register(VehicleSignal.BRAKE, { brake.registerBrakePedalStatus(brakeListener) }) {
                brake.unregisterBrakePedalStatus(brakeListener)
            }
            register(VehicleSignal.GEAR, { gearApi.registerGearSelection(gearListener) }) {
                gearApi.unregisterGearSelection(gearListener)
            }
            // 콜백이 초기 조회보다 먼저 도착하면 더 오래된 조회 결과로 덮어쓰지 않는다.
            val initialSteering = readInitial<Float>(VehicleSignal.STEER, steering::getCurrentSteeringWheelAngle)
            if (!listening) return
            if (!steeringEventReceived) rawSteering = initialSteering.toDouble()
            val initialBrake = readInitial<Boolean>(VehicleSignal.BRAKE, brake::getCurrentBrakePedalStatus)
            if (!listening) return
            if (!brakeEventReceived) updatePedal(initialBrake)
            val initialGear = readInitial<VehicleGear>(VehicleSignal.GEAR, gearApi::getCurrentGearSelectionState)
            if (!listening) return
            if (!gearEventReceived) gear = initialGear
            ready = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: VehicleConnectionFailure) {
            if (connectionFailure == null) showFailure(error)
        }
    }

    fun stop() {
        listening = false
        ready = false
        unregisterListeners.asReversed().forEach { unregister ->
            try {
                unregister()
            } catch (error: Exception) {
                Log.w(TAG, "차량 리스너 해제 실패", error)
            }
        }
        unregisterListeners.clear()
    }

    private fun register(signal: VehicleSignal, register: () -> Unit, unregister: () -> Unit) {
        unregisterListeners.add(unregister)
        try {
            register()
        } catch (error: Exception) {
            throw VehicleConnectionFailure.from(VehicleStage.SUB, signal, error)
        }
    }

    private fun postUpdate(update: () -> Unit) {
        main.post { if (listening) update() }
    }

    private fun subscriptionFailed(signal: VehicleSignal, error: Exception) = postUpdate {
        showFailure(VehicleConnectionFailure.from(VehicleStage.SUB, signal, error))
    }

    private fun showFailure(error: VehicleConnectionFailure) {
        connectionFailure = error
        stop()
        Log.e(TAG, error.code, error.cause)
    }

    private suspend fun <T : Any> readInitial(
        signal: VehicleSignal,
        request: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
    ): T = awaitVehicle(VehicleStage.READ, signal, request = request) ?: throw VehicleConnectionFailure.missingValue(signal)

    /** 다시 하기: 다시 브레이크를 밟아야 시작 */
    fun reset() {
        direction = Direction.FORWARD
        armed = BrakeGate.armedAfter(false, pedalPressed)
    }

    private fun updatePedal(status: Boolean?) {
        pedalPressed = status
        armed = BrakeGate.armedAfter(armed, status)
    }

    companion object {
        private const val TAG = "VehicleInput"
    }
}

internal fun requireReadAccess(signal: VehicleSignal, access: AccessType) {
    if (access != AccessType.READ && access != AccessType.READ_WRITE) {
        throw VehicleConnectionFailure.unsupported(signal)
    }
}

private data class VehicleResponse<T>(val value: T)

internal suspend fun <T> awaitVehicle(
    stage: VehicleStage,
    signal: VehicleSignal,
    timeoutMillis: Long = 5_000,
    request: ((T) -> Unit, (Exception) -> Unit) -> Unit,
): T {
    val response = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<VehicleResponse<T>> { continuation ->
            val completed = AtomicBoolean(false)
            val fail: (Exception) -> Unit = { error ->
                if (completed.compareAndSet(false, true)) {
                    continuation.resumeWithException(VehicleConnectionFailure.from(stage, signal, error))
                }
            }
            try {
                request({ value ->
                    if (completed.compareAndSet(false, true)) continuation.resume(VehicleResponse(value))
                }, fail)
            } catch (error: CancellationException) {
                continuation.cancel(error)
            } catch (error: Exception) {
                fail(error)
            }
        }
    } ?: throw VehicleConnectionFailure.timeout(stage, signal)
    return response.value
}
