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

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

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
        return homework;
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
        return at.isAfter(dueAt);
    }
}
