package com.njwenglish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 감사 컬럼이 없는 유일한 엔티티다. first_viewed_at·last_viewed_at이 그 역할을 하므로
 * BaseCreatedEntity를 상속하지 않는다. 붙이면 ddl-auto=validate가 부팅을 막는다.
 *
 * <p>시청 구간 추적은 하지 않는다. 봤는지 여부와 누적 시간만 기록한다.
 */
@Entity
@Table(name = "lesson_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "first_viewed_at", nullable = false)
    private OffsetDateTime firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private OffsetDateTime lastViewedAt;

    @Column(name = "watch_seconds", nullable = false)
    private Integer watchSeconds;
}
