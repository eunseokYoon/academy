package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;

/**
 * P-3 전용. 학부모는 <b>했는지 여부만</b> 본다.
 *
 * <p>학생용 DTO를 재사용하면 사진 URL·피드백·숙제 내용이 그대로 새어 나간다.
 * 그래서 필드가 겹쳐 보여도 별도 record로 둔다. 여기에 photos·feedback·description을
 * 추가하지 마라.
 */
public record ParentHomeworkResponse(Long homeworkId,
                                     String title,
                                     String classRoomName,
                                     OffsetDateTime dueAt,
                                     SubmissionStatus status,
                                     boolean isLate,
                                     boolean checked) {
}
