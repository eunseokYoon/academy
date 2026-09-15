package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.HomeworkKind;
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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출제 시점에 대상 학생 전원의 submissions를 미리 만든다.
 * 제출할 때 만들면 미제출자를 매번 LEFT JOIN으로 역산해야 한다.
 */
@Entity
@Table(name = "homeworks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Homework extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** GRID에서는 "재제출 마감"이다. 재제출을 열기 전까지 null이다. */
    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HomeworkKind kind;

    /** GRID 열의 표시 순서. 열이 삭제돼도 재정렬하지 않는다 — 번호가 비어도 정렬은 동작한다. */
    @Column(name = "sort_order")
    private Short sortOrder;

    /** lesson은 선택이다. 연결해야 캘린더의 숙제 완료율 색띠가 계산된다. */
    public static Homework create(ClassRoom classRoom, Lesson lesson, Teacher teacher,
                                  String title, String description, OffsetDateTime dueAt) {
        Homework homework = new Homework();
        homework.classRoom = classRoom;
        homework.lesson = lesson;
        homework.teacher = teacher;
        homework.title = title;
        homework.description = description;
        homework.dueAt = dueAt;
        homework.kind = HomeworkKind.ONLINE;
        return homework;
    }

    /**
     * 그리드의 열 하나. 마감 없이 만들어진다 — 온라인 제출은 선생님이 채점한 뒤
     * 재제출을 열어야 비로소 생긴다.
     *
     * <p>description은 쓰지 않는다. 열 제목 한 줄이 전부다.
     */
    public static Homework gridColumn(ClassRoom classRoom, Lesson lesson, Teacher teacher,
                                      String title, short sortOrder) {
        Homework homework = new Homework();
        homework.classRoom = classRoom;
        homework.lesson = lesson;
        homework.teacher = teacher;
        homework.title = title;
        homework.dueAt = null;
        homework.kind = HomeworkKind.GRID;
        homework.sortOrder = sortOrder;
        return homework;
    }

    public boolean isGrid() {
        return kind == HomeworkKind.GRID;
    }

    /** 열 제목·순서만 바꾼다. GRID 전용이라 description은 건드리지 않는다. */
    public void renameColumn(String title, short sortOrder) {
        this.title = title;
        this.sortOrder = sortOrder;
    }

    /**
     * 재제출이 열려 있는가. 이 값이 재제출 대상 판정의 절반이다
     * (나머지 절반은 Submission.isResubmitTarget의 result 검사).
     */
    public boolean isResubmitOpen() {
        return isGrid() && dueAt != null;
    }

    /**
     * 재제출 열기. dueAt이 채워지는 순간부터 🔺·❌ 학생에게 제출 경로가 열린다.
     *
     * <p>제목·상세 내용을 함께 받는 이유는 <b>한 트랜잭션 안에서 같이 바뀌어야</b>
     * 하기 때문이다. 따로 두면 선생님이 마감만 열고 설명을 못 적은 상태가 생긴다.
     */
    public void openResubmit(OffsetDateTime dueAt, String title, String description) {
        this.dueAt = dueAt;
        this.title = title;
        this.description = description;
    }

    /** 잘못 연 열을 되돌린다. 이미 낸 학생이 있으면 호출부가 먼저 409로 막는다. */
    public void closeResubmit() {
        this.dueAt = null;
    }

    public void edit(String title, String description, Lesson lesson) {
        this.title = title;
        this.description = description;
        this.lesson = lesson;
    }

    /**
     * 마감은 늦추는 방향만이다. 앞당기면 이미 제출한 학생의 is_late를 전부 재계산해야 하는데,
     * 그 비용에 비해 쓸 일이 없다. 앞당기려면 삭제 후 재출제한다.
     */
    public boolean canExtendTo(OffsetDateTime newDueAt) {
        return !newDueAt.isBefore(dueAt);
    }

    public void extendDueAt(OffsetDateTime newDueAt) {
        this.dueAt = newDueAt;
    }

    public boolean isLateAt(OffsetDateTime at) {
        return dueAt != null && at.isAfter(dueAt);
    }
}
