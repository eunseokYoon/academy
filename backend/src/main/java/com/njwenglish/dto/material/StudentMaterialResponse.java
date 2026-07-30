package com.njwenglish.dto.material;

import com.njwenglish.entity.Material;
import com.njwenglish.entity.enums.MaterialCategory;
import java.time.OffsetDateTime;

/**
 * S-8 학생 목록. <b>s3Key와 visibility·반 정보를 넣지 않는다.</b>
 * s3Key는 내부 저장 경로이고, 공개 범위는 학생이 알 필요가 없다
 * (자기에게 보이는 것만 목록에 있다).
 *
 * <p>다운로드는 별도 엔드포인트에서 presigned URL로 받는다. URL을 목록에 미리 담으면
 * 20개가 5분 뒤 전부 만료된 링크가 되고, 화면을 열어두면 못 받는다.
 */
public record StudentMaterialResponse(
    Long materialId,
    String title,
    MaterialCategory category,
    String fileName,
    Long bytes,
    Short year,
    Short month,
    Short week,
    OffsetDateTime createdAt
) {
    public static StudentMaterialResponse from(Material material) {
        return new StudentMaterialResponse(
            material.getId(), material.getTitle(), material.getCategory(),
            material.getFileName(), material.getBytes(),
            material.getYear(), material.getMonth(), material.getWeek(),
            material.getCreatedAt());
    }
}
