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

    /**
     * 반 전용 자료. 같은 파일을 여러 반에 주려면 반마다 이 메서드를 호출해 행을 만든다
     * (s3Key는 공유한다 — S3에 두 번 올릴 이유가 없다).
     */
    public static Material forClass(String title, MaterialCategory category, String s3Key,
                                    String fileName, Long bytes, ClassRoom classRoom,
                                    short year, short month, short week, Teacher uploadedBy) {
        Material material = base(title, category, s3Key, fileName, bytes,
            year, month, week, uploadedBy);
        material.classRoom = classRoom;
        material.visibility = MaterialVisibility.CLASS;
        return material;
    }

    /**
     * 반 제한 없는 자료. <b>"누구나"가 아니라 "로그인한 전체 재원생"이다.</b>
     * classRoom은 null이어야 한다 (ck_materials_scope).
     */
    public static Material forEveryone(String title, MaterialCategory category, String s3Key,
                                       String fileName, Long bytes,
                                       short year, short month, short week, Teacher uploadedBy) {
        Material material = base(title, category, s3Key, fileName, bytes,
            year, month, week, uploadedBy);
        material.visibility = MaterialVisibility.PUBLIC;
        return material;
    }

    /**
     * 공개 범위(visibility·classRoom)와 파일은 바꾸지 않는다. 대상이 바뀌는 것은
     * 사실상 다른 자료이고, 파일을 바꾸면 이전 s3 객체가 고아로 남는다.
     * 둘 다 삭제 후 새로 올리는 것이 맞다.
     */
    public void edit(String title, MaterialCategory category,
                     short year, short month, short week) {
        this.title = title;
        this.category = category;
        this.year = year;
        this.month = month;
        this.week = week;
    }

    private static Material base(String title, MaterialCategory category, String s3Key,
                                 String fileName, Long bytes,
                                 short year, short month, short week, Teacher uploadedBy) {
        Material material = new Material();
        material.title = title;
        material.category = category;
        material.s3Key = s3Key;
        material.fileName = fileName;
        material.bytes = bytes;
        material.year = year;
        material.month = month;
        material.week = week;
        material.uploadedBy = uploadedBy;
        return material;
    }
}
