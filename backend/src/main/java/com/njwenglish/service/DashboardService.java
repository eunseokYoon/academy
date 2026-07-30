package com.njwenglish.service;

import com.njwenglish.dto.home.TeacherDashboardResponse;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.ClinicChangeRequestRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.SubmissionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-1 대시보드. count 쿼리 열 개 남짓이 나가지만 200명 규모에서는 문제없다.
 * <b>캐시나 집계 테이블을 도입하지 마라</b> — 어긋난 숫자는 없는 숫자보다 나쁘다.
 *
 * <p>월별 출석률 추이 같은 통계 그래프를 임의로 추가하지 마라. 요구사항에 없다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** "오늘"의 기준. 대시보드는 날짜가 하루 어긋나면 오늘 수업이 안 보인다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 신규 가입 점검 기간. 등록 기간에는 매일 이 목록의 상단을 훑는다. */
    private static final int RECENT_SIGNUP_DAYS = 7;

    private final LessonRepository lessonRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final ClinicChangeRequestRepository clinicChangeRequestRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public TeacherDashboardResponse dashboard() {
        LocalDate today = LocalDate.now(KST);

        return new TeacherDashboardResponse(
            new TeacherDashboardResponse.Today(today, todayLessons(today)),
            todo(today),
            new TeacherDashboardResponse.Stats(
                studentRepository.countByStatus(StudentStatus.ENROLLED),
                classRoomRepository.countByStatus(ClassRoomStatus.ACTIVE)));
    }

    // ---------- 내부 ----------

    /**
     * 오늘 수업. 인원은 <b>수업일 기준 재원 인원</b>이다 —
     * 오늘 수업이라 오늘 기준과 같지만, 세는 지점을 통일해 두면 나중에 어긋나지 않는다.
     */
    private List<TeacherDashboardResponse.Lesson> todayLessons(LocalDate today) {
        return lessonRepository.findByDateWithClassRoom(today).stream()
            .map(lesson -> new TeacherDashboardResponse.Lesson(
                lesson.getId(),
                lesson.getClassRoom().getName(),
                lesson.getClassRoom().getStartTime(),
                enrollmentRepository.countActiveStudentsOn(
                    lesson.getClassRoom().getId(), lesson.getLessonDate()),
                lesson.getAttendanceStatus(),
                lesson.getContent() != null))
            .toList();
    }

    /**
     * 할 일 여섯 개 + 점검 두 개.
     *
     * <p>unsignedStudentCount와 unlinkedParentCount가 특히 중요하다. 가입하지 않으면
     * 선생님이 입력한 출석·숙제·성적이 아무에게도 전달되지 않는다.
     */
    private TeacherDashboardResponse.Todo todo(LocalDate today) {
        OffsetDateTime signupFrom = OffsetDateTime.now().minusDays(RECENT_SIGNUP_DAYS);

        return new TeacherDashboardResponse.Todo(
            lessonRepository.countPendingUntil(today),
            submissionRepository.countByStatus(SubmissionStatus.SUBMITTED),
            lessonRepository.countUnwrittenUntil(today),
            studentRepository.countByStatusAndUserIsNull(StudentStatus.ENROLLED),
            studentRepository.countByStatusAndParentIsNull(StudentStatus.ENROLLED),
            clinicChangeRequestRepository.countByStatus(ChangeRequestStatus.PENDING),
            studentRepository.countByCreatedAtGreaterThanEqual(signupFrom),
            classRoomRepository.countByStatusAndJoinCodeActiveTrue(ClassRoomStatus.ACTIVE));
    }
}
