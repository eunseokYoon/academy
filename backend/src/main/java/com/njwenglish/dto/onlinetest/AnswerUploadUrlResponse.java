package com.njwenglish.dto.onlinetest;

/**
 * uploadUrl에 PUT할 때 Content-Type을 발급 요청과 <b>같은 값</b>으로 보내야 한다.
 * 헤더가 다르면 서명이 어긋나 403이 난다.
 */
public record AnswerUploadUrlResponse(String uploadUrl, String s3Key) {
}
