package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.PendingClinicResponse;
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
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ClinicStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicChangeRequestRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.ClinicReservationRepository.ClinicCount;
import com.njwenglish.repository.TeacherRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ClinicChangeRequestRepository changeRequestRepository;
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
        return ClinicListItemResponse.of(clinic, reservedCount,
            isAttendanceConfirmed(List.of(clinicId)).contains(clinicId));
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

    /** T-13 목록. 기간 조회라 페이징이 없다. */
    @Transactional(readOnly = true)
    public List<ClinicListItemResponse> listForTeacher(LocalDate from, LocalDate to,
                                                       ClinicStatus status) {
        List<Clinic> clinics = clinicRepository.findInRange(from, to, status);
        Map<Long, Long> counts = reservedCounts(clinics);
        Set<Long> confirmed = isAttendanceConfirmed(ids(clinics));

        return clinics.stream()
            .map(clinic -> ClinicListItemResponse.of(clinic,
                counts.getOrDefault(clinic.getId(), 0L),
                confirmed.contains(clinic.getId())))
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
        Set<Long> pendingChanges = pendingChangeReservationIds(reservations);

        List<StudentClinicResponse> result = new ArrayList<>();
        for (Clinic clinic : clinics) {
            ClinicReservation reservation = mine.get(clinic.getId());
            if (!clinic.isOpen() && reservation == null) {
                continue;
            }
            long reservedCount = counts.getOrDefault(clinic.getId(), 0L);
            result.add(new StudentClinicResponse(clinic.getId(), clinic.getClinicDate(),
                clinic.getStartTime(), clinic.getEndTime(), clinic.getCapacity(),
                reservedCount, clinic.isFull(reservedCount),
                reservation == null ? null : new MyReservationResponse(
                    reservation.getId(), reservation.getStatus(),
                    // null이면 결석이 아니라 아직 확정 전이다. S-6이 이 값으로 출결을 그린다
                    reservation.getAttendStatus(),
                    pendingChanges.contains(reservation.getId())
                        ? ChangeRequestStatus.PENDING : null)));
        }
        return result;
    }

    /** P-2. 자녀의 클리닉 일정과 출석만. 신청·변경 경로는 학부모에게 열지 않는다. */
    @Transactional(readOnly = true)
    public List<ParentClinicResponse> listForChild(Long studentId, LocalDate from, LocalDate to) {
        Student student = studentAccessGuard.requireAccessible(studentId);

        List<ClinicReservation> reservations =
            reservationRepository.findReservedByStudentInRange(student.getId(), from, to);
        Set<Long> pendingChanges = pendingChangeReservationIds(reservations);

        return reservations.stream()
            .map(reservation -> new ParentClinicResponse(
                reservation.getClinic().getId(),
                reservation.getClinic().getClinicDate(),
                reservation.getClinic().getStartTime(),
                reservation.getClinic().getEndTime(),
                reservation.getAttendStatus(),
                pendingChanges.contains(reservation.getId())
                    ? ChangeRequestStatus.PENDING : null))
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

    private Set<Long> pendingChangeReservationIds(List<ClinicReservation> reservations) {
        if (reservations.isEmpty()) {
            return Set.of();
        }
        List<Long> reservationIds = reservations.stream().map(ClinicReservation::getId).toList();
        return new HashSet<>(changeRequestRepository.findPendingReservationIds(reservationIds));
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
