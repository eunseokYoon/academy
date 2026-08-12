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
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 학생이 고를 수 있는 도착 시각. <b>이 계산의 정본이다.</b>
     *
     * <p>시작 시각부터 1시간 간격이고, <b>마지막 슬롯은 종료 1시간 전</b>이다 —
     * 21시에 와서 22시에 끝나면 한 시간은 있는 셈이지만, 21시 반에 오면 30분뿐이다.
     * 17:00~22:00이면 17·18·19·20·21시 다섯 개다.
     *
     * <p>슬롯은 <b>클리닉 시작 시각에 붙는다.</b> 17:30 시작이면 17:30·18:30…이지
     * 18:00이 아니다. 정시로 맞추면 시작 직후 30분이 아무도 못 오는 구간이 된다.
     */
    public List<LocalTime> slots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime slot = startTime; ; slot = slot.plusHours(1)) {
            LocalTime slotEnd = slot.plusHours(1);
            /*
             * plusHours는 자정을 넘기면 00:00으로 되감긴다. 그러면 slotEnd가 무엇보다도
             * "이르다"고 나와서 22:00~23:59 클리닉에 23:00 슬롯이 생기고(실제로는 59분뿐),
             * 루프도 끝나지 않는다. 되감김 검사를 종료 시각 비교보다 먼저 해야 한다.
             */
            if (slotEnd.isBefore(slot) || slotEnd.isAfter(endTime)) {
                return slots;
            }
            slots.add(slot);
        }
    }

    /**
     * 학생이 보낸 도착 시각이 고를 수 있는 값인가. <b>서버가 반드시 검사한다</b> —
     * 화면에서 목록을 그려 주는 건 안내일 뿐이고 임의 시각이 그대로 올라올 수 있다.
     */
    public boolean hasSlot(LocalTime arrivalTime) {
        return arrivalTime != null && slots().contains(arrivalTime);
    }
}
