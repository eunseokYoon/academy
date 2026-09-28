package com.njwenglish.service.push;

/**
 * 푸시 트리거 9개(설계 2부). 문구·딥링크·묶기가 전부 여기 한 곳에 있다.
 *
 * <p><b>문구는 제목과 대상까지만이다.</b> 푸시는 DTO 경계를 우회하는 새 채널이라, 내용·점수·
 * 재제출 상세·사유를 한 줄이라도 담으면 화면에서 막아 둔 것이 그대로 샌다. 앱은 딥링크로
 * 화면을 열고 정상 API 로 다시 받는다.
 *
 * <p>학부모 문구에는 자녀 이름이 앞에 붙는다({@link #parentTitle}) — 형제·자매가 한 계정에
 * 붙을 수 있어 누구 얘기인지 없으면 쓸 수 없다.
 *
 * <p>{@code dailyKind} 가 있는 셋(숙제 채점·주차별 성적·출석 확정 둘)은 수신자당 하루 1건으로
 * 묶는다. 값은 {@code ck_push_daily_kind} 와 같이 고쳐라. <b>수업과 클리닉 출석을 한
 * kind 로 합치지 마라</b> — 같은 날 둘 다 있는 게 정상이라 뒤에 확정한 쪽이 사라진다.
 */
public enum PushTopic {

    /** #1·#7. 선생님 공지와 수업일·클리닉 변경의 자동 공지가 같은 경로다. */
    NOTICE(null, "새 공지가 올라왔어요", PushScreen.NOTICE, PushScreen.NOTICE),
    /** #2. 학생 본인 행동이라 학부모만 받는다. 선생님은 받지 않는다. */
    HOMEWORK_SUBMITTED(null, "숙제를 냈어요", null, PushScreen.HOMEWORK),
    /** #3 */
    HOMEWORK_GRADED("HOMEWORK_GRADED", "숙제가 채점됐어요",
        PushScreen.HOMEWORK, PushScreen.HOMEWORK),
    /** #4. 문구의 주차 라벨은 {@code MonthWeeks} 가 만든다. */
    WEEKLY_SCORE("WEEKLY_SCORE", "성적이 올라왔어요", PushScreen.SCORES, PushScreen.SCORES),
    /**
     * #5. 학부모만. 학부모에게는 온라인 테스트 화면이 없다 — 제출이 그 주차 클리닉 칸을
     * 채우므로 성적으로 데려간다. 학부모용 온라인 테스트 화면을 만들지 마라.
     */
    ONLINE_TEST_SUBMITTED(null, "온라인 테스트를 냈어요", null, PushScreen.SCORES),
    /** #6. 선생님 답글 → 질문한 학생만. 학부모 금지(게시판에 학부모 경로가 없다). */
    QNA_REPLY(null, "질문에 답글이 달렸어요", PushScreen.QNA, null),
    /** #8 수업 */
    ATTENDANCE_LESSON("ATTENDANCE_LESSON", "수업 출석이 확정됐어요",
        PushScreen.ATTENDANCE, PushScreen.SCHEDULE),
    /** #8 클리닉 */
    ATTENDANCE_CLINIC("ATTENDANCE_CLINIC", "클리닉 출석이 확정됐어요",
        PushScreen.ATTENDANCE, PushScreen.SCHEDULE),
    /** #9 */
    LESSON_PUBLISHED(null, "수업 내용이 올라왔어요", PushScreen.LESSON, PushScreen.REPORT);

    private final String dailyKind;
    private final String title;
    private final PushScreen studentScreen;
    private final PushScreen parentScreen;

    PushTopic(String dailyKind, String title, PushScreen studentScreen,
              PushScreen parentScreen) {
        this.dailyKind = dailyKind;
        this.title = title;
        this.studentScreen = studentScreen;
        this.parentScreen = parentScreen;
    }

    /** null 이면 묶지 않는다(행위당 한 번). */
    public String dailyKind() {
        return dailyKind;
    }

    /** null 이면 학생은 받지 않는다. */
    public PushScreen studentScreen() {
        return studentScreen;
    }

    /** null 이면 학부모는 받지 않는다. */
    public PushScreen parentScreen() {
        return parentScreen;
    }

    public String studentTitle(String label) {
        return label == null ? title : label + " " + title;
    }

    public String parentTitle(String childName, String label) {
        return childName + " · " + studentTitle(label);
    }
}
