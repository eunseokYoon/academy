package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.HomeworkKind;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * P-3 전용. 학부모는 <b>제목·채점 결과·제출 사진까지</b> 본다.
 *
 * <p>사진은 2026-09-10에 열었다(선생님 회의). 그 전까지는 "냈는지"까지였다.
 * <b>열린 것은 사진뿐이다</b> — description(숙제 내용)과 영상은 여전히 오지 않는다.
 * Task 12가 붙인 재제출 상세 내용이 학부모에게 새면 안 된다.
 *
 * <p>그래서 학생용 DTO를 재사용하지 마라. 필드가 겹쳐 보여도 별도 record로 둔다.
 * <b>여기에 description·video를 추가하지 마라.</b>
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
                                     boolean isLate,
                                     /** 0이면 화면이 「사진 보기」를 그리지 않는다. */
                                     int photoCount) {
}
