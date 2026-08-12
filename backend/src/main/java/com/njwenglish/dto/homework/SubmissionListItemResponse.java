package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;

/**
 * T-7 격자 한 칸. thumbnailUrl은 첫 사진의 조회용 presigned URL이고 미제출이면 null이다.
 *
 * <p>studentName은 students.name이다. 미가입 학생도 이름이 나와야 한다.
 */
public record SubmissionListItemResponse(Long submissionId,
                                         Long studentId,
                                         String studentName,
                                         SubmissionStatus status,
                                         OffsetDateTime submittedAt,
                                         boolean isLate,
                                         int photoCount,
                                         String thumbnailUrl,
                                         /**
                                          * 영상은 썸네일을 만들 수 없다(트랜스코딩 없음).
                                          * 격자에서는 아이콘으로만 표시한다.
                                          */
                                         boolean hasVideo) {
}
