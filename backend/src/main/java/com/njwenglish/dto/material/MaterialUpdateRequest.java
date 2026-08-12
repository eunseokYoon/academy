package com.njwenglish.dto.material;

import com.njwenglish.entity.enums.MaterialCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 제목·분류·주차만 고친다. <b>공개 범위와 파일은 여기서 못 바꾼다.</b>
 * 대상이 바뀌는 것은 사실상 다른 자료이고, 파일을 갈아치우면 이전 s3 객체가 고아로 남는다.
 * 둘 다 삭제 후 새로 올리는 것이 맞다.
 *
 * <p>null인 필드는 그대로 둔다.
 */
public record MaterialUpdateRequest(
    String title,
    MaterialCategory category,
    Short year,
    @Min(1) @Max(12) Short month,
    @Min(1) @Max(5) Short week
) {
}
