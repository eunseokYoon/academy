package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.dto.homework.HomeworkCountsResponse;
import com.njwenglish.dto.homework.HomeworkCreateRequest;
import com.njwenglish.dto.homework.HomeworkCreateResponse;
import com.njwenglish.dto.homework.HomeworkDetailResponse;
import com.njwenglish.dto.homework.HomeworkGridResponse;
import com.njwenglish.dto.homework.HomeworkListItemResponse;
import com.njwenglish.dto.homework.HomeworkUpdateRequest;
import com.njwenglish.dto.homework.PendingHomeworkResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.SubmissionPhoto;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionPhotoRepository.PhotoCountRow;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.SubmissionRepository.CountRow;
import com.njwenglish.repository.TeacherRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-6 숙제 출제·관리.
 *
 * <p>핵심은 출제 즉시 대상 전원의 submissions를 NOT_SUBMITTED로 깔아 두는 것이다.
 * 그래서 "행이 있으면 제출함"이 절대 성립하지 않는다 — 삭제 차단도 상태로 판단한다.
 */
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 기간 필터를 생략했을 때 쓰는 열린 경계. 리포지토리에 null을 넘기면
     * PostgreSQL이 타임스탬프 파라미터의 타입을 추론하지 못해 쿼리가 실패한다.
     */
    private static final OffsetDateTime OPEN_START =
        OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime OPEN_END =
        OffsetDateTime.of(2999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionPhotoRepository submissionPhotoRepository;
    private final ClassRoomRepository classRoomRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final HomeworkTemplateService templateService;
    private final PresignedUrlProvider presignedUrlProvider;

    /**
     * 출제. 대상은 <b>마감일 기준</b> 재원생 전원이다. 오늘 기준이 아니다 —
     * 다음 주 마감으로 미리 내면 그때 다니는 학생이 대상이어야 한다.
     *
     * <p>일부 학생만 지정하는 기능은 없다. 대상은 언제나 반 전체다.
     */
    @Transactional
    public HomeworkCreateResponse create(HomeworkCreateRequest request) {
        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Lesson lesson = findLessonInClassRoom(request.lessonId(), classRoom);

        Homework homework = homeworkRepository.save(Homework.create(
            classRoom, lesson, currentTeacher(),
            request.title(), request.description(), request.dueAt()));

        List<Student> students = enrollmentRepository.findActiveStudents(
            classRoom.getId(), toKstDate(request.dueAt()));
        List<Submission> rows = students.stream()
            .map(student -> Submission.notSubmitted(homework, student))
            .toList();
        submissionRepository.saveAll(rows);

        if (request.templateId() != null) {
            templateService.increaseUseCount(request.templateId());
        }
        if (request.saveAsTemplate()) {
            templateService.save(request.title(), request.description());
        }

        return new HomeworkCreateResponse(homework.getId(), rows.size());
    }

    @Transactional(readOnly = true)
    public PageResponse<HomeworkListItemResponse> list(Long classRoomId, OffsetDateTime from,
                                                       OffsetDateTime to, Pageable pageable) {
        Page<Homework> page = homeworkRepository.search(classRoomId,
            from == null ? OPEN_START : from, to == null ? OPEN_END : to, pageable);
        Map<Long, HomeworkCountsResponse> counts = countsOf(
            page.getContent().stream().map(Homework::getId).toList());

        return PageResponse.from(page.map(homework -> HomeworkListItemResponse.of(
            homework, counts.getOrDefault(homework.getId(), HomeworkCountsResponse.empty(0)))));
    }

    @Transactional(readOnly = true)
    public HomeworkDetailResponse detail(Long homeworkId) {
        Homework homework = findHomework(homeworkId);
        return HomeworkDetailResponse.of(homework, countsOf(List.of(homeworkId))
            .getOrDefault(homeworkId, HomeworkCountsResponse.empty(0)));
    }

    /**
     * 내용·마감 수정. <b>마감은 늦추는 방향만</b> 허용한다.
     * 앞당기면 이미 제출한 학생의 is_late를 전부 다시 계산해야 한다.
     */
    @Transactional
    public HomeworkDetailResponse update(Long homeworkId, HomeworkUpdateRequest request) {
        Homework homework = findHomework(homeworkId);
        homework.edit(request.title(), request.description(),
            findLessonInClassRoom(request.lessonId(), homework.getClassRoom()));

        if (request.dueAt() != null) {
            if (!homework.canExtendTo(request.dueAt())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            homework.extendDueAt(request.dueAt());
        }
        return detail(homeworkId);
    }

    /**
     * 삭제. 차단 조건은 "행의 존재"가 아니라 "상태"다 —
     * 출제 시 전원의 행이 NOT_SUBMITTED로 깔리므로 행은 언제나 있다.
     *
     * <p>전원 미제출이라도 사진은 붙어 있을 수 있다(제출 전에도 올릴 수 있다).
     * submissions를 먼저 지운다. submission_photos는 DB의 ON DELETE CASCADE가 따라 지운다.
     */
    @Transactional
    public void delete(Long homeworkId) {
        Homework homework = findHomework(homeworkId);
        if (submissionRepository.countByHomeworkIdAndStatusNot(
            homeworkId, SubmissionStatus.NOT_SUBMITTED) > 0) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }

        List<Long> submissionIds = submissionRepository.findByHomeworkForTeacher(homeworkId)
            .stream().map(Submission::getId).toList();
        List<SubmissionPhoto> photos = submissionIds.isEmpty()
            ? List.of() : submissionPhotoRepository.findBySubmissionIds(submissionIds);

        submissionRepository.deleteByHomeworkId(homeworkId);
        homeworkRepository.delete(homework);

        photos.forEach(photo -> presignedUrlProvider.deleteQuietly(photo.getS3Key()));
    }

    /** T-1 대시보드. 미제출·확인대기가 하나라도 남은 숙제만 나온다. */
    @Transactional(readOnly = true)
    public List<PendingHomeworkResponse> pending() {
        return homeworkRepository.findPendingSummaries().stream()
            .map(row -> new PendingHomeworkResponse(row.getHomeworkId(), row.getTitle(),
                row.getClassRoomName(), row.getDueAt(),
                row.getNotSubmitted(), row.getAwaitingCheck()))
            .toList();
    }

    // ---------- T-6b 숙제 그리드 ----------

    /**
     * 그리드 한 장. 페이징하지 않는다 — 반 단위(최대 30명)라 한 화면에 다 보여주는 것이 맞다.
     *
     * <p>명단은 <b>그 수업일 기준</b> 재원생이다. 퇴원생이 남으면 채점할 수 없는 칸이 생긴다.
     * 칸이 아직 없는 학생(열을 만든 뒤 들어온 학생)도 명단에는 남고 빈 칸으로 나간다.
     */
    @Transactional(readOnly = true)
    public HomeworkGridResponse grid(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ClassRoom classRoom = lesson.getClassRoom();

        List<Student> students = enrollmentRepository
            .findActiveStudents(classRoom.getId(), lesson.getLessonDate());
        List<Homework> columns = homeworkRepository.findGridColumns(lessonId);
        List<Submission> cells = submissionRepository.findByLessonForGrid(lessonId);

        Map<Long, Integer> photoCounts = photoCountsOf(
            cells.stream().map(Submission::getId).toList());

        // homeworkId → studentId → 칸
        Map<Long, Map<Long, Submission>> byColumn = new HashMap<>();
        for (Submission cell : cells) {
            byColumn.computeIfAbsent(cell.getHomework().getId(), key -> new HashMap<>())
                .put(cell.getStudent().getId(), cell);
        }

        List<HomeworkGridResponse.ColumnInfo> columnInfos = columns.stream()
            .map(column -> toColumnInfo(column, students,
                byColumn.getOrDefault(column.getId(), Map.of()), photoCounts))
            .toList();

        return new HomeworkGridResponse(
            new HomeworkGridResponse.LessonInfo(lesson.getId(), lesson.getLessonDate(),
                classRoom.getId(), classRoom.getName()),
            students.stream()
                .map(student -> new HomeworkGridResponse.StudentInfo(
                    student.getId(), student.getName()))
                .toList(),
            columnInfos);
    }

    private HomeworkGridResponse.ColumnInfo toColumnInfo(
            Homework column, List<Student> students,
            Map<Long, Submission> cellsByStudent, Map<Long, Integer> photoCounts) {

        List<HomeworkGridResponse.CellInfo> cellInfos = students.stream()
            .map(student -> {
                Submission cell = cellsByStudent.get(student.getId());
                if (cell == null) {
                    return new HomeworkGridResponse.CellInfo(student.getId(),
                        null, null, false, SubmissionStatus.NOT_SUBMITTED, 0, false);
                }
                return new HomeworkGridResponse.CellInfo(student.getId(),
                    cell.getResult(), cell.getCompletionRate(),
                    cell.isResolvedByResubmission(), cell.getStatus(),
                    photoCounts.getOrDefault(cell.getId(), 0), cell.hasVideo());
            })
            .toList();

        int targets = (int) cellsByStudent.values().stream()
            .filter(Submission::isResubmitTarget).count();
        int awaiting = (int) cellsByStudent.values().stream()
            .filter(Submission::isSubmitted).count();

        return new HomeworkGridResponse.ColumnInfo(column.getId(), column.getTitle(),
            column.getSortOrder(), column.getDueAt(), targets, awaiting, cellInfos);
    }

    /** 사진 수는 한 번에 가져온다. 칸마다 세면 100쿼리가 나간다. */
    private Map<Long, Integer> photoCountsOf(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (PhotoCountRow row : submissionPhotoRepository.countBySubmissionIds(submissionIds)) {
            counts.put(row.getSubmissionId(), (int) row.getPhotoCount());
        }
        return counts;
    }

    // ---------- 내부 ----------

    Homework findHomework(Long homeworkId) {
        return homeworkRepository.findWithClassRoom(homeworkId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    Map<Long, HomeworkCountsResponse> countsOf(List<Long> homeworkIds) {
        if (homeworkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, HomeworkCountsResponse> counts = new HashMap<>();
        for (CountRow row : submissionRepository.countsByHomeworkIds(homeworkIds)) {
            counts.put(row.getHomeworkId(), new HomeworkCountsResponse(
                (int) row.getTotal(), (int) row.getNotSubmitted(),
                (int) row.getSubmitted(), (int) row.getChecked()));
        }
        return counts;
    }

    /**
     * 수업 연결은 선택이다. 다만 다른 반의 수업에 붙이면 캘린더 색띠가 엉뚱한 반에 뜬다.
     */
    private Lesson findLessonInClassRoom(Long lessonId, ClassRoom classRoom) {
        if (lessonId == null) {
            return null;
        }
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!lesson.getClassRoom().getId().equals(classRoom.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return lesson;
    }

    /**
     * 마감 시각을 KST 날짜로 본다. 요청이 다른 오프셋으로 들어와도 재원 판정 기준일이 흔들리지 않는다.
     */
    private LocalDate toKstDate(OffsetDateTime dueAt) {
        return dueAt.atZoneSameInstant(KST).toLocalDate();
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
