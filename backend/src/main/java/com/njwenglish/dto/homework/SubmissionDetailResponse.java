package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-7 상세 뷰어. <b>보기 전용이다</b> — 확인·피드백 단계가 없다.
 * prev·next가 핵심이다 — 목록으로 돌아가지 않고 연속으로 넘긴다.
 * 둘 다 <b>제출한(SUBMITTED)</b> 것 중 앞뒤를 가리킨다.
 */
public record SubmissionDetailResponse(Long submissionId,
                                       Long studentId,
                                       String studentName,
                                       SubmissionStatus status,
                                       OffsetDateTime submittedAt,
                                       boolean isLate,
                                       List<SubmissionPhotoResponse> photos,
                                       SubmissionVideoResponse video,
                                       Long prevSubmissionId,
                                       Long nextSubmissionId) {
}
