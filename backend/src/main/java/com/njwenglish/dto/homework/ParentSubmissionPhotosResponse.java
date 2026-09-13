package com.njwenglish.dto.homework;

import java.util.List;

/**
 * P-3에서 「사진 보기」를 눌렀을 때. <b>사진만 담는다</b> —
 * 숙제 내용(description)과 영상은 학부모에게 가지 않는다.
 */
public record ParentSubmissionPhotosResponse(Long homeworkId,
                                             String title,
                                             List<SubmissionPhotoResponse> photos) {
}
