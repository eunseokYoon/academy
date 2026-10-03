package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.SubmissionMediaKeys;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.dto.homework.HomeworkBriefResponse;
import com.njwenglish.dto.homework.HomeworkCountsResponse;
import com.njwenglish.dto.homework.HomeworkNoteResponse;
import com.njwenglish.dto.homework.HomeworkSubmissionsResponse;
import com.njwenglish.dto.homework.MediaUploadUrlResponse;
import com.njwenglish.dto.homework.ParentHomeworkResponse;
import com.njwenglish.dto.homework.ParentSubmissionPhotosResponse;
import com.njwenglish.dto.homework.PhotoRegisterRequest;
import com.njwenglish.dto.homework.PhotoRegisterResponse;
import com.njwenglish.dto.homework.PhotoUploadUrlRequest;
import com.njwenglish.dto.homework.StudentHomeworkDetailResponse;
import com.njwenglish.dto.homework.StudentHomeworkListItemResponse;
import com.njwenglish.dto.homework.StudentHomeworkResponse;
import com.njwenglish.dto.homework.StudentSubmissionResponse;
import com.njwenglish.dto.homework.SubmissionDetailResponse;
import com.njwenglish.dto.homework.SubmissionListItemResponse;
import com.njwenglish.dto.homework.SubmissionPhotoResponse;
import com.njwenglish.dto.homework.SubmissionVideoResponse;
import com.njwenglish.dto.homework.SubmitResponse;
import com.njwenglish.dto.homework.VideoRegisterRequest;
import com.njwenglish.dto.homework.VideoUploadUrlRequest;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.SubmissionPhoto;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionPhotoRepository.PhotoCountRow;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-2 · S-3 · S-4 학생 제출과 T-7 선생님 보기 화면.
 *
 * <p>사진은 서버를 거치지 않는다. presigned URL을 발급하면 클라이언트가 S3로 직접 PUT하고,
 * 서버는 발급한 s3Key가 맞는지만 대조해 행을 남긴다.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    /**
     * 사진 20장, 장당 10MB. 리사이즈를 거치면 장당 1MB 미만이라 넉넉한 상한이다.
     * 10장에서 올렸다(2026-09-29, 학생 요청 — 재제출 분량이 10장을 넘는다).
     * 웹 StudentHomeworkDetailPage.tsx 의 MAX_PHOTOS, 앱 kMaxPhotos 와 같이 고쳐라.
     */
    private static final int MAX_PHOTOS = 20;
    private static final int MAX_PHOTO_BYTES = 10 * 1024 * 1024;

    /**
     * 영상은 제출물당 1개, 100MB까지. 폰으로 1분 안쪽 분량이다.
     *
     * <p>사진과 달리 브라우저에서 압축할 수 없어 원본이 그대로 올라온다. 상한이 없으면
     * 4K로 길게 찍은 단일 파일이 500MB를 넘고, 200명 규모에서 저장 비용이 예측 불가능해진다.
     * 서버가 재생 길이를 잴 수 없어 용량으로만 막는다.
     */
    private static final int MAX_VIDEO_BYTES = 100 * 1024 * 1024;

    private final SubmissionRepository submissionRepository;
    private final SubmissionPhotoRepository photoRepository;
    private final HomeworkService homeworkService;
    private final StudentAccessGuard studentAccessGuard;
    private final PresignedUrlProvider presignedUrlProvider;
    private final SubmissionMediaKeys mediaKeys;
    private final LessonRepository lessonRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- 학생 (S-2 · S-3 · S-4) ----------

    /**
     * 미제출이면서 마감 임박한 것이 위로 온다. 남은 시간은 서버가 계산한다.
     *
     * <p>year·month는 화면의 달 필터다. 걸면 <b>그 달에 수업이 있는 숙제만</b> 내려간다.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentHomeworkListItemResponse> myHomeworks(String status,
                                                                     Integer year, Integer month,
                                                                     Pageable pageable) {
        Student me = studentAccessGuard.requireSelf();
        Page<Submission> page = submissionRepository.findByStudent(
            me.getId(), status, monthStart(year, month), monthEnd(year, month), pageable);

        List<Long> ids = page.getContent().stream().map(Submission::getId).toList();
        Map<Long, Integer> photoCounts = photoCountsOf(ids);
        OffsetDateTime now = OffsetDateTime.now();

        return PageResponse.from(page.map(submission -> {
            Homework homework = submission.getHomework();
            return new StudentHomeworkListItemResponse(
                homework.getId(), homework.getTitle(), homework.getDescription(), homework.getClassRoom().getName(),
                homework.getKind(),
                homework.getLesson() == null ? null : homework.getLesson().getLessonDate(),
                submission.getResult(), submission.getCompletionRate(),
                submission.isResolvedByResubmission(), submission.isResubmitTarget(),
                homework.getDueAt(), submission.getStatus(), submission.isLate(),
                photoCounts.getOrDefault(submission.getId(), 0),
                submission.hasVideo(),
                remainingMinutes(now, homework.getDueAt()));
        }));
    }

    @Transactional(readOnly = true)
    public StudentHomeworkDetailResponse myHomework(Long homeworkId) {
        Submission submission = findMySubmission(homeworkId);

        return new StudentHomeworkDetailResponse(
            StudentHomeworkResponse.from(submission.getHomework()),
            new StudentSubmissionResponse(submission.getId(), submission.getStatus(),
                submission.getSubmittedAt(), submission.isLate(),
                photosOf(submission.getId()), videoOf(submission)),
            submission.isResubmitTarget());
    }

    /**
     * 업로드 URL 발급. s3Key에 제출물 id로 만든 서명이 박혀 나가고, 등록(2단계)에서 대조한다.
     * 그래서 클라이언트가 임의 경로나 남의 키를 보내도 통과하지 못한다.
     */
    @Transactional(readOnly = true)
    public MediaUploadUrlResponse issueUploadUrl(Long homeworkId, PhotoUploadUrlRequest request) {
        Submission submission = findEditableSubmission(homeworkId);

        if (!mediaKeys.isSupportedPhotoType(request.contentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.bytes() > MAX_PHOTO_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        requirePhotoRoom(submission.getId());

        String s3Key = mediaKeys.issuePhoto(
            submission.getId(), request.contentType(), LocalDate.now());
        return new MediaUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, request.contentType(), request.bytes()), s3Key);
    }

    /** S3 PUT이 끝난 뒤의 등록. 여기서 서명을 대조하지 않으면 앞의 발급이 무의미해진다. */
    @Transactional
    public PhotoRegisterResponse registerPhoto(Long homeworkId, PhotoRegisterRequest request) {
        Submission submission = findEditableSubmission(homeworkId);

        if (!mediaKeys.matchesPhoto(request.s3Key(), submission.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        requirePhotoRoom(submission.getId());

        SubmissionPhoto photo = photoRepository.save(SubmissionPhoto.of(
            submission, request.s3Key(), request.sortOrder(), request.bytes()));
        return new PhotoRegisterResponse(photo.getId(),
            (int) photoRepository.countBySubmissionId(submission.getId()));
    }

    @Transactional
    public void deletePhoto(Long homeworkId, Long photoId) {
        Submission submission = findEditableSubmission(homeworkId);
        SubmissionPhoto photo = photoRepository
            .findByIdAndSubmissionId(photoId, submission.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        photoRepository.delete(photo);
        presignedUrlProvider.deleteQuietly(photo.getS3Key());
    }

    /**
     * 영상 업로드 URL 발급. 사진과 같은 3단계지만 <b>리사이즈가 없다</b> —
     * 브라우저에서 영상을 압축할 방법이 없어 폰 원본이 그대로 올라온다.
     * 그래서 용량 상한이 사진보다 훨씬 중요하다.
     */
    @Transactional(readOnly = true)
    public MediaUploadUrlResponse issueVideoUploadUrl(Long homeworkId,
                                                      VideoUploadUrlRequest request) {
        Submission submission = findEditableSubmission(homeworkId);

        if (!mediaKeys.isSupportedVideoType(request.contentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.bytes() > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String s3Key = mediaKeys.issueVideo(
            submission.getId(), request.contentType(), LocalDate.now());
        return new MediaUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, request.contentType(), request.bytes()), s3Key);
    }

    /**
     * 영상 등록. 제출물당 1개라 이미 있으면 <b>덮어쓴다</b> — 개수 초과로 막지 않는다.
     * 학생이 다시 찍어 올리는 건 정상 흐름이고, 이전 파일은 S3에서 지운다.
     */
    @Transactional
    public SubmissionVideoResponse registerVideo(Long homeworkId, VideoRegisterRequest request) {
        Submission submission = findEditableSubmission(homeworkId);

        if (!mediaKeys.matchesVideo(request.s3Key(), submission.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        String replaced = submission.attachVideo(request.s3Key(), request.bytes());
        if (replaced != null) {
            presignedUrlProvider.deleteQuietly(replaced);
        }
        return videoOf(submission);
    }

    @Transactional
    public void deleteVideo(Long homeworkId) {
        Submission submission = findEditableSubmission(homeworkId);
        String removed = submission.detachVideo();
        if (removed == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        presignedUrlProvider.deleteQuietly(removed);
    }

    /**
     * 제출 확정. <b>마감이 지나도 막지 않는다</b>(확정 정책). 늦음은 is_late로 남는다.
     * 아예 막으면 늦게라도 내는 학생을 선생님이 손으로 처리해야 한다.
     *
     * <p><b>GRID 재제출은 여기서 곧바로 ⭕가 된다.</b> 선생님이 확인해서 올리는 단계는 없다
     * (2026-08-09 확정). 마감을 넘겨 낸 것도 ⭕고 is_late만 남는다 — 내용이 부족하면
     * 선생님이 그리드에서 🔺·❌로 내리면 되고, 그때 grade가 재제출 표시를 내려 다시
     * 대상으로 되돌린다.
     *
     * <p>부작용이 하나 있다. ⭕가 되는 순간 이 학생은 재제출 대상이 아니게 되므로
     * {@link #findEditableSubmission}이 사진 추가·삭제·재제출을 전부 막는다. 즉
     * <b>한 번 내면 학생이 스스로 고칠 수 없다</b>(확정). 잘못 냈으면 선생님이
     * 그리드에서 되돌려 줘야 한다.
     */
    @Transactional
    public SubmitResponse submit(Long homeworkId) {
        Submission submission = findEditableSubmission(homeworkId);

        // 사진이든 영상이든 하나는 있어야 한다. 빈 제출은 선생님에게 보여줄 것이 없다
        int photoCount = (int) photoRepository.countBySubmissionId(submission.getId());
        if (photoCount == 0 && !submission.hasVideo()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        submission.submit(now, submission.getHomework().isLateAt(now));
        if (submission.getHomework().isGrid()) {
            submission.resolveByResubmission();
        }
        // 푸시 #2 — 학부모만. 선생님은 받지 않는다(T-1·그리드에서 본다)
        eventPublisher.publishEvent(PushEvent.of(PushTopic.HOMEWORK_SUBMITTED,
            submission.getStudent().getId(), null));

        return new SubmitResponse(submission.getId(), submission.getStatus(),
            submission.getSubmittedAt(), submission.isLate(), photoCount);
    }

    /**
     * S-2 맨 위의 「이번 주에 낼 것」. 선생님이 수업에 적은 숙제 글이다.
     *
     * <p>숙제 <b>줄</b> 목록이 아니다 — 그건 아래 두 구획(다시 제출 필요 / 지난 숙제)이
     * 맡는다. 종이로 해오는 숙제라 학생이 읽어야 하는 건 글이다.
     *
     * <p>글이 빈 수업은 넣지 않는다. 빈 구획이 뜨면 "낼 것이 없다"로 읽힌다.
     */
    @Transactional(readOnly = true)
    public List<HomeworkNoteResponse> myHomeworkNotes() {
        Student me = studentAccessGuard.requireSelf();
        return lessonRepository
            .findLastPublishedPerClassRoom(me.getId(), LocalDate.now())
            .stream()
            .filter(lesson -> lesson.getHomeworkNote() != null
                && !lesson.getHomeworkNote().isBlank())
            .map(lesson -> new HomeworkNoteResponse(
                lesson.getId(), lesson.getLessonDate(),
                lesson.getClassRoom().getName(),
                MonthWeeks.label(lesson.getMonth(), lesson.getWeek()),
                lesson.getHomeworkNote()))
            .toList();
    }

    // ---------- 선생님 (T-7) ----------

    /**
     * 격자 목록. 반 단위라 페이징하지 않는다.
     *
     * <p>쿼리는 세 번이다: 제출물+학생, 사진 전체, 피드백 있는 id.
     * 20명 각각 사진을 조회하면 21쿼리가 나간다.
     */
    @Transactional(readOnly = true)
    public HomeworkSubmissionsResponse submissionsOf(Long homeworkId) {
        Homework homework = homeworkService.findHomework(homeworkId);
        List<Submission> submissions = submissionRepository.findByHomeworkForTeacher(homeworkId);

        List<Long> ids = submissions.stream().map(Submission::getId).toList();
        Map<Long, List<SubmissionPhoto>> photos = photosBySubmission(ids);

        List<SubmissionListItemResponse> items = submissions.stream()
            .map(submission -> {
                List<SubmissionPhoto> own = photos.getOrDefault(submission.getId(), List.of());
                return new SubmissionListItemResponse(
                    submission.getId(), submission.getStudent().getId(),
                    submission.getStudent().getName(),
                    submission.getStatus(), submission.getSubmittedAt(), submission.isLate(),
                    own.size(),
                    own.isEmpty() ? null : presignedUrlProvider.readUrl(own.get(0).getS3Key()),
                    submission.hasVideo());
            })
            .toList();

        return new HomeworkSubmissionsResponse(
            HomeworkBriefResponse.from(homework),
            homeworkService.countsOf(List.of(homeworkId))
                .getOrDefault(homeworkId, HomeworkCountsResponse.empty(items.size())),
            items);
    }

    /**
     * 상세 뷰어. <b>보기 전용이다</b> — 확인·피드백 단계가 없어졌다(2026-08-09 확정).
     * prev·next가 있어야 목록으로 돌아가지 않고 연속으로 넘길 수 있다.
     */
    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(Long submissionId) {
        Submission submission = submissionRepository.findWithStudentAndHomework(submissionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Long> submitted = submissionRepository
            .findSubmittedIds(submission.getHomework().getId());
        int index = submitted.indexOf(submissionId);

        return new SubmissionDetailResponse(
            submission.getId(), submission.getStudent().getId(),
            submission.getStudent().getName(), submission.getStatus(),
            submission.getSubmittedAt(), submission.isLate(),
            photosOf(submissionId), videoOf(submission),
            index > 0 ? submitted.get(index - 1) : null,
            nextSubmitted(submitted, index),
            submission.getHomework().isGrid());
    }

    // ---------- 학부모 (P-3) ----------

    /**
     * <b>제목·채점 결과·제출 사진까지</b> 내려준다. 학생용 DTO를 재사용하면 사진 URL과 피드백이 따라 나간다.
     *
     * <p>첫 줄이 권한 검증이다. 학부모가 URL의 숫자만 바꿔 남의 아이 숙제를 보는 걸 여기서 막는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ParentHomeworkResponse> childHomeworks(Long studentId, String status,
                                                               Integer year, Integer month,
                                                               Pageable pageable) {
        Student child = studentAccessGuard.requireAccessible(studentId);
        Page<Submission> page = submissionRepository.findByStudent(
            child.getId(), status, monthStart(year, month), monthEnd(year, month), pageable);

        List<Long> ids = page.getContent().stream().map(Submission::getId).toList();
        Map<Long, Integer> photoCounts = photoCountsOf(ids);

        return PageResponse.from(page.map(submission -> {
            Homework homework = submission.getHomework();
            return new ParentHomeworkResponse(
                homework.getId(), homework.getTitle(), homework.getClassRoom().getName(),
                homework.getKind(),
                homework.getLesson() == null ? null : homework.getLesson().getLessonDate(),
                submission.getResult(), submission.getCompletionRate(),
                submission.isResolvedByResubmission(),
                homework.getDueAt(), submission.getStatus(), submission.isLate(),
                photoCounts.getOrDefault(submission.getId(), 0));
        }));
    }

    /**
     * 자녀가 낸 숙제 사진. <b>첫 줄이 requireAccessible이다</b> —
     * 학부모 A가 학부모 B의 자녀 studentId를 넣으면 여기서 403이다.
     *
     * <p>영상과 description은 담지 않는다. 열린 것은 사진뿐이다(2026-09-10).
     */
    @Transactional(readOnly = true)
    public ParentSubmissionPhotosResponse childSubmissionPhotos(Long studentId,
                                                                Long homeworkId) {
        Student child = studentAccessGuard.requireAccessible(studentId);
        Submission submission = submissionRepository
            .findByHomeworkAndStudent(homeworkId, child.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new ParentSubmissionPhotosResponse(
            submission.getHomework().getId(), submission.getHomework().getTitle(),
            photosOf(submission.getId()));
    }

    // ---------- 내부 ----------

    /**
     * 화면이 고른 달. <b>연·월 중 하나라도 없으면 null</b>이고 그러면 범위를 안 건다 —
     * 화면이 연도만 바꾸는 순간이 있어서, 빠진 쪽을 1월이나 올해로 채우면 학생이 고르지도
     * 않은 달의 숙제만 보인다.
     *
     * <p>값을 검사하는 이유는 YearMonth.of가 DateTimeException을 던져 <b>500이 되기</b>
     * 때문이다. 잘못 만든 URL은 400이어야 한다. AttendanceService.validMonth와 같은 기준이다.
     */
    private static YearMonth filterMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            return null;
        }
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return YearMonth.of(year, month);
    }

    /** 달 필터의 시작일. 안 걸었으면 null이다. */
    private static LocalDate monthStart(Integer year, Integer month) {
        YearMonth filter = filterMonth(year, month);
        return filter == null ? null : filter.atDay(1);
    }

    /** 달 필터의 마지막 날. 말일은 달마다 다르므로 YearMonth가 계산한다. */
    private static LocalDate monthEnd(Integer year, Integer month) {
        YearMonth filter = filterMonth(year, month);
        return filter == null ? null : filter.atEndOfMonth();
    }

    /** 이 숙제의 대상이 아니면 애초에 행이 없다. 그때는 404다. */
    private Submission findMySubmission(Long homeworkId) {
        Student me = studentAccessGuard.requireSelf();
        return submissionRepository.findByHomeworkAndStudent(homeworkId, me.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * <b>GRID 숙제는 재제출 대상만 손댈 수 있다.</b> 목록에서 버튼을 안 그리는 것만으로는
     * 부족하다 — URL을 직접 치면 뚫린다. 학생이 거치는 변경 경로가 전부 이 메서드를
     * 지나므로 여기서 한 번만 막으면 된다.
     *
     * <p>제출을 확정하면 result가 DONE이 되어(자동 ⭕) 그 순간부터 이 검사에 걸린다.
     * 즉 <b>제출 후 수정 잠금이 여기 한 줄에 같이 들어 있다.</b> 별도 상태 검사를
     * 덧붙이지 마라.
     */
    private Submission findEditableSubmission(Long homeworkId) {
        Submission submission = findMySubmission(homeworkId);
        if (submission.getHomework().isGrid() && !submission.isResubmitTarget()) {
            throw new BusinessException(ErrorCode.RESUBMIT_NOT_REQUIRED);
        }
        return submission;
    }

    private void requirePhotoRoom(Long submissionId) {
        if (photoRepository.countBySubmissionId(submissionId) >= MAX_PHOTOS) {
            throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
        }
    }

    /** 영상은 최대 1개라 매번 presign한다. 사진처럼 목록으로 묶을 게 없다. */
    private SubmissionVideoResponse videoOf(Submission submission) {
        return submission.hasVideo()
            ? new SubmissionVideoResponse(
                presignedUrlProvider.readUrl(submission.getVideoS3Key()),
                submission.getVideoBytes())
            : null;
    }

    private List<SubmissionPhotoResponse> photosOf(Long submissionId) {
        return photoRepository.findBySubmissionIdOrderBySortOrderAscIdAsc(submissionId).stream()
            .map(photo -> new SubmissionPhotoResponse(photo.getId(),
                presignedUrlProvider.readUrl(photo.getS3Key()), photo.getSortOrder()))
            .toList();
    }

    private Map<Long, List<SubmissionPhoto>> photosBySubmission(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SubmissionPhoto>> photos = new LinkedHashMap<>();
        for (SubmissionPhoto photo : photoRepository.findBySubmissionIds(submissionIds)) {
            photos.computeIfAbsent(photo.getSubmission().getId(), key -> new ArrayList<>())
                .add(photo);
        }
        return photos;
    }

    private Map<Long, Integer> photoCountsOf(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (PhotoCountRow row : photoRepository.countBySubmissionIds(submissionIds)) {
            counts.put(row.getSubmissionId(), (int) row.getPhotoCount());
        }
        return counts;
    }

    /**
     * 미제출 칸을 열면 목록에서 빠져 있다(index &lt; 0). 그때는 처음으로 보낸다 —
     * 뒤로 가기를 누르게 하는 것보다 낫다.
     */
    private Long nextSubmitted(List<Long> submitted, int index) {
        if (index < 0) {
            return submitted.isEmpty() ? null : submitted.get(0);
        }
        return index + 1 < submitted.size() ? submitted.get(index + 1) : null;
    }

    /** 마감이 없는 GRID 열은 남은 시간도 없다. 0을 내리면 "마감 임박"으로 보인다. */
    private Long remainingMinutes(OffsetDateTime now, OffsetDateTime dueAt) {
        if (dueAt == null) {
            return null;
        }
        return Duration.between(now, dueAt).toMinutes();
    }

    /**
     * T-7에서 「미흡」. <b>❌로 되돌리고 사진·영상을 지운다</b>(2026-09-10).
     * 재제출을 잘못 낸 학생을 다시 내게 하는 경로다 — CLAUDE.md 4-5의
     * "선생님이 그리드에서 🔺·❌로 되돌려 줘야 다시 낼 수 있다"에 입구를 하나 더 냈다.
     *
     * <p><b>status는 SUBMITTED로 남긴다.</b> 선생님이 T-7에서 사진을 볼 수 있는 근거가
     * status <> NOT_SUBMITTED 하나뿐이다. 되돌리면 그 학생 칸으로 다시 들어갈 길이
     * 사라진다. 채점축(result)과 제출축(status)을 섞지 마라.
     *
     * <p>resolvedByResubmission은 {@code grade}가 알아서 내린다 — DONE이 아닌 값으로
     * 바꾸면 ck_submissions_resolved 때문에 반드시 내려가야 한다.
     *
     * <p>지운 S3 객체는 되돌릴 수 없다. 화면이 확인 한 단계를 띄운다.
     */
    @Transactional
    public void markNotDone(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!submission.getHomework().isGrid()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        submission.grade(HomeworkResult.NOT_DONE, null);

        for (SubmissionPhoto photo : photoRepository
            .findBySubmissionIdOrderBySortOrderAscIdAsc(submissionId)) {
            photoRepository.delete(photo);
            presignedUrlProvider.deleteQuietly(photo.getS3Key());
        }
        String videoKey = submission.detachVideo();
        if (videoKey != null) {
            presignedUrlProvider.deleteQuietly(videoKey);
        }
    }
}
