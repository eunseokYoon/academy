package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ReservationStatus;
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
 * 클리닉 출석은 attendances가 아니라 여기 attendStatus에 기록한다.
 * attendances.class_room_id가 NOT NULL인데 클리닉은 반이 없다.
 */
@Entity
@Table(name = "clinic_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicReservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** null이면 학생 본인 신청(S-9), 값이 있으면 선생님 배정(T-13). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private Teacher assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /** 출석 확정 전에는 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attend_status", length = 20)
    private AttendanceStatus attendStatus;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by")
    private Teacher checkedBy;

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    /**
     * assignedBy가 null이면 학생 본인 신청(S-9), 값이 있으면 선생님 배정(T-13)이다.
     * 명단에서 "왜 여기 있냐"는 문의에 답하려면 이 구분이 남아야 한다.
     */
    public static ClinicReservation reserve(Clinic clinic, Student student, Teacher assignedBy) {
        ClinicReservation reservation = new ClinicReservation();
        reservation.clinic = clinic;
        reservation.student = student;
        reservation.assignedBy = assignedBy;
        reservation.status = ReservationStatus.RESERVED;
        return reservation;
    }

    /** 행을 지우지 않는다. 부분 유니크 인덱스가 RESERVED만 보므로 나중에 다시 신청할 수 있다. */
    public void cancel() {
        this.status = ReservationStatus.CANCELED;
    }

    /** 변경 요청 승인 시 기존 예약에 붙는다. 취소(CANCELED)와 구분해야 이력이 남는다. */
    public void moveOut() {
        this.status = ReservationStatus.MOVED;
    }

    /** 클리닉 출석 확정. attendances 테이블은 건드리지 않는다. */
    public void checkAttendance(AttendanceStatus attendStatus, String memo,
                                Teacher teacher, OffsetDateTime now) {
        this.attendStatus = attendStatus;
        this.memo = memo;
        this.checkedBy = teacher;
        this.checkedAt = now;
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public boolean isAssignedByTeacher() {
        return assignedBy != null;
    }
}
