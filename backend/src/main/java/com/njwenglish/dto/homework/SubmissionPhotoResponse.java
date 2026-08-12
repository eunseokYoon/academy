package com.njwenglish.dto.homework;

/** url은 조회용 presigned URL이다(10분). 버킷은 비공개라 이 경로 말고는 열리지 않는다. */
public record SubmissionPhotoResponse(Long photoId, String url, Short sortOrder) {
}
