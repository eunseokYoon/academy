package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.clinic.ClinicAssignRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmResponse;
import com.njwenglish.dto.clinic.ClinicReservationCreateResponse;
import com.njwenglish.dto.clinic.ClinicReservationListResponse;
import com.njwenglish.dto.clinic.ClinicReservationStudentResponse;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클리닉 신청·배정·취소·출석. 신청 경로가 두 가지다 —
 * 학생 본인 신청(S-9)은 assigned_by = NULL, 선생님 배정(T-13)은 배정자 id가 들어간다.
 *
 * <p>정원 체크는 언제나 {@link #reserveLocked}를 거친다. 학생 신청, 선생님 일괄 배정,
 * 변경 요청 승인 세 곳이 같은 경합에 노출되므로 같은 메서드를 써야 한다.
 */
@Service
@RequiredArgsConstructor
public class ClinicReservationService {

    private final ClinicRepository clinicRepository;
    private final ClinicReservationRepository reservationRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * 정원 체크 + 삽입. <b>클리닉 행을 FOR UPDATE로 잠근 뒤에 센다.</b>
     *
     * <p>"세어 보고 넣기"는 동시 신청 시 정원을 넘긴다. 조건부 삽입 한 방도 같은 버그다 —
     * READ COMMITTED에서 서브쿼리의 count(*)는 세는 행에 락을 걸지 않아,
     * 두 트랜잭션이 같은 count를 읽고 둘 다 통과한다. 저부하에서는 통과하다가
     * 신청이 몰리는 순간 깨져서 테스트로 잡기도 어렵다.
     *
     * <p>failOnDuplicate는 경로마다 다르다. 학생 신청은 "이미 신청하셨습니다"를 보여줘야 해서
     * 409지만, 선생님 일괄 배정은 멱등이라 이미 있는 학생을 건너뛴다.
     */
    @Transactional
    public List<ClinicReservation> reserveLocked(Long clinicId, List<Student> students,
                                                 Teacher assignedBy, boolean failOnDuplicate) {
        Clinic clinic = clinicRepository.findByIdForUpdate(clinicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Student> toAdd = new ArrayList<>(students.size());
        for (Student student : students) {
            boolean already = reservationRepository.existsByClinicIdAndStudentIdAndStatus(
                clinicId, student.getId(), ReservationStatus.RESERVED);
            if (already) {
                // 정원 초과와 중복 신청은 다른 코드다. S-9 화면 문구가 갈린다
                if (failOnDuplicate) {
                    throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
                }
                continue;
            }
            toAdd.add(student);
        }
        if (toAdd.isEmpty()) {
            return List.of();
        }

        long reserved = reservationRepository.countByClinicIdAndStatus(
            clinicId, ReservationStatus.RESERVED);
        // 넘는 만큼만 넣지 말고 전체를 거절한다. 일부만 들어가면 누가 빠졌는지 모른다
        if (clinic.getCapacity() != null && reserved + toAdd.size() > clinic.getCapacity()) {
            throw new BusinessException(ErrorCode.CLINIC_CAPACITY_EXCEEDED);
        }

        return toAdd.stream()
            .map(student -> reservationRepository.save(
                ClinicReservation.reserve(clinic, student, assignedBy)))
            .toList();
    }

    /** S-9 학생 본인 신청. assigned_by는 null이다. */
    @Transactional
    public ClinicReservationCreateResponse reserve(Long clinicId) {
        Student student = studentAccessGuard.requireSelf();
        Clinic clinic = findClinic(clinicId);

        // 닫힌 시간대와 지난 날짜는 애초에 신청 대상이 아니다
        if (!clinic.isOpen()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if (clinic.getClinicDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<ClinicReservation> created =
            reserveLocked(clinicId, List.of(student), null, true);
        return ClinicReservationCreateResponse.from(created.get(0));
    }

    /**
     * S-9 신청 취소. 행을 지우지 않고 CANCELED로 바꾼다 —
     * 부분 유니크 인덱스가 RESERVED만 보므로 나중에 다시 신청할 수 있다.
     */
    @Transactional
    public void cancel(Long clinicId) {
        Student student = studentAccessGuard.requireSelf();
        ClinicReservation reservation = reservationRepository
            .findByClinicIdAndStudentIdAndStatus(clinicId, student.getId(),
                ReservationStatus.RESERVED)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        reservation.cancel();
    }

    /** T-13 명단. 선생님 화면 전용이라 이름이 나간다. */
    @Transactional(readOnly = true)
    public ClinicReservationListResponse reservations(Long clinicId) {
        Clinic clinic = findClinic(clinicId);
        List<ClinicReservation> reservations =
            reservationRepository.findReservedWithStudent(clinicId);

        return new ClinicReservationListResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime(), clinic.getCapacity(),
            reservations.stream().anyMatch(r -> r.getAttendStatus() != null),
            reservations.stream().map(ClinicReservationStudentResponse::from).toList());
    }

    /** T-13 선생님 배정(복수). 이미 신청한 학생은 건너뛰고 나머지만 넣는다. */
    @Transactional
    public ClinicReservationListResponse assign(Long clinicId, ClinicAssignRequest request) {
        List<Student> students = request.studentIds().stream().distinct()
            .map(studentId -> studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
            .toList();

        reserveLocked(clinicId, students, currentTeacher(), false);
        return reservations(clinicId);
    }

    /** T-13 배정 해제. 취소와 마찬가지로 행을 지우지 않는다. */
    @Transactional
    public void unassign(Long clinicId, Long studentId) {
        ClinicReservation reservation = reservationRepository
            .findByClinicIdAndStudentIdAndStatus(clinicId, studentId,
                ReservationStatus.RESERVED)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        reservation.cancel();
    }

    /**
     * 클리닉 출석 확정. T-5와 같은 방식이라 <b>안 온 학생만</b> 보낸다.
     *
     * <p>기록은 clinic_reservations.attend_status다.
     * attendances 테이블은 class_room_id가 NOT NULL인데 클리닉은 반이 없어서 못 쓴다.
     */
    @Transactional
    public ClinicAttendanceConfirmResponse confirmAttendance(Long clinicId,
                                                             AttendanceConfirmRequest request) {
        Clinic clinic = findClinic(clinicId);
        List<ClinicReservation> reservations =
            reservationRepository.findReservedWithStudent(clinicId);

        Map<Long, AttendanceExceptionRequest> exceptions = new HashMap<>();
        for (AttendanceExceptionRequest exception : request.exceptionsOrEmpty()) {
            exceptions.put(exception.studentId(), exception);
        }
        List<Long> rosterIds = reservations.stream()
            .map(reservation -> reservation.getStudent().getId()).toList();
        for (Long studentId : exceptions.keySet()) {
            if (!rosterIds.contains(studentId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }

        Teacher teacher = currentTeacher();
        OffsetDateTime now = OffsetDateTime.now();
        List<AttendanceStatus> statuses = new ArrayList<>(reservations.size());
        for (ClinicReservation reservation : reservations) {
            AttendanceExceptionRequest exception =
                exceptions.get(reservation.getStudent().getId());
            AttendanceStatus status = exception == null
                ? AttendanceStatus.PRESENT : exception.status();
            reservation.checkAttendance(status,
                exception == null ? null : exception.memo(), teacher, now);
            statuses.add(status);
        }

        return new ClinicAttendanceConfirmResponse(clinic.getId(), now,
            AttendanceSummaryResponse.of(statuses));
    }

    // ---------- 내부 ----------

    private Clinic findClinic(Long clinicId) {
        return clinicRepository.findById(clinicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
