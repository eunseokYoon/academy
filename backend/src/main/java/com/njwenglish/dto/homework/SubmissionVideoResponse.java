package com.njwenglish.dto.homework;

/**
 * 조회용 presigned URL(10분). 프론트는 &lt;video&gt;로 그대로 재생한다.
 *
 * <p>트랜스코딩을 하지 않으므로 아이폰 HEVC 원본은 일부 브라우저에서 재생되지 않을 수 있다.
 */
public record SubmissionVideoResponse(String url, Integer bytes) {
}
