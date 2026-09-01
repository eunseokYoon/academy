package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.dto.attendance.PendingClinicResponse;
import com.njwenglish.dto.clinic.ClinicBulkCreateRequest;
import com.njwenglish.dto.clinic.ClinicBulkCreateResponse;
import com.njwenglish.dto.clinic.ClinicCreateRequest;
import com.njwenglish.dto.clinic.ClinicCreateResponse;
import com.njwenglish.dto.clinic.ClinicListItemResponse;
import com.njwenglish.dto.clinic.ClinicUpdateRequest;
import com.njwenglish.dto.clinic.MyReservationResponse;
import com.njwenglish.dto.clinic.ParentClinicResponse;
import com.njwenglish.dto.clinic.StudentClinicResponse;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ClinicStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.ClinicReservationRepository.ClinicCount;
import com.njwenglish.repository.ClinicReservationRepository.SlotCount;
import com.njwenglish.repository.TeacherRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클리닉 시간대(T-13) 개설·수정·조회. 정규 수업과 별개인 보충 수업이고,
 * 한 시간대에 학생이 여러 명이라 clinics에 student_id가 없다.
 *
 * <p>신청·배정·출석은 ClinicReservationService, 변경 요청은
 * ClinicChangeRequestService가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class ClinicService {

    private final ClinicRepository clinicRepository;
    private final ClinicReservationRepository reservationRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional
    public ClinicCreateResponse create(ClinicCreateRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // uq_clinics_slot이 OPEN 부분 인덱스다. 닫은 시간대는 같은 슬롯을 다시 열 수 있다
        if (clinicRepository.existsByClinicDateAndStartTimeAndStatus(
            request.clinicDate(), request.startTime(), ClinicStatus.OPEN)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Clinic clinic = clinicRepository.save(Clinic.open(currentTeacher(),
            request.clinicDate(), request.startTime(), request.endTime(),
            validCapacity(request.capacity()), request.memo()));
        return ClinicCreateResponse.of(clinic, 0);
    }

    /**
     * 기간 안의 특정 요일에 클리닉을 한꺼번에 연다. {@code LessonService.bulkCreate}와 같은 규칙이다 —
     * <b>충돌하는 날짜는 건너뛰고 계속한다.</b> 전부 실패시키면 선생님이 걸린 날짜를 찾아
     * 지우고 다시 눌러야 한다.
     *
     * <p>슬롯이 하나도 안 나오는 시간대는 거절한다. ck_clinics_time은 end &gt; start만 보므로
     * 17:00~17:30 같은 값이 통과하는데, 그러면 학생이 고를 도착 시각이 없는 클리닉이 생긴다.
     */
    @Transactional
    public ClinicBulkCreateResponse bulkCreate(ClinicBulkCreateRequest request) {
        if (request.to().isBefore(request.from())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Teacher teacher = currentTeacher();
        Short capacity = validCapacity(request.capacity());
        DayOfWeek day = DayOfWeek.of(request.dayOfWeek());

        // 슬롯 판정은 Clinic.slots()가 정본이다. 여기서 시각 산술을 다시 하지 마라
        if (Clinic.open(teacher, request.from(), request.startTime(), request.endTime(),
            capacity, null).slots().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Set<LocalDate> skip = new HashSet<>(
            request.skipDates() == null ? List.<LocalDate>of() : request.skipDates());
        skip.addAll(clinicRepository.findOpenDates(
            request.from(), request.to(), request.startTime()));

        List<LocalDate> created = new ArrayList<>();
        int skipped = 0;
        for (LocalDate date = request.from();
             !date.isAfter(request.to());
             date = date.plusDays(1)) {
            if (date.getDayOfWeek() != day) {
                continue;
            }
            if (skip.contains(date)) {
                skipped++;
                continue;
            }
            clinicRepository.save(Clinic.open(teacher, date, request.startTime(),
                request.endTime(), capacity, request.memo()));
            created.add(date);
        }
        return new ClinicBulkCreateResponse(created.size(), skipped, created);
    }

    @Transactional
    public ClinicListItemResponse update(Long clinicId, ClinicUpdateRequest request) {
        Clinic clinic = findClinic(clinicId);

        LocalDate clinicDate = request.clinicDate() != null
            ? request.clinicDate() : clinic.getClinicDate();
        var startTime = request.startTime() != null ? request.startTime() : clinic.getStartTime();
        var endTime = request.endTime() != null ? request.endTime() : clinic.getEndTime();
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        // clearCapacity가 없으면 "제한 없음으로 바꾸기"와 "안 건드리기"를 구분할 수 없다
        Short capacity = request.clearCapacity()
            ? null
            : (request.capacity() != null ? validCapacity(request.capacity()) : clinic.getCapacity());

        clinic.reschedule(clinicDate, startTime, endTime, capacity,
            request.memo() != null ? request.memo() : clinic.getMemo());
        if (request.status() != null) {
            clinic.changeStatus(request.status());
        }

        long reservedCount = reservationRepository.countByClinicIdAndStatus(
            clinicId, ReservationStatus.RESERVED);
        List<SlotCount> slots = reservationRepository.countBySlot(List.of(clinicId));
        return ClinicListItemResponse.of(clinic, reservedCount,
            isAttendanceConfirmed(List.of(clinicId)).contains(clinicId),
            slots.size(), confirmedSlotCount(slots));
    }

    /**
     * 시간대 삭제. 신청자가 한 명이라도 있으면 지우지 말고 CLOSED로 닫아야 한다 —
     * 예약 행이 FK로 남아 있어 물리 삭제가 안 되고, 지워지면 학생 기록이 사라진다.
     */
    @Transactional
    public void delete(Long clinicId) {
        Clinic clinic = findClinic(clinicId);
        if (reservationRepository.countByClinicIdAndStatus(
            clinicId, ReservationStatus.RESERVED) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        clinicRepository.delete(clinic);
    }

    /**
     * T-5 미확정 클리닉. 출석 확정 화면에서 수업과 나란히 보여준다.
     *
     * <p>수업과 판정 기준이 다르다 — clinics에는 확정 컬럼이 없어서 예약의
     * attend_status가 비었는지로 본다. 신청자가 없는 시간대는 확정할 것이 없어 빠진다.
     */
    @Transactional(readOnly = true)
    public List<PendingClinicResponse> pendingAttendance() {
        List<Clinic> clinics = reservationRepository.findPendingUntil(LocalDate.now());
        Map<Long, Long> counts = reservedCounts(clinics);

        return clinics.stream()
            .map(clinic -> new PendingClinicResponse(
                clinic.getId(), clinic.getClinicDate(),
                clinic.getStartTime(), clinic.getEndTime(),
                counts.getOrDefault(clinic.getId(), 0L)))
            .toList();
    }

    /**
     * T-13 목록. 주차 하나라 페이징이 없다 — 한 주에 클리닉이 많아야 대여섯 개다.
     *
     * <p>주차 → 날짜 범위 변환은 {@link MonthWeeks}가 한다. 프론트에서 계산해 from·to로
     * 보내게 두면 "달 안에서 1일부터 7일씩" 규칙이 두 곳으로 갈라진다.
     */
    @Transactional(readOnly = true)
    public List<ClinicListItemResponse> listForTeacher(int year, int month, int week,
                                                       ClinicStatus status) {
        List<Clinic> clinics = clinicRepository.findInRange(
            MonthWeeks.startOf(year, month, week), MonthWeeks.endOf(year, month, week), status);
        Map<Long, Long> counts = reservedCounts(clinics);
        Set<Long> confirmed = isAttendanceConfirmed(ids(clinics));
        Map<Long, List<SlotCount>> slots = slotCountsByClinic(ids(clinics));

        return clinics.stream()
            .map(clinic -> {
                List<SlotCount> clinicSlots = slots.getOrDefault(clinic.getId(), List.of());
                return ClinicListItemResponse.of(clinic,
                    counts.getOrDefault(clinic.getId(), 0L),
                    confirmed.contains(clinic.getId()),
                    clinicSlots.size(), confirmedSlotCount(clinicSlots));
            })
            .toList();
    }

    /**
     * S-9. 신청 가능한 시간대와 본인 신청 현황을 함께 내려준다.
     *
     * <p><b>다른 학생 이름은 담지 않는다.</b> 인원 수만이다.
     * 닫힌 시간대는 목록에서 빼되, 본인이 이미 예약한 것은 남긴다 —
     * 안 그러면 학생이 자기 예약을 볼 곳이 없어진다.
     */
    @Transactional(readOnly = true)
    public List<StudentClinicResponse> listForStudent(LocalDate from, LocalDate to) {
        Student student = studentAccessGuard.requireSelf();
        List<Clinic> clinics = clinicRepository.findInRange(from, to, null);
        Map<Long, Long> counts = reservedCounts(clinics);

        Map<Long, ClinicReservation> mine = new HashMap<>();
        List<ClinicReservation> reservations =
            reservationRepository.findReservedByStudentInRange(student.getId(), from, to);
        for (ClinicReservation reservation : reservations) {
            mine.put(reservation.getClinic().getId(), reservation);
        }

        List<StudentClinicResponse> result = new ArrayList<>();
        for (Clinic clinic : clinics) {
            ClinicReservation reservation = mine.get(clinic.getId());
            if (!clinic.isOpen() && reservation == null) {
                continue;
            }
            long reservedCount = counts.getOrDefault(clinic.getId(), 0L);
            result.add(new StudentClinicResponse(clinic.getId(), clinic.getClinicDate(),
                clinic.getStartTime(), clinic.getEndTime(), clinic.slots(),
                clinic.getCapacity(), reservedCount, clinic.isFull(reservedCount),
                MonthWeeks.label(clinic.getClinicDate()),
                reservation == null ? null : new MyReservationResponse(
                    reservation.getId(), reservation.getStatus(),
                    reservation.getArrivalTime(),
                    // null이면 결석이 아니라 아직 확정 전이다. S-6이 이 값으로 출결을 그린다
                    reservation.getAttendStatus())));
        }
        return result;
    }

    /**
     * P-2 캘린더(한 달) · P-6 주간 레포트(한 주). 신청·변경 경로는 학부모에게 열지 않는다.
     *
     * <p>week가 null이면 그 달 전체다. <b>주차 → 날짜 변환은 여기서만 한다</b> —
     * 프론트에서 계산해 from·to로 보내게 두면 "달 안에서 1일부터 7일씩" 규칙이
     * 서버와 화면 두 곳으로 갈라진다. 수업은 서버가 묶고 클리닉은 화면이 묶으면
     * 같은 주 레포트에 다른 날이 섞인다.
     */
    @Transactional(readOnly = true)
    public List<ParentClinicResponse> listForChild(Long studentId, int year, int month,
                                                   Integer week) {
        Student student = studentAccessGuard.requireAccessible(studentId);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate from = week != null ? MonthWeeks.startOf(year, month, week) : monthStart;
        LocalDate to = week != null
            ? MonthWeeks.endOf(year, month, week)
            : monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        return reservationRepository.findReservedByStudentInRange(student.getId(), from, to)
            .stream()
            .map(reservation -> new ParentClinicResponse(
                reservation.getClinic().getId(),
                reservation.getClinic().getClinicDate(),
                reservation.getClinic().getStartTime(),
                reservation.getClinic().getEndTime(),
                // 도착 시각이 없으면 5시간짜리 시간대만 보여서 몇 시에 가는지 알 수 없다
                reservation.getArrivalTime(),
                reservation.getAttendStatus()))
            .toList();
    }

    // ---------- 내부 ----------

    Clinic findClinic(Long clinicId) {
        return clinicRepository.findById(clinicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Map<Long, Long> reservedCounts(List<Clinic> clinics) {
        if (clinics.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (ClinicCount row : reservationRepository.countReservedByClinicIds(ids(clinics))) {
            counts.put(row.getClinicId(), row.getReservedCount());
        }
        return counts;
    }

    private Set<Long> isAttendanceConfirmed(Collection<Long> clinicIds) {
        if (clinicIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(reservationRepository.findAttendanceConfirmedClinicIds(clinicIds));
    }

    /** 배지의 분모·분자 계산용. 클리닉별 슬롯 행을 묶어 둔다. */
    private Map<Long, List<SlotCount>> slotCountsByClinic(Collection<Long> clinicIds) {
        if (clinicIds.isEmpty()) {
            return Map.of();
        }
        return reservationRepository.countBySlot(clinicIds).stream()
            .collect(Collectors.groupingBy(SlotCount::getClinicId));
    }

    /** 배지의 분자. 슬롯 행 중 미확정 인원이 0인(전원 확정) 행 수다. */
    private int confirmedSlotCount(List<SlotCount> slots) {
        return (int) slots.stream().filter(slot -> slot.getPendingCount() == 0).count();
    }

    private List<Long> ids(List<Clinic> clinics) {
        return clinics.stream().map(Clinic::getId).toList();
    }

    /** 1회 정원은 미확정이라 기본값을 지어내지 않는다. 다만 0 이하는 의미가 없다. */
    private Short validCapacity(Short capacity) {
        if (capacity != null && capacity <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return capacity;
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
