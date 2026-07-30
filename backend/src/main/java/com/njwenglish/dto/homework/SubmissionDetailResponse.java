package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-7 상세 뷰어. prev·next가 핵심이다 — 목록으로 돌아가지 않고 200명을 연속으로 넘긴다.
 * next는 아직 확인하지 않은(SUBMITTED) 것 중 다음을 가리킨다.
 */
public record SubmissionDetailResponse(Long submissionId,
                                       Long studentId,
                                       String studentName,
                                       SubmissionStatus status,
                                       OffsetDateTime submittedAt,
                                       boolean isLate,
                                       List<SubmissionPhotoResponse> photos,
                                       SubmissionVideoResponse video,
                                       FeedbackResponse feedback,
                                       Long prevSubmissionId,
                                       Long nextSubmissionId) {
}
