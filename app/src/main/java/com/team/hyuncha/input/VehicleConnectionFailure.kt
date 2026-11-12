package com.team.hyuncha.input

import ai.pleos.playground.vehicle.constant.ErrorCode

internal enum class VehicleStage { INIT, CAP, READ, SUB }

internal enum class VehicleSignal(val label: String) {
    VEHICLE("차량"), STEER("조향"), BRAKE("브레이크"), GEAR("기어"),
}

internal class VehicleConnectionFailure(
    stage: VehicleStage,
    signal: VehicleSignal,
    reason: String,
    override val message: String,
    cause: Exception? = null,
) : Exception(message, cause) {
    val code = "${stage.name}-${signal.name}-$reason"

    companion object {
        fun from(stage: VehicleStage, signal: VehicleSignal, error: Exception): VehicleConnectionFailure {
            // Vehicle 2.3.2의 배포 AAR은 예외 타입을 난독화하지만, getMessage()는 SDK 오류 이름을 반환한다.
            val reportedCode = error.message?.trim()?.removePrefix("Error code:")?.trim()
            val sdkCode = ErrorCode.entries.firstOrNull { it.name == reportedCode || it.id.toString() == reportedCode }
            val message = when (sdkCode) {
                ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE -> "차량에 연결할 수 없어요."
                ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY -> unsupportedMessage(signal)
                ErrorCode.ERROR_SECURITY_PERMISSION -> "차량 정보를 읽을 권한이 없어요."
                ErrorCode.ERROR_DRIVING_RESTRICTED -> "현재 차량 상태에서는 주차 연습을 시작할 수 없어요."
                ErrorCode.ERROR_EMPTY_RESULT -> missingValueMessage(signal)
                else -> if (error is SecurityException) "차량 정보를 읽을 권한이 없어요."
                    else "차량 연동 중 오류가 발생했어요."
            }
            val reason = sdkCode?.id?.toString() ?: if (error is SecurityException) "NO_PERMISSION" else "UNKNOWN"
            return VehicleConnectionFailure(stage, signal, reason, message, error)
        }

        fun unsupported(signal: VehicleSignal) =
            VehicleConnectionFailure(VehicleStage.CAP, signal, "NO_READ", unsupportedMessage(signal))

        fun missingValue(signal: VehicleSignal) =
            VehicleConnectionFailure(VehicleStage.READ, signal, "NO_VALUE", missingValueMessage(signal))

        fun timeout(stage: VehicleStage, signal: VehicleSignal) =
            VehicleConnectionFailure(stage, signal, "TIMEOUT", "차량의 응답이 없어요.")

        private fun unsupportedMessage(signal: VehicleSignal) =
            "이 차량에서는 주차 연습에 필요한 ${signal.label} 정보를 사용할 수 없어요."

        private fun missingValueMessage(signal: VehicleSignal) =
            "차량의 ${signal.label} 상태를 확인할 수 없어요."
    }
}
