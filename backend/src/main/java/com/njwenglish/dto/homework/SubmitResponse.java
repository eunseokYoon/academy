package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;

/** 마감 후 제출도 성공한다. 늦음은 차단이 아니라 isLate로 남는다. */
public record SubmitResponse(Long submissionId,
                             SubmissionStatus status,
                             OffsetDateTime submittedAt,
                             boolean isLate,
                             int photoCount) {
}
