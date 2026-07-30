package com.njwenglish.dto.homework;

/**
 * 사진·영상 공용. uploadUrl은 5분짜리다. 만료되면 발급부터 다시 한다.
 *
 * <p>클라이언트는 발급 때 보낸 contentType과 같은 Content-Type 헤더로 PUT해야 한다.
 * 헤더가 다르면 서명이 어긋나 403이 난다.
 *
 * <p>만료는 요청이 <b>시작될 때</b> 검사되므로, 100MB 영상 업로드가 5분을 넘겨도
 * 5분 안에 시작만 했다면 끝까지 올라간다.
 */
public record MediaUploadUrlResponse(String uploadUrl, String s3Key) {
}
