package com.njwenglish.dto.material;

/**
 * 클라이언트는 이 uploadUrl에 <b>여기 내려준 contentType 그대로</b> PUT해야 한다.
 * 헤더가 다르면 서명이 어긋나 403이 난다.
 *
 * <p>같은 파일을 여러 반에 줄 때는 <b>S3에 한 번만 올리고 이 s3Key를 공유</b>해
 * POST /teacher/materials를 반 수만큼 호출한다. 서명이 자료 id가 아니라 선생님에게
 * 묶여 있어 재사용이 가능하다.
 */
public record MaterialUploadUrlResponse(String uploadUrl, String s3Key, String contentType) {
}
