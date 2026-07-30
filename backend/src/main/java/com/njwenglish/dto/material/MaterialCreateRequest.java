package com.njwenglish.dto.material;

import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.entity.enums.MaterialVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 공개 범위는 차원이 둘뿐이다. {@code PUBLIC}이면 classRoomId가 null, {@code CLASS}면 값이
 * 있어야 한다. 어긋나면 400이고, 서버가 통과시켜도 DB의 ck_materials_scope가 막는다.
 *
 * <p><b>year·month·week는 필수다.</b> T-9 화면이 주차를 먼저 고르고 그 주의 자료를 올리는
 * 구조라 선택한 값을 그대로 실어 보낸다. 서버가 날짜에서 계산하지 마라 — 달 경계에 걸친
 * 주는 세는 방식이 갈려서 자동 계산과 선생님의 인식이 어긋난다.
 *
 * <p>같은 파일을 여러 반에 주려면 <b>반마다 이 요청을 보낸다.</b> s3Key는 공유한다.
 */
public record MaterialCreateRequest(
    @NotBlank String title,
    @NotNull MaterialCategory category,
    @NotBlank String s3Key,
    @NotBlank String fileName,
    Long bytes,
    Long classRoomId,
    @NotNull MaterialVisibility visibility,
    @NotNull Short year,
    @NotNull @Min(1) @Max(12) Short month,
    @NotNull @Min(1) @Max(5) Short week
) {
}
