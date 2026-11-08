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

    /** 에뮬레이터 엔진이 실제로 돌려준 글자 (녹음 파일로 확인) */
    @Test fun 엔진이_잘못_들은_말도_알아듣는다() {
        check(VoiceCommand.REVERSE, "효진", "후진해져", "후진 기억")
        check(VoiceCommand.FORWARD, "1000진", "선진 기억", "앞으로 갈게", "드라이브 기어")
        check(VoiceCommand.LOOK_FRONT, "앞으로 보여줘")      // "앞을 보여 줘"가 이렇게 들림 → 전진이 되면 안 됨
        check(VoiceCommand.CLOSE, "그만 보기")
    }

    @Test fun 기어로_말해도_알아듣는다() {
        check(VoiceCommand.FORWARD, "기어 D로 바꿔 줘", "D단", "드라이브로 바꿔 줘", "전진 기어 넣어 줘", "앞쪽으로")
        check(VoiceCommand.REVERSE, "기어 R로 바꿔 줘", "R단 넣어 줘", "후진 기어 넣어 줘", "빠꾸", "뒤쪽으로")
        check(VoiceCommand.PARK_INFO, "P로 바꿔 줘", "P단", "파킹", "주차 기어")
        check(VoiceCommand.TOGGLE_DIRECTION, "방향 바꿔 줘", "기어 바꿔 줘", "기어 변경", "반대로", "방향 전환")
    }

    /** 에뮬레이터 엔진이 기어 말을 실제로 적어 준 글자 (2026-11-09 녹음 파일로 확인) */
    @Test fun 엔진이_적어_준_기어_말() {
        check(VoiceCommand.REVERSE, "기아 r로 바꿔 줘", "후진 기억이 넣어야죠")
        check(VoiceCommand.PARK_INFO, "p로 바꿔 줘", "파킹")
        check(VoiceCommand.TOGGLE_DIRECTION, "방향 바꿔 줘")
        check(VoiceCommand.RESTART, "한 번 더", "처음부터 다시")
        // "디로 바꿔"(D로)는 엔진이 "뒤로 바꿔"로 적음 → 말 그대로 후진. 그래서 화면 안내는 "앞으로 갈게"를 권함
        check(VoiceCommand.REVERSE, "뒤로 바꿔")
    }

    @Test fun 비슷한_말끼리_헷갈리지_않는다() {
        check(VoiceCommand.CLOSE, "다시 보기 끝", "그만 보기", "끝내기")
        check(VoiceCommand.SHOW_MINE, "다시 보기", "방금 거 돌려 봐")
        check(VoiceCommand.RESTART, "다시", "다시 할래", "한 번 더")
        check(VoiceCommand.LOOK_FRONT, "앞으로 봐", "원래대로", "정면 보기")
        check(VoiceCommand.FORWARD, "앞으로", "앞으로 가")
    }

    @Test fun 대표_말투는_모두_자기_명령으로_해석된다() {
        VoiceCommand.entries.forEach { assertEquals(it.phrase, it, VoiceCommands.parse(it.phrase)) }
    }

    @Test fun 문장부호와_빈_말() {
        check(VoiceCommand.REVERSE, "후진!", "후진.")
        assertNull(VoiceCommands.parse(""))
        assertNull(VoiceCommands.parse("   "))
        assertNull(VoiceCommands.parse("오늘 날씨 어때"))
    }
}
