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
 * 승인은 한 트랜잭션이다. 기존 예약을 MOVED로 바꾸고 목표 클리닉에 새 RESERVED 행을 만든다.
 * 목표 클리닉의 정원도 다시 확인해야 한다.
 */
@Entity
@Table(name = "clinic_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicChangeRequest extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private ClinicReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** null이면 취소 요청이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_clinic_id")
    private Clinic targetClinic;

    /**
     * 사유 선택값. <b>옵션 목록이 미확정이라 enum도 CHECK 제약도 만들지 않는다.</b>
     * 값을 그럴듯하게 지어내지 마라. 확정되면 V4에서 CHECK를 추가한다.
     */
    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "reason_note", columnDefinition = "TEXT")
    private String reasonNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Teacher decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    /** targetClinic이 null이면 취소 요청이다. reasonCode는 필수라 호출부에서 이미 검증돼 있어야 한다. */
    public static ClinicChangeRequest create(ClinicReservation reservation, Student student,
                                             Clinic targetClinic, String reasonCode,
                                             String reasonNote) {
        ClinicChangeRequest request = new ClinicChangeRequest();
        request.reservation = reservation;
        request.student = student;
        request.targetClinic = targetClinic;
        request.reasonCode = reasonCode;
        request.reasonNote = reasonNote;
        request.status = ChangeRequestStatus.PENDING;
        return request;
    }

    public void decide(boolean approve, Teacher teacher, OffsetDateTime now) {
        this.status = approve ? ChangeRequestStatus.APPROVED : ChangeRequestStatus.REJECTED;
        this.decidedBy = teacher;
        this.decidedAt = now;
    }

    public boolean isPending() {
        return status == ChangeRequestStatus.PENDING;
    }

    /** targetClinic이 없으면 시간 이동이 아니라 취소 요청이다. */
    public boolean isCancelRequest() {
        return targetClinic == null;
    }
}
