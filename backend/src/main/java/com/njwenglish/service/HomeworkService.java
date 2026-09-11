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
import com.njwenglish.dto.homework.HomeworkGridSaveRequest;
import com.njwenglish.dto.homework.HomeworkListItemResponse;
import com.njwenglish.dto.homework.HomeworkUpdateRequest;
import com.njwenglish.dto.homework.PendingHomeworkResponse;
import com.njwenglish.dto.homework.ResubmitOpenRequest;
import com.njwenglish.dto.homework.ResubmitOpenResponse;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
     *
     * <p>GRID 열은 이 경로로 dueAt을 받지 않는다. GRID에서 dueAt은 "재제출 마감"이고
     * 그 값은 openResubmit/closeResubmit에서만 관리한다 — 열려 있지 않은 열은 dueAt이
     * null이라 canExtendTo(!newDueAt.isBefore(dueAt))가 NPE를 낸다. 조용히 무시하지 않고
     * 400으로 막아 재제출 경로가 아닌 곳에서 마감이 새어 들어오지 못하게 한다.
     */
    @Transactional
    public HomeworkDetailResponse update(Long homeworkId, HomeworkUpdateRequest request) {
        Homework homework = findHomework(homeworkId);
        if (homework.isGrid() && request.dueAt() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
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
        if (submissionRepository.countGradedOrSubmitted(homeworkId) > 0) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }

        // findByHomeworkForTeacher는 GRID를 재제출 대상으로 좁혀 놓았다(T-7).
        // 여기서 그걸 쓰면 대상 밖 학생의 사진이 S3에 남는다 — 전량 id 조회를 따로 쓴다.
        List<Long> submissionIds = submissionRepository.findAllIdsByHomeworkId(homeworkId);
        List<SubmissionPhoto> photos = submissionIds.isEmpty()
            ? List.of() : submissionPhotoRepository.findBySubmissionIds(submissionIds);

        submissionRepository.deleteByHomeworkId(homeworkId);
        homeworkRepository.delete(homework);

        photos.forEach(photo -> presignedUrlProvider.deleteQuietly(photo.getS3Key()));
    }

    /** T-1 대시보드. 안 낸 학생이 하나라도 남은 숙제만 나온다. */
    @Transactional(readOnly = true)
    public List<PendingHomeworkResponse> pending() {
        return homeworkRepository.findPendingSummaries().stream()
            .map(row -> new PendingHomeworkResponse(row.getHomeworkId(), row.getTitle(),
                row.getClassRoomName(), row.getDueAt(), row.getNotSubmitted()))
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

        /*
         * cellsByStudent엔 그 반을 나간 학생의 옛 칸도 남아 있다 — 숙제 데이터는 지우지 않고
         * 상태만 바꾸기 때문이다(StudentService 퇴원 처리). targets/awaiting을 이 맵 전체로
         * 세면 화면에 안 보이는 학생이 배지 숫자에 섞여 들어간다. 반드시 재원생 명단(students)
         * 기준으로 걸러낸 칸만 센다. "간단하게" cellsByStudent.values()로 되돌리지 마라.
         */
        List<Submission> rosterCells = students.stream()
            .map(student -> cellsByStudent.get(student.getId()))
            .filter(Objects::nonNull)
            .toList();

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

        int targets = (int) rosterCells.stream()
            .filter(Submission::isResubmitTarget).count();
        /*
         * 낸 학생 수. isSubmitted()가 아니라 resolvedByResubmission으로 센다 —
         * 선생님이 그리드에서 직접 ⭕를 찍은 칸과, 학생이 재제출해서 ⭕가 된 칸을
         * 구분해야 열 머리의 "N명 제출"이 실제로 볼 사진이 있는 수와 맞는다.
         */
        int resubmitted = (int) rosterCells.stream()
            .filter(Submission::isResolvedByResubmission).count();

        return new HomeworkGridResponse.ColumnInfo(column.getId(), column.getTitle(), column.getDescription(),
            column.getSortOrder(), column.getDueAt(), targets, resubmitted, cellInfos);
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

    /**
     * 그리드 한 장 통째 저장. 열 생성과 채점이 한 트랜잭션에서 끝난다.
     *
     * <p><b>배열에서 빠진 열을 지우지 않는다.</b> 삭제는 delete()뿐이다 —
     * PUT이 열을 지우면 통신이 끊긴 저장 한 번에 제출물이 날아간다.
     *
     * <p>요청에 없는 학생의 칸도 건드리지 않는다. 두 사람이 동시에 열어도
     * 서로의 입력을 지우지 않는다.
     */
    @Transactional
    public HomeworkGridResponse saveGrid(HomeworkGridSaveRequest request) {
        Lesson lesson = lessonRepository.findById(request.lessonId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Map<Long, Homework> existing = homeworkRepository.findGridColumns(lesson.getId())
            .stream().collect(Collectors.toMap(Homework::getId, column -> column));
        // 칸 키는 "homeworkId:studentId" 문자열이다 — 열 하나에 학생 하나가 물려야 유일하다.
        Map<String, Submission> cells = submissionRepository
            .findByLessonForGrid(lesson.getId()).stream()
            .collect(Collectors.toMap(HomeworkService::cellKey, cell -> cell));
        // 명단은 한 번만 읽는다. 열마다 읽으면 새 열 3개에 같은 쿼리가 3번 나간다
        Map<Long, Student> roster = enrollmentRepository
            .findActiveStudents(lesson.getClassRoom().getId(), lesson.getLessonDate())
            .stream().collect(Collectors.toMap(Student::getId, student -> student,
                (first, duplicate) -> first, LinkedHashMap::new));

        for (HomeworkGridSaveRequest.Column column : request.columns()) {
            Homework homework = column.homeworkId() == null
                ? createGridColumn(lesson, column, cells, roster)
                : renameGridColumn(lesson, existing, column);
            applyCells(homework, column, cells, roster);
        }
        return grid(lesson.getId());
    }

    /**
     * 새 열. 열을 만드는 순간 대상 전원의 칸을 미리 깐다 — 그래야 미채점이 회색으로 보인다.
     *
     * <p>방금 깐 칸을 cells 맵에도 넣어 둔다. 새 열과 그 채점을 한 요청에 같이 보내도
     * applyCells가 그 칸을 곧바로 찾을 수 있다 — saveAll의 반환값이 아니라 방금 만든
     * rows를 그대로 쓴다. IDENTITY라 flush 전에도 homeworkId·studentId는 이미 채워져 있다.
     */
    private Homework createGridColumn(Lesson lesson, HomeworkGridSaveRequest.Column column,
                                      Map<String, Submission> cells,
                                      Map<Long, Student> roster) {
        Homework homework = homeworkRepository.save(Homework.gridColumn(
            lesson.getClassRoom(), lesson, currentTeacher(),
            column.title(), column.sortOrder()));

        List<Submission> rows = roster.values().stream()
            .map(student -> Submission.notSubmitted(homework, student))
            .toList();
        submissionRepository.saveAll(rows);
        rows.forEach(row -> cells.put(cellKey(row), row));
        return homework;
    }

    /**
     * 남의 수업 열을 밀어 넣으면 그 반 그리드가 오염된다. id가 이 수업 것인지 대조한다.
     *
     * <p>existing은 findGridColumns(lesson.getId())로 이미 이 수업으로 걸러서 가져오지만,
     * 그 필터를 유일한 방어선으로 삼지 않는다 — lesson 소속을 여기서 한 번 더 확인한다.
     */
    private Homework renameGridColumn(Lesson lesson, Map<Long, Homework> existing,
                                      HomeworkGridSaveRequest.Column column) {
        Homework homework = existing.get(column.homeworkId());
        if (homework == null || homework.getLesson() == null
            || !homework.getLesson().getId().equals(lesson.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        homework.renameColumn(column.title(), column.sortOrder());
        return homework;
    }

    /**
     * 칸 채점. 새로 만든 열의 칸도 createGridColumn이 cells 맵에 미리 넣어 두므로
     * 같은 요청 안에서 바로 채점할 수 있다.
     *
     * <p><b>명단에 있는데 칸이 없으면 그 자리에서 만든다.</b> grid()가 그런 학생에게도
     * 빈 칸을 내려주기 때문에(칸 없는 학생도 명단에 남는다) 화면에는 채점 가능한 칸이 뜬다.
     * 여기서 건너뛰면 선생님이 매긴 값이 200 OK와 함께 조용히 사라진다.
     * 열을 만든 뒤 잘못된 퇴원이 정정되면 실제로 이 상태가 된다 —
     * findActiveStudents가 보는 student.status는 그 시점의 값이 아니라 현재 값이다.
     *
     * <p>명단에도 없는 studentId는 조용히 건너뛴다. 잘못된 값으로 500을 내는 것보다 낫다.
     */
    private void applyCells(Homework homework, HomeworkGridSaveRequest.Column column,
                            Map<String, Submission> cells, Map<Long, Student> roster) {
        for (HomeworkGridSaveRequest.Cell requested : column.cells()) {
            Submission cell = cells.get(
                cellKey(homework.getId(), requested.studentId()));
            if (cell == null) {
                Student student = roster.get(requested.studentId());
                if (student == null) {
                    continue;
                }
                cell = submissionRepository.save(Submission.notSubmitted(homework, student));
                cells.put(cellKey(cell), cell);
            }
            cell.grade(requested.result(), requested.completionRate());
        }
    }

    private static String cellKey(Submission cell) {
        return cellKey(cell.getHomework().getId(), cell.getStudent().getId());
    }

    private static String cellKey(Long homeworkId, Long studentId) {
        return homeworkId + ":" + studentId;
    }

    /**
     * 재제출 열기. 이 순간부터 🔺·❌를 받은 학생만 온라인으로 낼 수 있다.
     *
     * <p>이미 열린 열에 다시 부르면 <b>마감 연장</b>이다. 앞당기면 이미 낸 학생의
     * is_late를 전부 재계산해야 해서 막는다.
     *
     * <p>요청 후 선생님이 어떤 칸을 🔺❌로 새로 고치면 그 학생도 자동으로 대상이 된다.
     * 다시 열 필요가 없다 — 판정이 result를 실시간으로 보기 때문이다.
     */
    @Transactional
    public ResubmitOpenResponse openResubmit(Long homeworkId, ResubmitOpenRequest request) {
        Homework homework = findHomework(homeworkId);
        if (!homework.isGrid()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<Submission> targets = submissionRepository.findResubmitTargets(homeworkId);
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_RESUBMIT_TARGET);
        }

        // 마감은 선생님이 정한다. 기본값을 되살리지 마라
        if (request.dueAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (homework.isResubmitOpen() && !homework.canExtendTo(request.dueAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        homework.openResubmit(request.dueAt(), request.title(), request.description());

        return new ResubmitOpenResponse(targets.size(), request.dueAt());
    }

    /**
     * 잘못 연 열을 되돌린다. <b>이미 낸 학생이 있으면 409다</b> —
     * 올린 사진이 조용히 사라지면 안 된다.
     */
    @Transactional
    public void closeResubmit(Long homeworkId) {
        Homework homework = findHomework(homeworkId);
        if (!homework.isGrid()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (submissionRepository.countByHomeworkIdAndStatusNot(
            homeworkId, SubmissionStatus.NOT_SUBMITTED) > 0) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }
        homework.closeResubmit();
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
                (int) row.getTotal(), (int) row.getNotSubmitted(), (int) row.getSubmitted()));
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
