package com.njwenglish.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * created_at·updated_at이 둘 다 NOT NULL인 테이블용.
 *
 * <p>Attendance는 이걸 상속하면 안 된다. attendances.updated_at은 감사 타임스탬프가 아니라
 * 출석 정정 시각이고, 정정이 없으면 NULL이어야 한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
