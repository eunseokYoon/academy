package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import com.njwenglish.entity.enums.ChangeRequestStatus;
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
 * 수업일 변경 요청. 학생이 "이번 주 우리 반 수업 대신 같은 주 다른 반 수업에 가겠다"고 낸다.
 *
 * <p><b>승인해도 배정(enrollments)도 수업(lessons)도 바뀌지 않는다.</b> 승인의 결과물은
 * 개인 공지 한 건뿐이다. 실제 반 이동으로 만들면 출석·숙제·성적이 전부 따라 움직여야 하고
 * 되돌릴 방법이 없다. 원래 반 출석부에는 그 날이 그대로 남으니 선생님이 출석 확정할 때
 * 손으로 처리한다.
 *
 * <p>이 설계를 "미완성"으로 보고 나중에 실제 이동을 붙이지 마라. 확정된 범위다.
 */
@Entity
@Table(name = "lesson_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonChangeRequest extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** 못 가는 내 수업 회차. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_lesson_id", nullable = false)
    private Lesson fromLesson;

    /** 대신 갈 다른 반 수업 회차. 같은 주(월~일) 안이어야 한다 — 서비스에서 검증한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_lesson_id", nullable = false)
    private Lesson toLesson;

    /**
     * 변경 사유. 필수이고 <b>승인 공지 본문에 그대로 들어간다.</b>
     *
     * <p>클리닉 변경(reason_code)과 달리 선택지를 두지 않았다. 옵션 목록이 미확정이라
     * 값을 지어내지 않는다. 확정되면 그때 select로 바꾼다.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Teacher decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    /** 승인 시 발행된 공지. 선생님이 그 공지를 지우면 NULL이 된다(ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notice notice;

    public static LessonChangeRequest create(Student student, Lesson fromLesson, Lesson toLesson,
                                             String reason) {
        LessonChangeRequest request = new LessonChangeRequest();
        request.student = student;
        request.fromLesson = fromLesson;
        request.toLesson = toLesson;
        request.reason = reason;
        request.status = ChangeRequestStatus.PENDING;
        return request;
    }

    /** 승인일 때만 notice가 채워진다. 거절이면 아무에게도 알리지 않는다. */
    public void decide(boolean approve, Teacher teacher, Notice notice, OffsetDateTime now) {
        this.status = approve ? ChangeRequestStatus.APPROVED : ChangeRequestStatus.REJECTED;
        this.decidedBy = teacher;
        this.decidedAt = now;
        this.notice = notice;
    }

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }
}
