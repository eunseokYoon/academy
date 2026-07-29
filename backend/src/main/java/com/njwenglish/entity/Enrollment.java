package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재원 여부의 기준이다. 반 학생 목록을 뽑을 때 students를 직접 조회하지 말고
 * 항상 joined_at <= 기준일 AND (left_at IS NULL OR left_at > 기준일) 조건을 함께 봐라.
 * 빠뜨리면 퇴원생이 출석부에 남아 결석으로 쌓인다.
 */
@Entity
@Table(name = "enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    /** null이면 재원 중. 퇴원·배정 해제 시 날짜를 넣는다 (행을 지우지 않는다). */
    @Column(name = "left_at")
    private LocalDate leftAt;

    public static Enrollment create(Student student, ClassRoom classRoom, LocalDate joinedAt) {
        Enrollment enrollment = new Enrollment();
        enrollment.student = student;
        enrollment.classRoom = classRoom;
        enrollment.joinedAt = joinedAt;
        return enrollment;
    }
}
