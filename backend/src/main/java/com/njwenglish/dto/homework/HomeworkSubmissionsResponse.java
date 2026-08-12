package com.njwenglish.dto.homework;

import java.util.List;

/** T-7. 반 단위(30명 내외)라 페이징하지 않는다. */
public record HomeworkSubmissionsResponse(HomeworkBriefResponse homework,
                                          HomeworkCountsResponse counts,
                                          List<SubmissionListItemResponse> items) {
}
