package com.njwenglish.dto.qna;

/** 클라이언트는 uploadUrl에 발급 때와 같은 Content-Type으로 PUT하고, s3Key를 작성 요청에 담는다. */
public record QnaUploadUrlResponse(String uploadUrl, String s3Key) {
}
