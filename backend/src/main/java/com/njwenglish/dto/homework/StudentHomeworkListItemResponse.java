package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.HomeworkKind;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * S-2 목록. remainingMinutes는 <b>서버가</b> 계산한다. 음수면 마감이 지난 것이다 —
 * 클라이언트 시계는 틀릴 수 있어서 프론트에서 계산하면 사람마다 남은 시간이 달라진다.
 *
 * <p>resubmitRequired가 제출 버튼을 그리는 유일한 근거다. false인 GRID 숙제는
 * 서버가 제출 경로를 전부 막는다.
 */
public record StudentHomeworkListItemResponse(Long homeworkId,
                                              String title,
                                              /**
                                               * 재제출 요청에 적힌 상세 내용. 없으면 null이다.
                                               * 학생이 목록에서 눌러 모달로 본다.
                                               * <b>학부모 응답에는 넣지 마라.</b>
                                               */
                                              String description,
                                              String classRoomName,
                                              HomeworkKind kind,
                                              LocalDate lessonDate,
                                              HomeworkResult result,
                                              Short completionRate,
                                              boolean resolvedByResubmission,
                                              boolean resubmitRequired,
                                              OffsetDateTime dueAt,
                                              SubmissionStatus status,
                                              boolean isLate,
                                              int photoCount,
                                              boolean hasVideo,
                                              Long remainingMinutes) {
}
