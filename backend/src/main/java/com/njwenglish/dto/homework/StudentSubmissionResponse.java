package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentSubmissionResponse(Long id,
                                        SubmissionStatus status,
                                        OffsetDateTime submittedAt,
                                        boolean isLate,
                                        List<SubmissionPhotoResponse> photos,
                                        /** 최대 1개. 없으면 null이다. */
                                        SubmissionVideoResponse video) {
}
