package com.njwenglish.service;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.home.HomeHomeworkResponse;
import com.njwenglish.dto.home.HomeLessonResponse;
import com.njwenglish.dto.home.HomeNoticesResponse;
import com.njwenglish.dto.home.NextClinicResponse;
import com.njwenglish.dto.home.NextLessonResponse;
import com.njwenglish.dto.home.ParentHomeResponse;
import com.njwenglish.dto.home.StudentHomeResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-1 학생 홈과 P-1 학부모 홈. 앞 단계 데이터를 조합하는 화면이라
 * <b>화면 하나당 API 하나</b>로 묶는다. 프론트에서 6개를 병렬 호출하면
 * 카드가 하나씩 튀어나오고 실패한 하나 때문에 화면 전체가 흔들린다.
 *
 * <p>비어 있는 값은 <b>null로 내려보낸다.</b> 프론트가 카드를 숨긴다.
 * 0이나 임의 값을 채우면 "시험이 오늘"이나 "출석 0회"로 읽힌다.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** D-day는 하루 차이가 그대로 보이는 값이라 기준 시간대를 명시한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LessonRepository lessonRepository;
    private final SubmissionRepository submissionRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClinicReservationRepository clinicReservationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamScheduleService examScheduleService;
    private final NoticeService noticeService;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * S-1. 본인 것으로 고정된다.
     *
     * <p>currentHomeworks에 <b>마감 지난 미제출도 들어간다.</b> 홈에서 사라지면 학생이 잊고,
     * 미제출 독려 발송 수단이 범위 밖이라 아무도 다시 알려주지 않는다.
     */
    @Transactional(readOnly = true)
    public StudentHomeResponse studentHome() {
        Student me = studentAccessGuard.requireSelf();
        LocalDate today = LocalDate.now(KST);
        OffsetDateTime now = OffsetDateTime.now();

        List<HomeHomeworkResponse> homeworks =
            submissionRepository.findOpenByStudent(me.getId()).stream()
                .map(submission -> HomeHomeworkResponse.from(submission, now))
                .toList();

        return new StudentHomeResponse(
            new StudentHomeResponse.StudentRef(me.getName()),
            lessonRepository.findNextForStudent(me.getId(), today)
                .map(lesson -> NextLessonResponse.from(lesson, today))
                .orElse(null),
            examScheduleService.findNextExam(me.getId()).orElse(null),
            homeworks,
            lessonRepository.findLastForStudent(me.getId(), today)
                .map(HomeLessonResponse::from)
                .orElse(null),
            new HomeNoticesResponse(
                noticeService.countFor(me.getId()),
                noticeService.recentFor(me.getId())));
    }

    /**
     * P-1. <b>첫 줄이 requireAccessible이다</b> — 학부모 A가 학부모 B의 자녀
     * studentId를 넣으면 여기서 403이다.
     *
     * <p>수업 제목·내용·영상, 숙제 내용·사진·피드백, 자료실은 넣지 않는다.
     * 학부모는 "자녀가 했는지 여부"만 본다 (확정 사항).
     */
    @Transactional(readOnly = true)
    public ParentHomeResponse parentHome(Long studentId) {
        Student child = studentAccessGuard.requireAccessible(studentId);
        LocalDate today = LocalDate.now(KST);
        YearMonth thisMonth = YearMonth.from(today);

        AttendanceSummaryResponse attendance = AttendanceSummaryResponse.of(
            attendanceRepository.findConfirmedStatuses(
                child.getId(), thisMonth.atDay(1), thisMonth.atEndOfMonth()));

        // 날짜와 시각만 꺼낸다. 제목·내용은 학부모에게 노출하지 않는다
        Optional<Lesson> nextLesson = lessonRepository.findNextForStudent(child.getId(), today);

        return new ParentHomeResponse(
            childRef(child),
            examScheduleService.findNextExam(child.getId()).orElse(null),
            nextLesson.map(Lesson::getLessonDate).orElse(null),
            // 시각은 lessons가 아니라 그 날짜 요일의 반 슬롯에 있다. 슬롯이 없으면 null이다
            nextLesson
                .flatMap(lesson -> lesson.getClassRoom().scheduleOn(lesson.getLessonDate()))
                .map(ClassRoomSchedule::getStartTime)
                .orElse(null),
            new HomeNoticesResponse(
                noticeService.countFor(child.getId()),
                noticeService.recentFor(child.getId())),
            submissionRepository.countPendingHomeworks(child.getId()),
            clinicReservationRepository.findNextReserved(child.getId(), today)
                .map(reservation -> NextClinicResponse.from(reservation, today))
                .orElse(null),
            attendance);
    }

    // ---------- 내부 ----------

    /**
     * 이름은 students.name이다 — 미가입 자녀는 users 행이 없어 users.name을 타면 사라진다.
     * 전화번호는 보호자가 자기 자녀 것을 보는 것이라 마스킹하지 않는다.
     */
    private ParentHomeResponse.ChildRef childRef(Student child) {
        List<String> classRooms =
            enrollmentRepository.findByStudentIdAndLeftAtIsNull(child.getId()).stream()
                .map(Enrollment::getClassRoom)
                .sorted(Comparator.comparing(ClassRoom::getName))
                .map(ClassRoom::getName)
                .toList();

        return new ParentHomeResponse.ChildRef(
            child.getId(),
            child.getName(),
            // 보호자가 자기 자녀 번호를 보는 것이라 마스킹하지 않는다.
            // 미가입 자녀는 users 행이 없어 null이다
            child.getUser() == null ? null : child.getUser().getPhone(),
            classRooms);
    }
}
