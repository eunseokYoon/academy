package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.lessonchange.LessonChangeCreateRequest;
import com.njwenglish.dto.lessonchange.LessonChangeDecideRequest;
import com.njwenglish.dto.lessonchange.LessonChangeRequestResponse;
import com.njwenglish.dto.lessonchange.LessonSlotResponse;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonChangeRequest;
import com.njwenglish.entity.Notice;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonChangeRequestRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수업일 변경 (S-9 · T-13).
 *
 * <p><b>승인해도 배정도 수업도 바뀌지 않는다.</b> 승인의 결과물은 학생·학부모에게 가는
 * 개인 공지 한 건뿐이고, 선생님은 자기가 승인했으므로 이미 알고 있다. 실제 반 이동으로
 * 만들면 출석·숙제·성적이 전부 따라 움직여야 하고 되돌릴 방법이 없다. 확정된 범위다.
 *
 * <p>그래서 <b>원래 반 출석부에는 그 날이 그대로 남는다.</b> 선생님이 출석 확정할 때
 * 손으로 처리한다. 이걸 자동화하려고 attendances를 건드리지 마라.
 */
@Service
@RequiredArgsConstructor
public class LessonChangeRequestService {

    /** "못 가는 내 수업" 후보를 오늘부터 며칠 앞까지 보여줄지. 학기 전체를 늘어놓을 이유가 없다. */
    private static final int MY_LESSON_WINDOW_DAYS = 30;

    /** 활성 배정이 없는 학생용 더미. 빈 컬렉션을 IN에 넘기면 SQL 오류다. */
    private static final List<Long> NO_CLASS_ROOM = List.of(-1L);

