package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.HomeworkKind;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * P-3 전용. 학부모는 <b>제목과 채점 결과까지만</b> 본다.
 *
 * <p>학생용 DTO를 재사용하면 사진 URL·피드백·숙제 내용이 그대로 새어 나간다.
 * 그래서 필드가 겹쳐 보여도 별도 record로 둔다.
 * <b>여기에 photos·photoCount·thumbnailUrl·feedback·description을 추가하지 마라.</b>
 *
 * <p>resolvedByResubmission이 true면 화면에 "⭕ 재제출"로 뜬다.
 * 수업 때는 못 해왔지만 다시 냈다는 사실이 학부모에게 남는다(확정 사항).
 */
public record ParentHomeworkResponse(Long homeworkId,
                                     String title,
                                     String classRoomName,
                                     HomeworkKind kind,
                                     LocalDate lessonDate,
                                     HomeworkResult result,
                                     Short completionRate,
                                     boolean resolvedByResubmission,
                                     OffsetDateTime dueAt,
                                     SubmissionStatus status,
                                     boolean isLate) {
}
