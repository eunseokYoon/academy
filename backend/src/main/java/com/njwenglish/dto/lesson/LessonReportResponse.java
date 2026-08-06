package com.njwenglish.dto.lesson;

import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * S-5(학생) · P-5(학부모) 공용 수업 상세.
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
    /** 수업에 딸린 숙제가 없으면 null이다. 사진·피드백은 어느 쪽에도 오지 않는다. */
    public record Homework(
        Long homeworkId,
        String title,
        String description,
        OffsetDateTime dueAt,
        SubmissionStatus submissionStatus
    ) {
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
     * P-5. <b>영상 자리는 여기서 null로 고정된다.</b> 인자로도 받지 않는다 —
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
