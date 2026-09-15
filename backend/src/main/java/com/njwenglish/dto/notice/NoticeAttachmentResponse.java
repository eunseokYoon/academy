package com.njwenglish.dto.notice;

import com.njwenglish.entity.NoticeAttachment;

/**
 * <b>s3Key를 내려주지 않는다.</b> 다운로드는 별도 엔드포인트가 권한을 다시 확인한 뒤
 * presigned URL을 발급한다.
 */
public record NoticeAttachmentResponse(Long attachmentId, String fileName, Long bytes) {

    public static NoticeAttachmentResponse from(NoticeAttachment attachment) {
        return new NoticeAttachmentResponse(
            attachment.getId(), attachment.getFileName(), attachment.getBytes());
    }
}
