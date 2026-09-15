package com.njwenglish.dto.qna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * s3Keys는 upload-url로 미리 받아 S3에 올려 둔 키다. 등록 시 서명을 대조한다.
 * 비어 있어도 된다 — 사진 없는 질문이 기본이다.
 */
public record QnaQuestionCreateRequest(
    @NotNull Long classRoomId,
    @NotBlank @Size(max = 200) String title,
    @NotBlank String content,
    @NotNull Boolean isPublic,
    @Size(max = 5) List<String> s3Keys) {

    public List<String> s3KeysOrEmpty() {
        return s3Keys == null ? List.of() : s3Keys;
    }
}
