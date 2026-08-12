package com.njwenglish.dto.lesson;

import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.HomeworkKind;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * S-5(학생) · P-6(학부모) 공용 수업 상세.
 *
 * <p>선생님용 {@link LessonDetailResponse}와 다른 레코드다. 그쪽은 videoUrl 원본·미공개 여부
 * 같은 관리용 값을 담는다. 이름을 겹치게 하지 마라 — 한쪽을 다른 쪽으로 덮어쓰기 쉽다.
 *
 * <p><b>영상은 학생만 본다.</b> 학부모 응답은 videoId·embedUrl이 null이다.
 *
 * <p><b>이 레코드를 {@code new}로 직접 만들지 마라.</b> 아래 두 팩토리만 쓴다 —
 * {@link #forStudent}는 영상을 채우고 {@link #forParent}는 null로 고정한다.
 * 호출부마다 null을 손으로 넘기게 두면 언젠가 한 곳에서 값이 들어가고, 그 한 곳이
 * 학부모에게 영상을 노출한다. 대상별 분기는 이 파일 안에서만 일어나야 한다.
 *
 * <p>videoId·embedUrl은 저장하지 않고 videoUrl에서 파싱해 내려준다. 프론트가 URL 형식을
 * 판단하지 않게 하려는 것이다. videoId가 null이면 프론트는 영상 영역을 숨긴다 —
 * 학부모에게는 언제나 그렇게 보이고, 학생에게는 영상이 없는 수업일 때만 그렇다.
 *
 * <p>attendanceStatus가 null이면 아직 확정 전이다. 출석이 아니라 "미확인"이다.
 */
public record LessonReportResponse(
    Long lessonId,
    LocalDate lessonDate,
    String title,
    String classRoomName,
    String videoId,
    String embedUrl,
    String content,
    String keyPoints,
    String nextPreview,
    Homework homework,
    AttendanceStatus attendanceStatus
) {
    /**
     * 수업에 딸린 숙제가 없으면 null이다. 사진·피드백은 어느 쪽에도 오지 않는다.
     *
     * <p><b>description은 학부모에게 가지 않는다.</b> 학부모가 보는 건 "냈는지"까지고,
     * 그건 P-3 숙제 목록(ParentHomeworkResponse)이 지키는 규칙과 같아야 한다 —
     * 한쪽에서 막고 다른 쪽으로 흘리면 막은 의미가 없다.
     *
     * <p>여기도 {@code new} 대신 팩토리를 쓴다. forParent는 description을 인자로 받지 않는다.
     */
    /**
     * 수업에 걸린 숙제 한 건.
     *
     * <p><b>축이 둘이다.</b> {@code result}가 오프라인 채점(⭕🔺❌·null=미채점)이고
     * {@code submissionStatus}가 온라인 제출이다. GRID 숙제에서 ⭕를 받은 학생은
     * 온라인 제출을 하지 않아 status가 영원히 NOT_SUBMITTED다 —
     * <b>status만 보고 "미제출"을 그리면 다 해온 학생이 안 한 것으로 보인다.</b>
     * 화면은 kind가 GRID면 result로, ONLINE이면 status로 분기해라.
     *
     * <p>dueAt은 GRID에서 null일 수 있다(재제출을 열기 전). 마감 줄을 조건부로 그려라.
     */
    public record Homework(
        Long homeworkId,
        String title,
        String description,
        HomeworkKind kind,
        OffsetDateTime dueAt,
        HomeworkResult result,
        Short completionRate,
        boolean resolvedByResubmission,
        SubmissionStatus submissionStatus
    ) {
        public static Homework forStudent(Long homeworkId, String title, String description,
                                          HomeworkKind kind, OffsetDateTime dueAt,
                                          Submission submission) {
            return of(homeworkId, title, description, kind, dueAt, submission);
        }

        /** P-6. description 자리는 여기서 null로 고정된다. */
        public static Homework forParent(Long homeworkId, String title,
                                         HomeworkKind kind, OffsetDateTime dueAt,
                                         Submission submission) {
            return of(homeworkId, title, null, kind, dueAt, submission);
        }

        /** submission이 null인 경우가 있다 — 출제 뒤에 입반한 학생은 칸이 없다. */
        private static Homework of(Long homeworkId, String title, String description,
                                   HomeworkKind kind, OffsetDateTime dueAt,
                                   Submission submission) {
            return new Homework(homeworkId, title, description, kind, dueAt,
                submission == null ? null : submission.getResult(),
                submission == null ? null : submission.getCompletionRate(),
                submission != null && submission.isResolvedByResubmission(),
                submission == null ? null : submission.getStatus());
        }
    }

    /** S-5. 영상을 포함한다. */
    public static LessonReportResponse forStudent(Long lessonId, LocalDate lessonDate,
                                                  String title, String classRoomName,
                                                  String videoId, String embedUrl,
                                                  String content, String keyPoints,
                                                  String nextPreview, Homework homework,
                                                  AttendanceStatus attendanceStatus) {
        return new LessonReportResponse(lessonId, lessonDate, title, classRoomName,
            videoId, embedUrl, content, keyPoints, nextPreview, homework, attendanceStatus);
    }

    /**
     * P-6. <b>영상 자리는 여기서 null로 고정된다.</b> 인자로도 받지 않는다 —
     * 받으면 언젠가 누가 채워 넣는다.
     */
    public static LessonReportResponse forParent(Long lessonId, LocalDate lessonDate,
                                                 String title, String classRoomName,
                                                 String content, String keyPoints,
                                                 String nextPreview, Homework homework,
                                                 AttendanceStatus attendanceStatus) {
        return new LessonReportResponse(lessonId, lessonDate, title, classRoomName,
            null, null, content, keyPoints, nextPreview, homework, attendanceStatus);
    }
}
