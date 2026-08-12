package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "homework_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeworkTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "use_count", nullable = false)
    private Integer useCount;

    public static HomeworkTemplate create(Teacher teacher, String title, String description) {
        HomeworkTemplate template = new HomeworkTemplate();
        template.teacher = teacher;
        template.title = title;
        template.description = description;
        template.useCount = 0;
        return template;
    }

    /** 목록이 use_count DESC 정렬이라 자주 쓰는 숙제가 위로 올라온다. */
    public void increaseUseCount() {
        this.useCount = this.useCount + 1;
    }
}
