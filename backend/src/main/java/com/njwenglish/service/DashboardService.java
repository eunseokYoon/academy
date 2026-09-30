package com.njwenglish.service;

import com.njwenglish.dto.home.TeacherDashboardResponse;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.QnaPostRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.common.security.CurrentUser;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
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
    private final StudentRepository studentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final QnaPostRepository qnaPostRepository;

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
                // 시각은 lessons에 없다. 그 날짜의 요일에 해당하는 반 슬롯에서 온다 —
                // 반이 주 2회면 요일마다 시각이 달라 반 단위로 하나를 고를 수 없다.
                // 슬롯이 없으면 null이다(요일 미정이거나, 요일을 바꾼 뒤 남은 예전 날짜)
                lesson.getClassRoom().scheduleOn(lesson.getLessonDate())
                    .map(ClassRoomSchedule::getStartTime)
                    .orElse(null),
                enrollmentRepository.countActiveStudentsOn(
                    lesson.getClassRoom().getId(), lesson.getLessonDate()),
                lesson.getAttendanceStatus(),
                lesson.getContent() != null))
            // 시작 시각순. 쿼리에서 못 하는 이유는 LessonRepository 주석에 있다.
            // 시각이 없는 반(요일 미정)은 뒤로 보낸다 — 오늘 첫 수업이 뭔지가 이 목록의 용도다.
            .sorted(Comparator.comparing(TeacherDashboardResponse.Lesson::startTime,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * 할 일 네 개 + 점검 두 개.
     *
     * <p>unsignedStudentCount와 unlinkedParentCount가 특히 중요하다. 가입하지 않으면
     * 선생님이 입력한 출석·숙제·성적이 아무에게도 전달되지 않는다.
     */
    private TeacherDashboardResponse.Todo todo(LocalDate today) {
        OffsetDateTime signupFrom = OffsetDateTime.now().minusDays(RECENT_SIGNUP_DAYS);

        return new TeacherDashboardResponse.Todo(
            lessonRepository.countPendingUntil(today),
            lessonRepository.countUnwrittenUntil(today),
            studentRepository.countByStatusAndUserIsNull(StudentStatus.ENROLLED),
            studentRepository.countByStatusAndParentIsNull(StudentStatus.ENROLLED),
            studentRepository.countByCreatedAtGreaterThanEqual(signupFrom),
            classRoomRepository.countByStatusAndJoinCodeActiveTrue(ClassRoomStatus.ACTIVE),
            newQuestionCount());
    }

    /** 선생님이 게시판을 마지막으로 연 뒤의 질문. 선생님 행이 없으면(관리 계정 등) 0이다. */
    private long newQuestionCount() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .map(teacher -> qnaPostRepository.countRootsCreatedAfter(teacher.getQnaSeenAt()))
            .orElse(0L);
    }
}
