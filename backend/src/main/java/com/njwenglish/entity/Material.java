package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.entity.enums.MaterialVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자료실은 학생(S-8) 전용이다. 학부모에게 노출하지 마라.
 *
 * <p>공개 범위는 visibility와 classRoom 둘뿐이다. PUBLIC이면 classRoom이 null,
 * CLASS면 not null이어야 한다 (ck_materials_scope가 DB에서 막는다).
 * 조회 쿼리에서 PUBLIC 분기를 빠뜨리면 전체 공개 자료가 아무에게도 안 보인다.
 */
@Entity
@Table(name = "materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialCategory category;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    private Long bytes;

    /** PUBLIC이면 null이어야 한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialVisibility visibility;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "week", nullable = false)
    private Short week;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private Teacher uploadedBy;
}