    /** 공지 본문의 날짜. "8월 13일(목)" */
    private static final DateTimeFormatter NOTICE_DATE =
        DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);

    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final LessonChangeRequestRepository requestRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final NoticeService noticeService;

    // ---------- 학생 (S-9) ----------

    /** 못 가는 회차로 고를 수 있는 내 수업. 오늘부터 한 달. */
    @Transactional(readOnly = true)
    public List<LessonSlotResponse> myLessons() {
        Student student = studentAccessGuard.requireSelf();
        LocalDate today = LocalDate.now();
        return lessonRepository
            .findUpcomingForStudent(student.getId(), today, today.plusDays(MY_LESSON_WINDOW_DAYS))
            .stream()
            .map(LessonSlotResponse::from)
            .toList();
    }

    /**
     * 대신 갈 수업 후보. <b>fromLesson이 있는 그 주(월~일)의 다른 반 수업만이다.</b>
     *
     * <p>주 경계는 lessons의 year·month·week가 아니라 날짜로 계산한다. 그 컬럼들은
     * 월 기준 주차라 8월 31일과 9월 2일이 같은 주인데도 값이 갈린다.
     */
    @Transactional(readOnly = true)
    public List<LessonSlotResponse> candidates(Long fromLessonId) {
        Student student = studentAccessGuard.requireSelf();
        Lesson fromLesson = requireMyLesson(fromLessonId, student.getId());

        LocalDate monday = fromLesson.getLessonDate().with(DayOfWeek.MONDAY);
        return lessonRepository
            .findWeekCandidates(monday, monday.plusDays(6), myClassRoomIds(student.getId()))
            .stream()
            .map(LessonSlotResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonChangeRequestResponse> listMine() {
        Student student = studentAccessGuard.requireSelf();
        return requestRepository.findAllByStudent(student.getId()).stream()
            .map(LessonChangeRequestResponse::from)
            .toList();
    }

    @Transactional
    public LessonChangeRequestResponse create(LessonChangeCreateRequest request) {
        Student student = studentAccessGuard.requireSelf();

        Lesson fromLesson = requireMyLesson(request.fromLessonId(), student.getId());
        // 지난 수업은 옮길 수 없다. 이미 결석으로 기록됐거나 곧 그렇게 된다
        if (fromLesson.getLessonDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Lesson toLesson = lessonRepository.findWithClassRoom(request.toLessonId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireValidTarget(fromLesson, toLesson, student.getId());

        // 같은 수업에 요청이 둘이면 선생님이 둘 다 승인했을 때 서로 다른 반으로 가는
        // 공지가 두 장 나간다. DB에도 부분 유니크 인덱스가 있지만 여기서 먼저 걸러야
        // 409 메시지를 제대로 돌려줄 수 있다
        if (requestRepository.existsByStudentIdAndFromLessonIdAndStatus(
            student.getId(), fromLesson.getId(), ChangeRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        return LessonChangeRequestResponse.from(requestRepository.save(
            LessonChangeRequest.create(student, fromLesson, toLesson, request.reason().trim())));
    }

    // ---------- 선생님 (T-13) ----------

    /** 대기 목록. 페이징 없음 — 강사 1명이 처리하는 큐다. */
    @Transactional(readOnly = true)
    public List<LessonChangeRequestResponse> listForTeacher(ChangeRequestStatus status) {
        return requestRepository.findAllWithDetail(status).stream()
            .map(LessonChangeRequestResponse::from)
            .toList();
    }

    /**
     * 승인·거절. <b>승인의 유일한 부수효과가 개인 공지 발행이다.</b>
     * 수업도 배정도 출석도 건드리지 않는다.
     *
     * <p>거절이면 아무에게도 알리지 않는다. 학생만 자기 목록에서 상태를 본다 —
     * 학부모에게 "요청이 거절됐다"를 알릴 이유가 없다.
     */
    @Transactional
    public LessonChangeRequestResponse decide(Long requestId, LessonChangeDecideRequest request) {
        LessonChangeRequest changeRequest = requestRepository.findWithDetail(requestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!changeRequest.isPending()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Teacher teacher = currentTeacher();
        boolean approve = Boolean.TRUE.equals(request.approve());
        Notice notice = approve ? publishNotice(changeRequest, teacher) : null;

        changeRequest.decide(approve, teacher, notice, OffsetDateTime.now());
        return LessonChangeRequestResponse.from(changeRequest);
    }

    // ---------- 내부 ----------

    /**
     * 그 수업이 이 학생의 수업인지. 남의 반 수업을 "내 수업"으로 넣어 요청할 수 없다.
     *
     * <p>재원 기간까지 보는 조회를 그대로 쓴다 — 따로 짜면 퇴원 이후 수업이 통과한다.
     */
    private Lesson requireMyLesson(Long lessonId, Long studentId) {
        LocalDate today = LocalDate.now();
        return lessonRepository
            .findUpcomingForStudent(studentId, today, today.plusDays(MY_LESSON_WINDOW_DAYS))
            .stream()
            .filter(lesson -> lesson.getId().equals(lessonId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 후보 목록과 같은 규칙이다. 목록에 없는 수업이 요청으로 들어오면 400이다. */
    private void requireValidTarget(Lesson fromLesson, Lesson toLesson, Long studentId) {
        if (toLesson.getId().equals(fromLesson.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 내가 속한 반의 수업은 대체가 아니다. 어차피 그 수업도 내가 듣는 수업이다
        if (myClassRoomIds(studentId).contains(toLesson.getClassRoom().getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!toLesson.getClassRoom().isActive()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LocalDate monday = fromLesson.getLessonDate().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        if (toLesson.getLessonDate().isBefore(monday) || toLesson.getLessonDate().isAfter(sunday)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private List<Long> myClassRoomIds(Long studentId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        return ids.isEmpty() ? NO_CLASS_ROOM : ids;
    }

    /**
     * 학생·학부모에게 갈 공지. <b>사유가 본문에 들어간다</b> — 그게 이 알림의 핵심이다.
     *
     * <p>scope는 STUDENT다. CLASS로 보내면 같은 반 20명이 이 학생의 사유를 읽는다.
     */
    private Notice publishNotice(LessonChangeRequest request, Teacher teacher) {
        String body = """
            %s 학생의 수업일이 변경되었습니다.

            변경 전 · %s
            변경 후 · %s

            사유 · %s"""
            .formatted(
                request.getStudent().getName(),
                slotText(request.getFromLesson()),
                slotText(request.getToLesson()),
                request.getReason());
        return noticeService.publishForStudent(
            "수업일 변경 안내", body, request.getStudent(), teacher);
    }

    /** "8월 13일(목) A고 2학년 목요일반 19:00". 슬롯이 없으면 시각을 빼고 쓴다. */
    private String slotText(Lesson lesson) {
        String head = "%s %s".formatted(
            lesson.getLessonDate().format(NOTICE_DATE), lesson.getClassRoom().getName());
        return lesson.getClassRoom().scheduleOn(lesson.getLessonDate())
            .map(ClassRoomSchedule::getStartTime)
            .map(start -> head + " " + start.format(NOTICE_TIME))
            .orElse(head);
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
