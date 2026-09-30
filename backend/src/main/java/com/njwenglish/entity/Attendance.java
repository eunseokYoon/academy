package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import com.njwenglish.entity.enums.AttendanceStatus;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BaseTimeEntity를 상속하면 안 된다. updated_at이 감사 타임스탬프가 아니라
 * 출석 정정 시각이라서, 정정이 없으면 NULL이어야 한다.
 * {@code @LastModifiedDate}를 붙이면 저장할 때마다 채워져 "정정된 적 없음"을 표현할 수 없다.
 */
@Entity
@Table(name = "attendances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 보강·자습처럼 정규 수업일이 없는 출석을 대비해 nullable이다. 1차에서는 항상 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** lessons.lesson_date를 복사한 비정규화 컬럼. 캘린더 조회에서 조인을 피하려는 것이다. */
    @Column(name = "attend_date", nullable = false)
    private LocalDate attendDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by")
    private Teacher checkedBy;

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Teacher updatedBy;

    /** nullable. 자동 갱신 금지 — 정정할 때만 채운다. */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** 정정할 때만 호출한다. */
    public void correct(AttendanceStatus status, String memo, Teacher teacher) {
        this.status = status;
        this.memo = memo;
        this.updatedBy = teacher;
        this.updatedAt = OffsetDateTime.now();
    }
}
