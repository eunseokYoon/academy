package com.njwenglish.dto.material;

import com.njwenglish.entity.Material;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.entity.enums.MaterialVisibility;
import java.time.OffsetDateTime;

/**
 * T-9 선생님 목록. 공개 범위를 보여줘야 하므로 visibility·classRoom이 함께 나간다.
 *
 * <p><b>s3Key는 여기에도 넣지 않는다.</b> 선생님 화면이라 위험이 낮아 보이지만,
 * 한 DTO에 넣으면 다음 사람이 학생 DTO에서도 따라 넣는다.
 */
public record MaterialResponse(
    Long materialId,
    String title,
    MaterialCategory category,
    String fileName,
    Long bytes,
    MaterialVisibility visibility,
    Long classRoomId,
    String classRoomName,
    Short year,
    Short month,
    Short week,
    OffsetDateTime createdAt
) {
    public static MaterialResponse from(Material material) {
        return new MaterialResponse(
            material.getId(), material.getTitle(), material.getCategory(),
            material.getFileName(), material.getBytes(),
            material.getVisibility(),
            material.getClassRoom() == null ? null : material.getClassRoom().getId(),
            material.getClassRoom() == null ? null : material.getClassRoom().getName(),
            material.getYear(), material.getMonth(), material.getWeek(),
            material.getCreatedAt());
    }
}
