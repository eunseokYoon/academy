package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.SubmissionMediaKeys;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.homework.HomeworkCountsResponse;
import com.njwenglish.dto.homework.HomeworkSubmissionsResponse;
import com.njwenglish.dto.homework.MediaUploadUrlResponse;
import com.njwenglish.dto.homework.ParentHomeworkResponse;
import com.njwenglish.dto.homework.PhotoRegisterRequest;
import com.njwenglish.dto.homework.PhotoUploadUrlRequest;
import com.njwenglish.dto.homework.SubmissionListItemResponse;
import com.njwenglish.dto.homework.SubmitResponse;
import com.njwenglish.dto.homework.VideoRegisterRequest;
import com.njwenglish.dto.homework.VideoUploadUrlRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionPhotoRepository photoRepository;
    @Mock
    private HomeworkService homeworkService;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;

    private SubmissionService submissionService;

    /** 이미 지난 마감. 지각 제출 판정을 실제 시각에 기대지 않기 위해 과거로 둔다. */
    private static final OffsetDateTime PAST_DUE =
        OffsetDateTime.of(2026, 5, 21, 20, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime FUTURE_DUE =
        OffsetDateTime.now().plusDays(3);

    private final Student seo = Fixtures.student(88L, "서동환");
    private ClassRoom classRoom;

    @BeforeEach
    void setUp() {
        classRoom = ClassRoom.create(null, "고2 심화반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);

        submissionService = new SubmissionService(submissionRepository, photoRepository,
            homeworkService, studentAccessGuard, presignedUrlProvider,
            new SubmissionMediaKeys("test-secret-value-for-hmac-signing-0123456789"));
        Fixtures.login(Fixtures.studentUser(10L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("마감 후 제출은 허용되고 is_late가 true다")
    void 마감_후_제출은_허용되고_is_late가_true다() {
        Submission submission = givenMySubmission(PAST_DUE);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(2L);

        SubmitResponse response = submissionService.submit(720L);

        // 차단(DUE_DATE_PASSED)이 아니다. 늦게라도 내는 학생이 대부분이라 허용이 확정 정책이다
        assertThat(response.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.isLate()).isTrue();
        assertThat(submission.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("마감 전 제출은 is_late가 false다")
    void 마감_전_제출은_is_late가_false다() {
        givenMySubmission(FUTURE_DUE);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(1L);

        assertThat(submissionService.submit(720L).isLate()).isFalse();
    }

    @Test
    @DisplayName("사진도 영상도 없이 제출하면 400이다")
    void 첨부_없이_제출하면_거부된다() {
        givenMySubmission(FUTURE_DUE);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(0L);

        assertThatThrownBy(() -> submissionService.submit(720L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("사진이 0장이어도 영상이 있으면 제출된다")
    void 영상만_있어도_제출된다() {
        Submission submission = givenMySubmission(FUTURE_DUE);
        submission.attachVideo("submissions/2026/05/x-1234567890abcdef.mp4", 50_000_000);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(0L);

        SubmitResponse response = submissionService.submit(720L);

        assertThat(response.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(response.photoCount()).isZero();
    }

    @Test
    @DisplayName("사진 11장 업로드는 거부된다")
    void 사진_11장_업로드는_거부된다() {
        givenMySubmission(FUTURE_DUE);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(10L);

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 284012)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PHOTO_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("허용되지 않은 contentType은 400이다")
    void 허용되지_않은_contentType은_거부된다() {
        givenMySubmission(FUTURE_DUE);

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("application/pdf", 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("10MB를 넘으면 413이다")
    void 용량_초과는_거부된다() {
        givenMySubmission(FUTURE_DUE);

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 11 * 1024 * 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("서버가 발급하지 않은 s3Key는 거부된다")
    void 임의의_s3Key는_거부된다() {
        givenMySubmission(FUTURE_DUE);

        assertThatThrownBy(() -> submissionService.registerPhoto(720L,
            new PhotoRegisterRequest("submissions/2026/05/aaaa.webp", (short) 1, 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(photoRepository, never()).save(any());
    }

    @Test
    @DisplayName("영상이 100MB를 넘으면 413이다")
    void 영상_용량_초과는_거부된다() {
        givenMySubmission(FUTURE_DUE);

        // 브라우저에서 압축할 방법이 없어 원본이 그대로 올라온다. 여기서 막지 못하면
        // 4K로 길게 찍은 단일 파일이 그대로 들어온다
        assertThatThrownBy(() -> submissionService.issueVideoUploadUrl(720L,
            new VideoUploadUrlRequest("video/mp4", 101 * 1024 * 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("영상 형식이 아니면 400이다")
    void 영상이_아닌_형식은_거부된다() {
        givenMySubmission(FUTURE_DUE);

        assertThatThrownBy(() -> submissionService.issueVideoUploadUrl(720L,
            new VideoUploadUrlRequest("image/webp", 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("영상을 다시 올리면 이전 것을 덮고 S3에서 지운다")
    void 영상은_1개이고_다시_올리면_덮어쓴다() {
        Submission submission = givenMySubmission(FUTURE_DUE);
        String first = issuedVideoKey();
        submissionService.registerVideo(720L, new VideoRegisterRequest(first, 10_000_000));

        String second = issuedVideoKey();
        submissionService.registerVideo(720L, new VideoRegisterRequest(second, 20_000_000));

        // 개수 초과로 막지 않는다. 다시 찍어 올리는 건 정상 흐름이고 이전 파일은 남기지 않는다
        assertThat(submission.getVideoS3Key()).isEqualTo(second);
        assertThat(submission.getVideoBytes()).isEqualTo(20_000_000);
        verify(presignedUrlProvider).deleteQuietly(first);
    }

    @Test
    @DisplayName("서버가 발급하지 않은 영상 s3Key는 거부된다")
    void 임의의_영상_s3Key는_거부된다() {
        Submission submission = givenMySubmission(FUTURE_DUE);

        assertThatThrownBy(() -> submissionService.registerVideo(720L,
            new VideoRegisterRequest("submissions/2026/05/hack.mp4", 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThat(submission.hasVideo()).isFalse();
    }

    @Test
    @DisplayName("GRID 재제출을 내면 자동으로 ⭕가 되고 재제출 표시가 붙는다")
    void gridResubmissionResolvesItselfOnSubmit() {
        Submission cell = givenResubmitTarget(HomeworkResult.NOT_DONE, null);
        given(photoRepository.countBySubmissionId(1L)).willReturn(2L);

        submissionService.submit(720L);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(cell.getCompletionRate()).isNull();
        assertThat(cell.isResolvedByResubmission()).isTrue();
        // 채점축만 올라간다. 제출축은 SUBMITTED 그대로여야 선생님이 T-7에서 사진을 볼 수 있다
        assertThat(cell.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    @DisplayName("세모(PARTIAL)로 남은 퍼센트도 재제출하면 지워진다")
    void gridResubmissionClearsCompletionRate() {
        Submission cell = givenResubmitTarget(HomeworkResult.PARTIAL, (short) 60);
        given(photoRepository.countBySubmissionId(1L)).willReturn(1L);

        submissionService.submit(720L);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(cell.getCompletionRate()).isNull();
    }

    @Test
    @DisplayName("마감이 지나 낸 재제출도 ⭕가 되고 지각으로 남는다")
    void lateGridResubmissionStillResolves() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(PAST_DUE, "복습", null);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(HomeworkResult.NOT_DONE, null);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));
        given(photoRepository.countBySubmissionId(1L)).willReturn(1L);

        submissionService.submit(720L);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(cell.isLate()).isTrue();
    }

    @Test
    @DisplayName("한 번 낸 GRID 재제출은 학생이 다시 손댈 수 없다")
    void gridResubmissionLocksAfterSubmit() {
        // 자동 ⭕가 붙으면 재제출 대상에서 빠지고, findEditableSubmission이 그것만으로 막는다.
        // 목록에서 버튼을 안 그리는 것과 별개로 URL을 직접 쳐도 뚫리지 않아야 한다
        givenResubmitTarget(HomeworkResult.NOT_DONE, null);
        given(photoRepository.countBySubmissionId(1L)).willReturn(1L);

        submissionService.submit(720L);

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);

        assertThatThrownBy(() -> submissionService.deletePhoto(720L, 8812L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);

        assertThatThrownBy(() -> submissionService.deleteVideo(720L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);

        assertThatThrownBy(() -> submissionService.submit(720L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);
    }

    @Test
    @DisplayName("ONLINE 숙제의 제출은 채점 결과를 건드리지 않는다")
    void onlineSubmitLeavesResultUntouched() {
        Submission submission = givenMySubmission(FUTURE_DUE);
        given(photoRepository.countBySubmissionId(4412L)).willReturn(1L);

        submissionService.submit(720L);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getResult()).isNull();
        assertThat(submission.isResolvedByResubmission()).isFalse();
    }

    @Test
    @DisplayName("재제출 대상이 아닌 GRID 숙제의 업로드 URL 발급은 409다")
    void uploadUrlBlockedWhenNotResubmitTarget() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(HomeworkResult.DONE, null);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 284012)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);
    }

    @Test
    @DisplayName("재제출을 연 열이어도 본인이 DONE이면 업로드 URL 발급은 409다")
    void uploadUrlBlockedWhenResubmitOpenButOwnResultIsDone() {
        // 같은 열에서 다른 학생은 재제출 대상일 수 있다(NOT_DONE/PARTIAL). 열이 열려 있다는
        // 사실만으로 통과시키면, 그 열의 DONE인 학생까지 URL을 직접 쳐서 뚫을 수 있다.
        // 판정은 항상 이 학생 본인의 result여야 한다.
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)), "복습", null);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(HomeworkResult.DONE, null);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 284012)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESUBMIT_NOT_REQUIRED);
    }

    @Test
    @DisplayName("재제출을 연 열에서 X를 받은 학생은 제출할 수 있다")
    void uploadUrlAllowedForResubmitTarget() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)), "복습", null);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(HomeworkResult.NOT_DONE, null);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));
        given(photoRepository.countBySubmissionId(1L)).willReturn(0L);

        MediaUploadUrlResponse response = submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 284012));

        assertThat(response.s3Key()).isNotBlank();
    }

    @Test
    @DisplayName("재제출을 연 열에서 세모(PARTIAL)를 받은 학생도 제출할 수 있다")
    void uploadUrlAllowedForResubmitTargetWithPartialResult() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)), "복습", null);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(HomeworkResult.PARTIAL, (short) 60);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));
        given(photoRepository.countBySubmissionId(1L)).willReturn(0L);

        MediaUploadUrlResponse response = submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 284012));

        assertThat(response.s3Key()).isNotBlank();
    }

    @Test
    @DisplayName("ONLINE 숙제는 재제출 판정과 무관하게 제출할 수 있다")
    void uploadUrlAllowedForOnlineHomework() {
        Homework online = Fixtures.homework(700L, classRoom, FUTURE_DUE);
        Submission submission = Fixtures.submission(2L, online, seo);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(700L, 88L))
            .willReturn(Optional.of(submission));
        given(photoRepository.countBySubmissionId(2L)).willReturn(0L);

        MediaUploadUrlResponse response = submissionService.issueUploadUrl(700L,
            new PhotoUploadUrlRequest("image/webp", 284012));

        assertThat(response.s3Key()).isNotBlank();
    }

    // ---------- 선생님 (T-7) ----------

    /*
     * 리포지토리 필터링 자체(findByHomeworkForTeacher의 JPQL)는 이 스위트에서 검증하지
     * 않는다 — 이 프로젝트에는 @DataJpaTest 같은 JPQL 실행 인프라가 없다. 대신 리포지토리가
     * "이 명단을 넘겨줬다"고 가정했을 때 서비스가 그 명단을 손대지 않고 그대로 응답에
     * 옮기는지를 검증한다. 리포지토리를 스텁하고 스텁 내용을 그대로 되묻는 테스트가
     * 아니라, submissionsOf가 실제로 책임지는 매핑·집계 로직을 검증하는 것이다.
     */

    @Test
    @DisplayName("ONLINE 숙제는 리포지토리가 돌려준 명단 전원을 그대로 응답에 담는다")
    void submissionsOfKeepsFullRosterForOnlineHomework() {
        Homework online = Fixtures.homework(700L, classRoom, FUTURE_DUE);
        Submission notSubmitted = Fixtures.submission(11L, online, Fixtures.student(1L, "가나다"));
        Submission submitted = Fixtures.submission(12L, online, Fixtures.student(2L, "나다라"));
        submitted.submit(OffsetDateTime.now(), false);
        Submission lateSubmitted = Fixtures.submission(13L, online, Fixtures.student(3L, "다라마"));
        lateSubmitted.submit(OffsetDateTime.now(), true);

        given(homeworkService.findHomework(700L)).willReturn(online);
        given(submissionRepository.findByHomeworkForTeacher(700L))
            .willReturn(List.of(notSubmitted, submitted, lateSubmitted));
        given(homeworkService.countsOf(List.of(700L)))
            .willReturn(Map.of(700L, new HomeworkCountsResponse(3, 1, 2)));

        HomeworkSubmissionsResponse response = submissionService.submissionsOf(700L);

        // 리포지토리가 넘긴 3명 전부가 응답에 남는다 — 서비스가 자체적으로 다시 거르지 않는다
        assertThat(response.items())
            .extracting(SubmissionListItemResponse::studentId)
            .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(response.counts().total()).isEqualTo(3);
    }

    @Test
    @DisplayName("재제출로 자동 해결된 GRID 제출물도 명단에 남는다")
    void submissionsOfKeepsResubmissionResolvedGridSubmission() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)), "복습", null);
        Submission resolved = Fixtures.submission(9L, column, Fixtures.student(88L, "고연준"));
        resolved.grade(HomeworkResult.NOT_DONE, null);
        resolved.submit(OffsetDateTime.now(), false);
        resolved.resolveByResubmission();

        given(homeworkService.findHomework(720L)).willReturn(column);
        given(submissionRepository.findByHomeworkForTeacher(720L)).willReturn(List.of(resolved));
        given(homeworkService.countsOf(List.of(720L)))
            .willReturn(Map.of(720L, new HomeworkCountsResponse(1, 0, 1)));

        HomeworkSubmissionsResponse response = submissionService.submissionsOf(720L);

        // s.status <> NOT_SUBMITTED 절이 지키는 대상이다. 없었다면 이 학생은 목록에서 빠진다 —
        // ⭕가 붙는 순간 재제출 대상 절에서도 빠지므로 사진을 볼 길이 사라진다
        assertThat(response.items()).hasSize(1);
        SubmissionListItemResponse item = response.items().get(0);
        assertThat(item.studentId()).isEqualTo(88L);
        assertThat(item.status()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    // ---------- 학부모 (P-3) ----------

    @Test
    @DisplayName("학부모는 자녀의 숙제만 조회할 수 있다")
    void 학부모는_자녀의_숙제만_조회할_수_있다() {
        given(studentAccessGuard.requireAccessible(999L))
            .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));

        assertThatThrownBy(() -> submissionService.childHomeworks(999L, null, null, null, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);

        // 권한 검증이 첫 줄이라 조회 자체가 일어나지 않는다
        verify(submissionRepository, never()).findByStudent(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("학부모 응답에는 사진·피드백·숙제 내용이 없다")
    void 학부모_응답에는_사진과_피드백이_없다() {
        // 학생 DTO를 재사용하면 그대로 새어 나간다. 필드 목록 자체를 고정한다
        assertThat(Arrays.stream(ParentHomeworkResponse.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactly("homeworkId", "title", "classRoomName", "kind", "lessonDate",
                "result", "completionRate", "resolvedByResubmission", "dueAt",
                "status", "isLate");
    }

    @Test
    @DisplayName("학부모 응답에 채점 결과가 담기고 사진·피드백은 없다")
    void childHomeworksExposesGradeOnly() {
        ClassRoom classRoom = ClassRoom.create(Fixtures.teacherEntity(1L),
            "동성고1 수요일반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission cell = Fixtures.submission(1L, column, Fixtures.student(88L, "고연준"));
        cell.grade(HomeworkResult.PARTIAL, (short) 50);

        given(studentAccessGuard.requireAccessible(88L))
            .willReturn(Fixtures.student(88L, "고연준"));
        given(submissionRepository.findByStudent(eq(88L), any(), any(), any(), any()))
            .willReturn(new PageImpl<>(List.of(cell)));

        PageResponse<ParentHomeworkResponse> response =
            submissionService.childHomeworks(88L, null, null, null, PageRequest.of(0, 20));

        ParentHomeworkResponse item = response.items().get(0);
        assertThat(item.title()).isEqualTo("독해 5-8");
        assertThat(item.result()).isEqualTo(HomeworkResult.PARTIAL);
        assertThat(item.completionRate()).isEqualTo((short) 50);
        assertThat(item.lessonDate()).isEqualTo(LocalDate.of(2026, 7, 29));
        // 학부모 DTO에는 사진·피드백·숙제 내용 필드가 아예 없다
        assertThat(ParentHomeworkResponse.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .doesNotContain("photos", "photoCount", "thumbnailUrl", "feedback", "description");
    }


    // ---------- 달 필터 (S-2 · P-3) ----------

    @Test
    @DisplayName("연·월을 주면 그 달 1일~말일의 수업일 범위로 조회한다")
    void 연월을_주면_그_달_범위로_조회한다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByStudent(eq(88L), any(), any(), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        submissionService.myHomeworks(null, 2026, 2, PageRequest.of(0, 20));

        // 말일은 달마다 다르다. 28을 박아 두면 31일 수업의 숙제가 사라진다
        verify(submissionRepository).findByStudent(eq(88L), eq(null),
            eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)), any());
    }

    @Test
    @DisplayName("연·월이 없으면 수업일 범위를 걸지 않는다")
    void 연월이_없으면_범위를_걸지_않는다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByStudent(eq(88L), any(), any(), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        submissionService.myHomeworks(null, null, null, PageRequest.of(0, 20));

        // null이면 지금까지와 똑같이 전부 내려온다 — 수업이 없는 ONLINE 숙제도 포함이다
        verify(submissionRepository).findByStudent(eq(88L), eq(null), eq(null), eq(null), any());
    }

    @Test
    @DisplayName("연·월 중 하나만 오면 범위를 걸지 않는다")
    void 연월_중_하나만_오면_범위를_걸지_않는다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByStudent(eq(88L), any(), any(), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        // 화면이 연도만 바꾸는 순간이 있다. 여기서 달을 1월로 가정해 버리면
        // 학생이 고르지 않은 달의 숙제만 보인다
        submissionService.myHomeworks(null, 2026, null, PageRequest.of(0, 20));

        verify(submissionRepository).findByStudent(eq(88L), eq(null), eq(null), eq(null), any());
    }

    @Test
    @DisplayName("범위 밖 월은 400이다")
    void 범위_밖_월은_400이다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);

        // YearMonth.of가 DateTimeException을 던지면 500이 된다. 잘못 만든 URL은 400이어야 한다
        assertThatThrownBy(() -> submissionService.myHomeworks(null, 2026, 99, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(submissionRepository, never()).findByStudent(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("학부모 조회도 같은 달 범위를 쓴다")
    void 학부모_조회도_같은_달_범위를_쓴다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(submissionRepository.findByStudent(eq(88L), any(), any(), any(), any()))
            .willReturn(new PageImpl<>(List.of()));

        submissionService.childHomeworks(88L, null, 2026, 8, PageRequest.of(0, 20));

        verify(submissionRepository).findByStudent(eq(88L), eq(null),
            eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)), any());
    }

    // ---------- 헬퍼 ----------

    /** 서명이 붙은 진짜 발급 키를 얻는다. 등록 대조를 통과시키려면 이 경로여야 한다. */
    private String issuedVideoKey() {
        return submissionService
            .issueVideoUploadUrl(720L, new VideoUploadUrlRequest("video/mp4", 10_000_000))
            .s3Key();
    }

    /** 재제출이 열린 GRID 열에서 🔺·❌를 받은 칸. 자동 ⭕ 관련 테스트의 공통 출발점이다. */
    private Submission givenResubmitTarget(HomeworkResult result, Short completionRate) {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(FUTURE_DUE, "복습", null);
        Submission cell = Fixtures.submission(1L, column, seo);
        cell.grade(result, completionRate);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(cell));
        return cell;
    }

    private Submission givenMySubmission(OffsetDateTime dueAt) {
        Homework homework = Fixtures.homework(720L, classRoom, dueAt);
        Submission submission = Fixtures.submission(4412L, homework, seo);
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(submissionRepository.findByHomeworkAndStudent(720L, 88L))
            .willReturn(Optional.of(submission));
        return submission;
    }
}
