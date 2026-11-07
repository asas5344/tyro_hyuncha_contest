package com.team.hyuncha.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {

    private fun check(expected: VoiceCommand?, vararg sayings: String) =
        sayings.forEach { assertEquals("\"$it\"", expected, VoiceCommands.parse(it)) }

    @Test fun 방향() {
        check(VoiceCommand.REVERSE, "후진", "후진해 줘", "후진으로 바꿔", "뒤로", "뒤로 가")
        check(VoiceCommand.FORWARD, "전진", "전진해", "앞으로", "앞으로 가 줘")
    }

    @Test fun 다시_하기() = check(VoiceCommand.RESTART, "다시", "다시 하기", "처음부터", "처음부터 다시 할래", "재시작")

    @Test fun 미러와_앞_보기() {
        check(VoiceCommand.LOOK_LEFT, "왼쪽 미러", "왼쪽 사이드미러 보여 줘", "왼쪽", "좌측 미러")
        check(VoiceCommand.LOOK_RIGHT, "오른쪽 미러", "오른쪽 봐", "우측")
        check(VoiceCommand.LOOK_FRONT, "앞 봐", "앞을 봐", "앞으로 봐", "정면")
    }

    @Test fun 시점() {
        check(VoiceCommand.TOP_VIEW, "탑뷰", "탑 뷰로 보여 줘", "위에서 볼래")
        check(VoiceCommand.DRIVER_VIEW, "운전석", "운전석 시점", "1인칭")
    }

    @Test fun 다시_보기는_다시_하기와_구분() {
        check(VoiceCommand.SHOW_MINE, "다시 보기", "내 주행 보여 줘", "내 주행")
        check(VoiceCommand.SHOW_MODEL, "모범 주행", "모범 주행 다시 보여 줘", "정답 보여 줘")
        check(VoiceCommand.CLOSE, "닫기", "그만")
    }

    @Test fun 문장부호와_빈_말() {
        check(VoiceCommand.REVERSE, "후진!", "후진.")
        assertNull(VoiceCommands.parse(""))
        assertNull(VoiceCommands.parse("   "))
        assertNull(VoiceCommands.parse("오늘 날씨 어때"))
    }
}
