package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;

/** s3Key는 서버가 영상용으로 발급한 것이어야 한다. 사진용 키는 서명 대조에서 걸린다. */
public record VideoRegisterRequest(@NotBlank String s3Key, Integer bytes) {
}
