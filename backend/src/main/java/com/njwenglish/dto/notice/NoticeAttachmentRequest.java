package com.njwenglish.dto.notice;

import jakarta.validation.constraints.NotBlank;

/** 클라이언트가 S3에 올린 뒤 등록하는 값. s3Key는 서버가 서명을 대조한다. */
public record NoticeAttachmentRequest(
    @NotBlank String s3Key,
    @NotBlank String fileName,
    Long bytes
) {
}
