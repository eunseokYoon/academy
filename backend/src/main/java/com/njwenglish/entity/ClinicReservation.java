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
import java.time.LocalTime;
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

    /**
     * 학생이 몇 시에 오는가. 클리닉 시간대(17:00~22:00) 안의 1시간 단위 값이고
     * 마지막 슬롯은 종료 1시간 전이다. 검사는 {@link Clinic#hasSlot}이 한다.
     */
    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

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
    public static ClinicReservation reserve(Clinic clinic, Student student, Teacher assignedBy,
                                            LocalTime arrivalTime) {
        ClinicReservation reservation = new ClinicReservation();
        reservation.clinic = clinic;
        reservation.student = student;
        reservation.assignedBy = assignedBy;
        reservation.arrivalTime = arrivalTime;
        reservation.status = ReservationStatus.RESERVED;
        return reservation;
    }

    /**
     * 같은 클리닉 안에서 도착 시각만 옮긴다. 선생님 승인은 없다(2026-08-10 확정) —
     * 대신 호출부가 사유와 함께 ClinicChangeLog를 남긴다. 그 기록이 유일한 대응책이므로
     * 이 메서드를 로그 없이 부르지 마라.
     *
     * <p><b>출결 기록을 함께 비운다</b>(2026-09-01). 출결 확정이 슬롯 단위가 되면서
     * 필요해졌다 — 17시 슬롯을 확정하며 결석 처리된 학생이 20시로 옮기면, 그 학생의
     * attend_status가 따라가 20시 슬롯이 <b>선생님이 확인한 적도 없는 학생을 포함한 채</b>
     * 「확정」으로 뜬다(슬롯 확정 판정이 「전원 attend_status가 채워짐」이라서다).
     * 옮긴 시각에 대해서는 아직 아무 판정도 받지 않은 상태가 맞다.
     *
     * <p>비우는 것을 되돌리지 마라. 되돌리면 선생님이 못 본 학생이 확정 명단에 섞인다.
     */
    public void changeArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
        this.attendStatus = null;
        this.memo = null;
        this.checkedBy = null;
        this.checkedAt = null;
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
