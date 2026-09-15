package com.njwenglish.dto.qna;

/** url은 조회용 presigned URL이다. 버킷은 비공개라 이 경로 말고는 열리지 않는다. */
public record QnaPhotoResponse(Long photoId, String url) {
}
