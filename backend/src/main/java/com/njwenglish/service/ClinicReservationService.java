package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.clinic.ClinicAssignRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmResponse;
import com.njwenglish.dto.clinic.ClinicReservationChangeRequest;
import com.njwenglish.dto.clinic.ClinicReservationCreateResponse;
import com.njwenglish.dto.clinic.ClinicReservationListResponse;
import com.njwenglish.dto.clinic.ClinicReservationStudentResponse;
import com.njwenglish.dto.clinic.ClinicSlotState;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicChangeLog;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicChangeLogRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클리닉 배정·변경·출석. <b>학생은 클리닉을 신청하지 못한다</b>(2026-09-01 확정) —
 * 배정은 선생님이 T-13에서 한다({@link #assign}). assigned_by에 배정한 선생님의 id가 들어간다.
 * 학생이 스스로 다른 클리닉으로 옮길 때({@link #change})도 같은 경로를 타지만,
 * 그때는 선생님이 배정한 게 아니므로 assigned_by가 NULL로 남는다.
 *
 * <p>정원 체크는 언제나 {@link #reserveLocked}를 거친다. 선생님 일괄 배정과
 * 클리닉 이동 두 곳이 같은 경합에 노출되므로 같은 메서드를 써야 한다.
 *
 * <p><b>변경에 선생님 승인이 없다</b>(2026-08-10 확정). 학생이 하면 즉시 반영된다.
 * 대신 사유를 받아 {@link ClinicChangeLog}를 남기고 공지를 한 건 발행한다 —
 * 승인이 없어서 그 둘이 유일한 대응책이다. 예약을 바꾸는 경로에서 빼먹지 마라.
 *
 * <p><b>학생에게 취소 경로는 없다</b>(2026-08-10 확정). 못 가면 다른 시각으로 옮긴다.
 * 아예 빠져야 하면 선생님이 T-13에서 배정 해제({@link #unassign})한다 —
 * 학생이 스스로 명단에서 사라지면 선생님이 그날 인원을 신뢰할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ClinicReservationService {

    /** 공지 본문의 날짜. "8월 13일(목)" — 수업일 변경 공지와 같은 형식이다. */
    private static final DateTimeFormatter NOTICE_DATE =
        DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);

    private final ClinicRepository clinicRepository;
    private final ClinicReservationRepository reservationRepository;
    private final ClinicChangeLogRepository changeLogRepository;
    private final NoticeService noticeService;
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
     * <p>failOnDuplicate는 경로마다 다르다. 학생이 다른 클리닉에서 옮겨 올 때는
     * "이미 그 시간대에 있습니다"를 보여줘야 해서 409지만, 선생님 일괄 배정은 멱등이라
     * 이미 있는 학생을 건너뛴다.
     *
     * <p>arrivalTime이 null이면 클리닉 시작 시각이다(선생님 배정의 기본값).
     * <b>정원은 클리닉 전체 기준이다</b> — 슬롯별 정원은 만들지 않기로 확정했다.
     */
    @Transactional
    public List<ClinicReservation> reserveLocked(Long clinicId, List<Student> students,
                                                 Teacher assignedBy, LocalTime arrivalTime,
                                                 boolean failOnDuplicate) {
        Clinic clinic = clinicRepository.findByIdForUpdate(clinicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LocalTime slot = requireSlot(clinic, arrivalTime);

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
                ClinicReservation.reserve(clinic, student, assignedBy, slot)))
            .toList();
    }

    /**
     * S-9 도착 시각 변경 · 다른 클리닉으로 이동. <b>선생님 승인이 없다</b>(확정) —
     * 대신 사유를 받아 기록을 남긴다.
     *
     * <p>targetClinicId가 없거나 지금 클리닉과 같으면 arrival_time만 UPDATE한다.
     * 다르면 기존 예약을 MOVED로 비우고 목표 클리닉에 새 RESERVED를 만든다 —
     * <b>먼저 비워야</b> 같은 학생이 두 시간대에 RESERVED로 남지 않는다.
     * 정원 재확인은 선생님 배정과 같은 reserveLocked를 탄다 — 정원 잠금·중복 검사·
     * 슬롯 검증이 전부 거기 있다. 이동 전용 경로를 따로 만들지 마라.
     */
    @Transactional
    public ClinicReservationCreateResponse change(Long clinicId,
                                                  ClinicReservationChangeRequest request) {
        Student student = studentAccessGuard.requireSelf();
        ClinicReservation reservation = findMyReservation(clinicId, student);
        Clinic fromClinic = reservation.getClinic();
        LocalTime fromArrivalTime = reservation.getArrivalTime();

        boolean sameClinic = request.targetClinicId() == null
            || request.targetClinicId().equals(clinicId);
        Clinic targetClinic = sameClinic ? fromClinic : findClinic(request.targetClinicId());
        requireOpenForStudent(targetClinic);

        ClinicReservation result;
        if (sameClinic) {
            result = reservation;
            result.changeArrivalTime(requireSlot(targetClinic, request.arrivalTime()));
        } else {
            reservation.moveOut();
            result = reserveLocked(targetClinic.getId(), List.of(student), null,
                request.arrivalTime(), true).get(0);
        }

        String reason = request.reason().trim();
        changeLogRepository.save(ClinicChangeLog.moved(student, fromClinic, fromArrivalTime,
            result.getClinic(), result.getArrivalTime(), reason));
        publishNotice(student, fromClinic, "클리닉 시간 변경 안내", """
            %s 학생이 클리닉 시간을 변경했습니다.

            변경 전 · %s
            변경 후 · %s

            사유 · %s"""
            .formatted(student.getName(),
                slotText(fromClinic, fromArrivalTime),
                slotText(result.getClinic(), result.getArrivalTime()),
                reason));
        return ClinicReservationCreateResponse.from(result);
    }

    /**
     * T-13 명단. 선생님 화면 전용이라 이름이 나간다.
     *
     * <p>정렬은 <b>도착 시각 → 이름</b>이다. 화면이 시각별로 묶어 그리므로 서버가 순서를
     * 맞춰 준다. 이름은 students.name이다 — user.name을 쓰면 미가입 학생이 사라진다.
     */
    @Transactional(readOnly = true)
    public ClinicReservationListResponse reservations(Long clinicId) {
        Clinic clinic = findClinic(clinicId);
        List<ClinicReservation> reservations =
            reservationRepository.findReservedWithStudent(clinicId);

        List<ClinicReservationStudentResponse> students = reservations.stream()
            .map(ClinicReservationStudentResponse::from)
            .sorted(Comparator.comparing(ClinicReservationStudentResponse::arrivalTime)
                .thenComparing(ClinicReservationStudentResponse::name))
            .toList();

        return new ClinicReservationListResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime(), clinic.slots(), clinic.getCapacity(),
            isAttendanceConfirmed(reservations),
            students, slotStates(clinic, reservations));
    }

    /**
     * 「학생이 있는 슬롯 전부 확정」(2026-09-01) — 목록 쪽 {@code findAttendanceConfirmedClinicIds}와
     * 정확히 같은 뜻이어야 한다. RESERVED 예약이 1건도 없으면 확정할 학생이 없으므로 false다
     * (allMatch는 빈 스트림에 vacuously true를 주므로 반드시 먼저 비어있는지 본다).
     */
    private boolean isAttendanceConfirmed(List<ClinicReservation> reservations) {
        return !reservations.isEmpty()
            && reservations.stream().allMatch(r -> r.getAttendStatus() != null);
    }

    /**
     * 도착 시각별 상태. <b>Clinic.slots()와 실제 예약 시각의 합집합</b>이다(2026-09-01) —
     * 선생님이 시간대를 좁혀 범위 밖으로 남은 예약(outOfRange)도 줄에 없으면 확정할 방법이
     * 없어져 클리닉이 영영 미확정으로 남는다. 시각 오름차순으로 돌린다.
     */
    private List<ClinicSlotState> slotStates(Clinic clinic, List<ClinicReservation> reservations) {
        Map<LocalTime, List<ClinicReservation>> byArrival = reservations.stream()
            .collect(Collectors.groupingBy(ClinicReservation::getArrivalTime));

        TreeSet<LocalTime> times = new TreeSet<>(clinic.slots());
        times.addAll(byArrival.keySet());

        return times.stream()
            .map(time -> {
                List<ClinicReservation> atTime = byArrival.getOrDefault(time, List.of());
                boolean confirmed = !atTime.isEmpty()
                    && atTime.stream().allMatch(r -> r.getAttendStatus() != null);
                return new ClinicSlotState(time, atTime.size(), confirmed);
            })
            .toList();
    }

    /**
     * T-13 선생님 배정(복수). 이미 신청한 학생은 건너뛰고 나머지만 넣는다.
     * arrivalTime을 생략하면 클리닉 시작 시각이다.
     */
    @Transactional
    public ClinicReservationListResponse assign(Long clinicId, ClinicAssignRequest request) {
        List<Student> students = request.studentIds().stream().distinct()
            .map(studentId -> studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
            .toList();

        reserveLocked(clinicId, students, currentTeacher(), request.arrivalTime(), false);
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
     * T-13 출결 확정. <b>도착 시각 슬롯 하나를 확정한다</b>(2026-09-01 확정).
     *
     * <p>다시 부르면 덮어쓴다 — 그게 "수정"이다. 되돌리기용 엔드포인트를 따로 만들지 마라.
     * 예약이 0명인 슬롯은 확정할 학생이 없어 아무것도 하지 않고 200이다.
     */
    @Transactional
    public ClinicAttendanceConfirmResponse confirmAttendance(
        Long clinicId, ClinicAttendanceConfirmRequest request) {
        Clinic clinic = findClinic(clinicId);
        List<ClinicReservation> reservations =
            reservationRepository.findReservedWithStudentAt(clinicId, request.arrivalTime());
        /*
         * 슬롯이거나, 슬롯은 아니어도 그 시각에 학생이 있으면 받는다.
         *
         * hasSlot만으로 막으면 outOfRange 예약(선생님이 시간대를 좁혀 범위 밖으로 남은 것)을
         * 영영 확정할 수 없다. attend_status가 null로 굳으면 findPendingUntil이 그 클리닉을
         * 계속 미확정으로 뽑아 T-1에서 사라지지 않는다.
         *
         * 예약이 0명일 때 hasSlot을 요구하는 것으로 임의 시각 방어는 그대로 남는다 —
         * 화면이 슬롯을 그려 주는 건 안내일 뿐이고 아무 값이나 올라올 수 있다.
         */
        if (reservations.isEmpty() && !clinic.hasSlot(request.arrivalTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

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

    /**
     * 학생·학부모에게 갈 공지. <b>사유가 본문에 들어간다</b> — 그게 이 알림의 핵심이다.
     *
     * <p>승인 절차가 없어서 선생님이 변경을 사후에만 안다. T-13 목록만으로는 학부모가
     * 알 길이 없으므로 수업일 변경과 같은 경로로 공지를 발행한다 —
     * 그래야 학생·학부모의 <b>공지 탭</b>에 뜬다.
     *
     * <p>scope는 STUDENT다. CLASS로 보내면 같은 반 20명이 이 학생의 사유를 읽는다.
     * 작성자는 그 클리닉의 선생님이다 — notices.created_by가 NOT NULL이고,
     * 바꾼 사람(학생)을 넣을 자리가 없다. 본문 첫 줄이 누가 바꿨는지 말해 준다.
     */
    private void publishNotice(Student student, Clinic clinic, String title, String body) {
        noticeService.publishForStudent(title, body, student, clinic.getTeacher());
    }

    /** "8월 13일(목) 17:00 도착". 공지 본문에서 두 번 쓴다. */
    private String slotText(Clinic clinic, LocalTime arrivalTime) {
        return "%s %s 도착".formatted(
            clinic.getClinicDate().format(NOTICE_DATE), arrivalTime);
    }

    /** 남의 예약은 건드릴 수 없다. 학생 본인의 RESERVED 행만 찾는다. */
    private ClinicReservation findMyReservation(Long clinicId, Student student) {
        return reservationRepository
            .findByClinicIdAndStudentIdAndStatus(clinicId, student.getId(),
                ReservationStatus.RESERVED)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 닫힌 시간대와 지난 날짜는 애초에 신청·이동 대상이 아니다. */
    private void requireOpenForStudent(Clinic clinic) {
        if (!clinic.isOpen()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if (clinic.getClinicDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 도착 시각 검증. null이면 클리닉 시작 시각으로 채운다(선생님 배정의 기본값).
     *
     * <p><b>서버가 반드시 검사한다.</b> 화면이 슬롯 목록을 그려 주는 건 안내일 뿐이고,
     * arrivalTime은 클라이언트가 보내는 값이라 21:37 같은 임의 시각이 그대로 올라온다.
     */
    private LocalTime requireSlot(Clinic clinic, LocalTime arrivalTime) {
        if (arrivalTime == null) {
            return clinic.getStartTime();
        }
        if (!clinic.hasSlot(arrivalTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return arrivalTime;
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
