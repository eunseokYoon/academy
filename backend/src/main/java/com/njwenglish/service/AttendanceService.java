package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceConfirmResponse;
import com.njwenglish.dto.attendance.AttendanceCorrectRequest;
import com.njwenglish.dto.attendance.AttendanceDayResponse;
import com.njwenglish.dto.attendance.AttendanceDetailResponse;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import com.njwenglish.dto.attendance.AttendanceRosterResponse;
import com.njwenglish.dto.attendance.AttendanceStudentResponse;
import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.attendance.PendingLessonResponse;
import com.njwenglish.entity.Attendance;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.AttendanceRepository.CalendarRow;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.SubmissionRepository.HomeworkRateRow;
import com.njwenglish.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-5 출석 확정과 S-6 · P-2 캘린더.
 *
 * <p>핵심은 두 가지다. 기본값이 출석이라 선생님은 안 온 학생만 보내고,
 * PENDING인 날은 출석이 아니라 "미확인"이라 집계에 넣지 않는다.
 * 미래 수업일도 PENDING이라 이 구분이 없으면 아직 오지 않은 날이 출석으로 잡힌다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * 출석 입력 화면. 확정 전이면 전원 PRESENT로 초기화하고, 확정 후면 저장된 값을 보여준다.
     *
     * <p>명단은 <b>수업일 기준</b> 재원생이다. 오늘 기준이 아니다 —
     * 5월 수업의 출석을 6월에 입력할 때 5월에 다니던 학생이 나와야 한다.
     */
    @Transactional(readOnly = true)
    public AttendanceRosterResponse roster(Long lessonId) {
        Lesson lesson = findLesson(lessonId);
        List<Student> students = enrollmentRepository.findActiveStudents(
            lesson.getClassRoom().getId(), lesson.getLessonDate());

        Map<Long, Attendance> saved = lesson.isAttendanceConfirmed()
            ? indexByStudent(attendanceRepository.findByLessonId(lessonId))
            : Map.of();

        List<AttendanceStudentResponse> rows = students.stream()
            .map(student -> {
                Attendance attendance = saved.get(student.getId());
                return attendance == null
                    ? AttendanceStudentResponse.present(student.getId(), student.getName())
                    : new AttendanceStudentResponse(student.getId(), student.getName(),
                        attendance.getStatus(), attendance.getMemo());
            })
            .toList();

        return new AttendanceRosterResponse(lesson.getId(), lesson.getClassRoom().getId(),
            lesson.getClassRoom().getName(), lesson.getLessonDate(),
            lesson.getAttendanceStatus(), rows);
    }

    /**
     * 확정. 예외로 지정된 학생만 해당 상태로, 나머지는 PRESENT로 <b>전원</b> 행을 만든다.
     *
     * <p>예외만 저장하고 "행이 없으면 출석"으로 두면 캘린더를 그릴 때마다
     * 재원생 명단 × 수업일을 역산해야 한다. 200명 × 연 40회면 연 8천 행이라 부담이 없다.
     *
     * <p>재확정은 정상적인 정정 흐름이다. 409를 던지지 마라 —
     * upsert가 ON CONFLICT로 흡수하고 checked_by는 최초 확정자로 남는다.
     */
    @Transactional
    public AttendanceConfirmResponse confirm(Long lessonId, AttendanceConfirmRequest request) {
        Lesson lesson = findLesson(lessonId);
        List<Student> students = enrollmentRepository.findActiveStudents(
            lesson.getClassRoom().getId(), lesson.getLessonDate());

        Map<Long, AttendanceExceptionRequest> exceptions = new HashMap<>();
        for (AttendanceExceptionRequest exception : request.exceptionsOrEmpty()) {
            exceptions.put(exception.studentId(), exception);
        }
        requireAllInRoster(exceptions.keySet(), students);

        Teacher teacher = currentTeacher();
        List<AttendanceStatus> statuses = new ArrayList<>(students.size());
        for (Student student : students) {
            AttendanceExceptionRequest exception = exceptions.get(student.getId());
            AttendanceStatus status = exception == null
                ? AttendanceStatus.PRESENT : exception.status();
            String memo = exception == null ? null : exception.memo();

            // attend_date에 lesson_date를 복사한다. 캘린더 조회가 이 컬럼에 의존한다
            attendanceRepository.upsert(lesson.getId(), lesson.getClassRoom().getId(),
                student.getId(), lesson.getLessonDate(), status.name(), memo, teacher.getId());
            statuses.add(status);
        }

        OffsetDateTime now = OffsetDateTime.now();
        lesson.confirmAttendance(teacher, now);

        return new AttendanceConfirmResponse(lesson.getId(), LessonAttendanceStatus.CONFIRMED,
            now, AttendanceSummaryResponse.of(statuses));
    }

    /**
     * 확정 후 개별 정정. updated_by · updated_at을 반드시 남긴다 —
     * "어제 병원 간다고 미리 말씀드렸는데 결석이에요" 같은 연락에 답하려면
     * 누가 언제 무엇을 바꿨는지가 있어야 한다.
     */
    @Transactional
    public AttendanceDetailResponse correct(Long attendanceId, AttendanceCorrectRequest request) {
        Attendance attendance = attendanceRepository.findWithStudent(attendanceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        attendance.correct(request.status(), request.memo(), currentTeacher());
        return AttendanceDetailResponse.from(attendance);
    }

    /** 미확정 수업 목록. 미래 수업은 아직 확정할 수 없으므로 제외한다. */
    @Transactional(readOnly = true)
    public List<PendingLessonResponse> pending() {
        return lessonRepository.findPendingUntil(LocalDate.now()).stream()
            .map(lesson -> new PendingLessonResponse(
                lesson.getId(),
                lesson.getClassRoom().getId(),
                lesson.getClassRoom().getName(),
                lesson.getLessonDate(),
                enrollmentRepository.countActiveStudentsOn(
                    lesson.getClassRoom().getId(), lesson.getLessonDate())))
            .toList();
    }

    /** S-6. 본인 것으로 고정된다. */
    @Transactional(readOnly = true)
    public AttendanceCalendarResponse myCalendar(int year, int month) {
        return calendar(studentAccessGuard.requireSelf().getId(), year, month);
    }

    /**
     * S-6 · P-2 월별 캘린더. <b>첫 줄이 권한 검증이다.</b>
     * 학부모가 URL의 숫자만 바꿔 남의 아이 출석을 보는 걸 여기서 막는다.
     */
    @Transactional(readOnly = true)
    public AttendanceCalendarResponse calendar(Long studentId, int year, int month) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        YearMonth yearMonth = validMonth(year, month);

        List<CalendarRow> rows = attendanceRepository.findCalendarRows(
            student.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());

        // 한 날짜에 수업이 둘일 수 있다(반 두 곳이 같은 요일). 캘린더 칸은 하나뿐이라
        // 확정된 쪽을 우선하고, 둘 다 확정이면 먼저 만들어진 수업을 쓴다.
        Map<LocalDate, CalendarRow> byDate = new LinkedHashMap<>();
        for (CalendarRow row : rows) {
            byDate.merge(row.getLessonDate(), row,
                (kept, candidate) -> kept.getAttendStatus() != null ? kept : candidate);
        }

        Map<Long, HomeworkRateRow> homeworkRates = homeworkRatesOf(student.getId(), byDate.values());

        List<AttendanceDayResponse> days = new ArrayList<>(byDate.size());
        List<AttendanceStatus> confirmed = new ArrayList<>(byDate.size());
        long doneTotal = 0;
        long assignedTotal = 0;
        for (CalendarRow row : byDate.values()) {
            boolean isConfirmed = row.getLessonStatus() == LessonAttendanceStatus.CONFIRMED
                && row.getAttendStatus() != null;
            HomeworkRateRow rate = homeworkRates.get(row.getLessonId());

            // rate가 null이면 그날 숙제가 없었다는 뜻이다. 0으로 바꾸지 마라 —
            // 0은 "전부 미제출"이라 캘린더에 빨간 띠가 뜨고 학부모는 그걸 그렇게 읽는다
            days.add(new AttendanceDayResponse(row.getLessonDate(),
                isConfirmed ? row.getAttendStatus().name() : AttendanceDayResponse.PENDING,
                rate == null || rate.getTargetCount() == 0
                    ? null : average(rate.getScoreSum(), rate.getTargetCount())));

            if (isConfirmed) {
                confirmed.add(row.getAttendStatus());
            }
            if (rate != null) {
                doneTotal += rate.getScoreSum();
                assignedTotal += rate.getTargetCount();
            }
        }

        // 그 달에 숙제가 하나도 없으면 월 전체도 null이다.
        // doneTotal은 이미 0~100 스케일로 누적된 값이라 여기서 다시 *100을 하면 안 된다
        Integer monthlyRate = assignedTotal == 0 ? null : average(doneTotal, assignedTotal);

        return new AttendanceCalendarResponse(yearMonth.getYear(), yearMonth.getMonthValue(),
            AttendanceSummaryResponse.of(confirmed), monthlyRate, days);
    }

    // ---------- 내부 ----------

    private Lesson findLesson(Long lessonId) {
        return lessonRepository.findWithClassRoom(lessonId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 캘린더 색띠의 원본. lesson에 연결된 숙제만 대상이라, 행이 없는 수업일은
     * "그날 숙제가 없었다"이지 0%가 아니다.
     */
    private Map<Long, HomeworkRateRow> homeworkRatesOf(Long studentId,
                                                       Collection<CalendarRow> rows) {
        List<Long> lessonIds = rows.stream().map(CalendarRow::getLessonId).toList();
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, HomeworkRateRow> rates = new HashMap<>();
        for (HomeworkRateRow rate : submissionRepository.findHomeworkRates(studentId, lessonIds)) {
            rates.put(rate.getLessonId(), rate);
        }
        return rates;
    }

    // 반올림이다. 버림으로 바꾸지 마라 — 2개 중 1개가 100·99면 99.5가 99로 떨어진다.
    // (double) 캐스트가 없으면 나눗셈이 정수 연산으로 먼저 끝나 Math.round가 이미 잘린
    // 값을 받는다 — 같은 버그가 소리 없이 되돌아온다
    private int average(long scoreSum, long targetCount) {
        return (int) Math.round((double) scoreSum / targetCount);
    }

    private Map<Long, Attendance> indexByStudent(List<Attendance> attendances) {
        Map<Long, Attendance> map = new HashMap<>();
        for (Attendance attendance : attendances) {
            map.put(attendance.getStudent().getId(), attendance);
        }
        return map;
    }

    /**
     * 명단에 없는 학생이 예외로 섞이면 400이다. 반을 잘못 고른 채로 확정하면
     * 엉뚱한 학생에게 결석이 붙고, 그건 조용히 넘어가지 않는다.
     */
    private void requireAllInRoster(Iterable<Long> studentIds, List<Student> roster) {
        List<Long> rosterIds = roster.stream().map(Student::getId).toList();
        for (Long studentId : studentIds) {
            if (!rosterIds.contains(studentId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    private YearMonth validMonth(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return YearMonth.of(year, month);
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
