package com.njwenglish.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 허용 여부는 <b>확장자</b>로 판정한다. hwp·hwpx·zip은 브라우저가 보내는 MIME이 제각각이라
 * Content-Type을 믿을 수 없다. 그래서 요청에 contentType이 없고, 서버가 정해 내려준다.
 */
public record MaterialUploadUrlRequest(
    @NotBlank String fileName,
    @NotNull @Positive Long bytes
) {
}
