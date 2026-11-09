package com.team.hyuncha.input

/**
 * 음성으로 할 수 있는 일.
 * 차를 실제로 움직이게 하는 브레이크는 안전을 위해 음성으로 조작하지 않음 (방향 선택·화면 조작만).
 * @param phrase 안내할 대표 말투. 에뮬레이터 차 안 엔진에서 잘 알아들은 말로 고름 (2026-11-09 녹음 파일로 확인)
 */
enum class VoiceCommand(val label: String, val phrase: String) {
    FORWARD("전진", "앞으로 갈게"),
    REVERSE("후진", "후진해 줘"),
    TOGGLE_DIRECTION("반대 방향으로", "방향 바꿔 줘"),
    PARK_INFO("P단 안내", "P로 바꿔 줘"),
    RESTART("다시 하기", "다시 하기"),
    LOOK_LEFT("왼쪽 미러", "왼쪽 미러"),
    LOOK_RIGHT("오른쪽 미러", "오른쪽 미러"),
    LOOK_FRONT("앞 보기", "앞을 보여 줘"),
    TOP_VIEW("탑뷰", "위에서 보기"),
    DRIVER_VIEW("운전석 시점", "운전석 시점"),
    SHOW_MODEL("모범 주행 보기", "모범 주행"),
    SHOW_MINE("내 주행 보기", "내 주행"),
    CLOSE("닫기", "그만 보기"),
}

/**
 * 음성 인식 결과(글자)를 명령으로 바꿈. Pleos SpeechToText는 말을 글자로만 바꿔 주고 동작은 앱이 정해야 함.
 * 띄어쓰기·문장부호를 지우고 핵심 단어가 들어 있는지로 판단해서 "후진해 줘", "후진으로 바꿔" 같은 말도 알아들음.
 */
object VoiceCommands {

    /**
     * 화면에 보여줄 예시. 한 단어("후진"→"효진", "전진"→"1000진", "탑뷰"→"삽교", "닫기"→"세탁기")보다
     * 두 단어 이상의 대표 말투가 정확함
     */
    val examples = listOf(VoiceCommand.REVERSE, VoiceCommand.FORWARD, VoiceCommand.RESTART, VoiceCommand.LOOK_LEFT, VoiceCommand.TOP_VIEW)
        .map { it.phrase }

    // 위에서부터 먼저 맞는 것을 고름. 겹치는 말은 더 구체적인 것을 위에 둠
    // (예: "다시 보기 끝"은 닫기, "다시 보기"는 내 주행, "다시"는 다시 하기 / "앞으로 봐"는 앞 보기, "앞으로"는 전진)
    // 영어 글자는 소문자로 바꿔서 비교 (엔진이 "r로", "p로"처럼 소문자로 적음)
    // 엔진이 자주 잘못 듣는 말도 같이 받음 (2026-11-09 녹음 파일로 확인):
    //   "후진"→"효진", "전진"→"천진·1000진·선진", "기어"→"기억·기아"
    // 주의: "디로(D로)"는 "뒤로"로 들려서 후진이 됨 → 화면 안내는 "앞으로 갈게"를 권함
    private val rules: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.SHOW_MODEL to listOf("모범", "정답", "예시", "시범", "어떻게해", "어떻게하는", "잘하는법"),
        VoiceCommand.CLOSE to listOf("보기끝", "그만보기", "끝내", "종료"),
        VoiceCommand.SHOW_MINE to listOf("내주행", "다시보기", "다시보여", "리플레이", "내기록", "방금거", "돌려봐", "되돌려"),
        VoiceCommand.LOOK_FRONT to listOf("앞봐", "앞을봐", "앞으로봐", "앞쪽봐", "앞에봐", "앞보여", "앞을보여", "앞으로보여",
            "앞보기", "정면", "전방", "원래대로"),
        VoiceCommand.LOOK_LEFT to listOf("왼쪽", "좌측", "왼편"),
        VoiceCommand.LOOK_RIGHT to listOf("오른쪽", "우측", "오른편"),
        VoiceCommand.TOP_VIEW to listOf("탑뷰", "톱뷰", "위에서", "하늘에서", "조감", "지도", "전체보기", "버드뷰"),
        VoiceCommand.DRIVER_VIEW to listOf("운전석", "1인칭", "일인칭", "운전자시점", "차안", "실내"),
        VoiceCommand.PARK_INFO to listOf("p로", "p단", "p기어", "기어p", "피단", "파킹", "주차기어"),
        VoiceCommand.REVERSE to listOf("후진", "효진", "뒤로", "뒤쪽으로", "리버스", "r로", "r단", "r기어", "기어r", "알단", "빠꾸"),
        VoiceCommand.FORWARD to listOf("전진", "천진", "1000진", "선진", "앞으로", "앞쪽으로", "드라이브",
            "d로", "d단", "d기어", "기어d", "디단"),
        VoiceCommand.TOGGLE_DIRECTION to listOf("방향바꿔", "방향전환", "방향변경", "기어바꿔", "기어변경", "기어전환",
            "기억바꿔", "기아바꿔", "반대로", "반대방향"),
        VoiceCommand.RESTART to listOf("다시", "처음부터", "재시작", "리셋", "새로", "한번더", "리트라이"),
        VoiceCommand.CLOSE to listOf("닫기", "닫아", "그만", "취소"),
    )

    /** 못 알아들으면 null */
    fun parse(text: String): VoiceCommand? {
        val t = text.replace(Regex("[\\s.,!?~]"), "").lowercase()
        if (t.isEmpty()) return null
        return rules.firstOrNull { (_, words) -> words.any { it in t } }?.first
    }
}
