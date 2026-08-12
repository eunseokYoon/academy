package com.njwenglish.dto.lesson;

import java.time.LocalDate;

/**
 * S-5(학생) · P-6(학부모) 공용 수업 목록.
 *
 * <p>선생님용 {@link LessonListItemResponse}와 다른 레코드다. 그쪽은 미작성·미공개처럼
 * 관리용 상태를 담는다. 이름을 겹치게 하지 마라.
 *
 * <p><b>hasVideo는 학부모 응답에서 null이다.</b> false가 아니라 null인 이유는
 * false가 거짓말이기 때문이다 — 영상은 있고 학부모가 못 볼 뿐이다. false로 채우면
 * "영상 없음"으로 그려지고, 나중에 누군가 그 값을 믿고 화면을 만든다.
 *
 * <p>viewed(시청 여부)는 2026-08-11에 없앴다. 시청 기록 기능 자체가 사라졌다.
 *
 * <p><b>{@code new}로 직접 만들지 마라.</b> 아래 두 팩토리만 쓴다.
 * 대상별 분기는 이 파일 안에서만 일어나야 한다.
 */
public record LessonReportListItemResponse(
    Long lessonId,
    LocalDate lessonDate,
    String title,
    String classRoomName,
    Boolean hasVideo,
    boolean isNew,
    String homeworkTitle
) {
    /** S-5. 영상 유무를 포함한다. */
    public static LessonReportListItemResponse forStudent(Long lessonId, LocalDate lessonDate,
                                                          String title, String classRoomName,
                                                          boolean hasVideo, boolean isNew,
                                                          String homeworkTitle) {
        return new LessonReportListItemResponse(lessonId, lessonDate, title, classRoomName,
            hasVideo, isNew, homeworkTitle);
    }

    /** P-6. <b>영상 관련 값은 여기서 null로 고정된다.</b> 인자로도 받지 않는다. */
    public static LessonReportListItemResponse forParent(Long lessonId, LocalDate lessonDate,
                                                         String title, String classRoomName,
                                                         boolean isNew, String homeworkTitle) {
        return new LessonReportListItemResponse(lessonId, lessonDate, title, classRoomName,
            null, isNew, homeworkTitle);
    }
}
