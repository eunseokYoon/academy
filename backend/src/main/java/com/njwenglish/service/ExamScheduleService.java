package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.score.ExamScheduleCreateRequest;
import com.njwenglish.dto.score.ExamScheduleResponse;
import com.njwenglish.dto.score.ExamScheduleUpdateRequest;
import com.njwenglish.dto.score.NextExamResponse;
import com.njwenglish.dto.score.StudentExamScheduleResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.ExamScheduleRepository;
import com.njwenglish.repository.ScoreRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-11 시험 일정. <b>여기 등록된 일정이 학생·학부모 홈 D-day의 유일한 근거다.</b>
 * 등록이 빠진 반은 D-day가 표시되지 않는다.
 *
 * <p>시험 일정을 외부에서 자동 수집하지 마라. 선생님이 직접 입력한다.
 */
@Service
@RequiredArgsConstructor
public class ExamScheduleService {

    /** D-day는 하루 차이가 그대로 보이는 값이라 기준 시간대를 명시한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ExamScheduleRepository examScheduleRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ScoreRepository scoreRepository;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional(readOnly = true)
    public List<ExamScheduleResponse> list(Long classRoomId, Short year) {
        return examScheduleRepository.search(classRoomId, year).stream()
            .map(ExamScheduleResponse::from)
            .toList();
    }

    @Transactional
    public ExamScheduleResponse create(ExamScheduleCreateRequest request) {
        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireValidPeriod(request.startDate(), request.endDate());

        // UNIQUE (class_room_id, year, semester, exam_type). DB가 잡기 전에 409로 돌려준다
        if (examScheduleRepository.existsByClassRoomIdAndYearAndSemesterAndExamType(
                classRoom.getId(), request.year(), request.semester(), request.examType())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        return ExamScheduleResponse.from(examScheduleRepository.save(ExamSchedule.create(
            classRoom, request.year(), request.semester(), request.examType(),
            request.startDate(), request.endDate(), request.scopeNote())));
    }

    @Transactional
    public ExamScheduleResponse update(Long examScheduleId, ExamScheduleUpdateRequest request) {
        ExamSchedule schedule = findSchedule(examScheduleId);
        LocalDate startDate = request.startDate() == null
            ? schedule.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? schedule.getEndDate() : request.endDate();
        requireValidPeriod(startDate, endDate);

        schedule.reschedule(startDate, endDate,
            request.scopeNote() == null ? schedule.getScopeNote() : request.scopeNote());
        return ExamScheduleResponse.from(schedule);
    }

    /**
     * 내신 성적이 이 일정을 참조하고 있으면 FK로 막히기 전에 409로 돌려준다.
     * 지우고 싶으면 연결된 성적을 먼저 정리해야 한다.
     */
    @Transactional
    public void delete(Long examScheduleId) {
        ExamSchedule schedule = findSchedule(examScheduleId);
        if (scoreRepository.existsByExamScheduleId(examScheduleId)) {
            throw new BusinessException(ErrorCode.EXAM_SCHEDULE_HAS_SCORES);
        }
        examScheduleRepository.delete(schedule);
    }

    @Transactional(readOnly = true)
    public List<StudentExamScheduleResponse> mySchedules() {
        return forStudent(studentAccessGuard.requireSelf());
    }

    /** 학부모. 첫 줄이 requireAccessible이다 — 자녀가 아니면 403이다. */
    @Transactional(readOnly = true)
    public List<StudentExamScheduleResponse> childSchedules(Long studentId) {
        return forStudent(studentAccessGuard.requireAccessible(studentId));
    }

    /**
     * 홈 화면 D-day. 재원 중인 반 전체에서 가장 가까운 일정 하나다.
     * <b>없으면 Optional.empty()다.</b> 0이나 임의 값을 채우지 마라.
     *
     * <p>다자녀 학부모는 자녀별로 다른 값을 본다 — 자녀가 바뀌면 반 목록이 바뀌기 때문이다.
     */
    @Transactional(readOnly = true)
    public Optional<NextExamResponse> findNextExam(Long studentId) {
        LocalDate today = LocalDate.now(KST);
        List<Long> classRoomIds = enrollmentRepository.findActiveClassRoomIds(studentId);
        if (classRoomIds.isEmpty()) {
            return Optional.empty();
        }
        return examScheduleRepository.findNext(classRoomIds, today)
            .map(schedule -> NextExamResponse.from(schedule, today));
    }

    // ---------- 내부 ----------

    private List<StudentExamScheduleResponse> forStudent(Student student) {
        List<Long> classRoomIds = enrollmentRepository.findActiveClassRoomIds(student.getId());
        if (classRoomIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KST);
        return examScheduleRepository.findByClassRoomIds(classRoomIds).stream()
            .map(schedule -> StudentExamScheduleResponse.from(schedule, today))
            .toList();
    }

    private ExamSchedule findSchedule(Long examScheduleId) {
        return examScheduleRepository.findWithClassRoom(examScheduleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
