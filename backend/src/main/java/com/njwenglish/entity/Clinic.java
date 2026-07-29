package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ClinicStatus;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정규 수업과 별개인 보충 수업 시간대다. 한 시간대에 학생이 여러 명이라
 * 여기에 student_id를 둘 수 없다. 연결은 ClinicReservation이 담는다.
 *
 * <p>정원 체크는 이 행을 FOR UPDATE로 잠근 뒤에 해라.
 * "세어 보고 넣기"는 동시 신청 시 정원을 넘긴다.
 */
@Entity
@Table(name = "clinics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clinic extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "clinic_date", nullable = false)
    private LocalDate clinicDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** null이면 인원 제한 없음. 1회 정원은 미확정이라 기본값을 지어내지 마라. */
    private Short capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClinicStatus status;

    @Column(columnDefinition = "TEXT")
    private String memo;

    public static Clinic open(Teacher teacher, LocalDate clinicDate, LocalTime startTime,
                              LocalTime endTime, Short capacity, String memo) {
        Clinic clinic = new Clinic();
        clinic.teacher = teacher;
        clinic.clinicDate = clinicDate;
        clinic.startTime = startTime;
        clinic.endTime = endTime;
        clinic.capacity = capacity;
        clinic.memo = memo;
        clinic.status = ClinicStatus.OPEN;
        return clinic;
    }

    /** capacity를 null로 바꾸면 인원 제한 없음이다. 호출부가 "안 보냄"과 "null로 지정"을 구분해야 한다. */
    public void reschedule(LocalDate clinicDate, LocalTime startTime, LocalTime endTime,
                           Short capacity, String memo) {
        this.clinicDate = clinicDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.memo = memo;
    }

    public void changeStatus(ClinicStatus status) {
        this.status = status;
    }

    public boolean isOpen() {
        return status == ClinicStatus.OPEN;
    }

    /** capacity가 null이면 제한 없음이라 언제나 여유가 있다. */
    public boolean isFull(long reservedCount) {
        return capacity != null && reservedCount >= capacity;
    }
}
