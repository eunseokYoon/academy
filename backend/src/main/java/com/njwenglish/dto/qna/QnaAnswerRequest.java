package com.njwenglish.dto.qna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QnaAnswerRequest(
    @NotBlank String content,
    @Size(max = 5) List<String> s3Keys) {

    public List<String> s3KeysOrEmpty() {
        return s3Keys == null ? List.of() : s3Keys;
    }
}
