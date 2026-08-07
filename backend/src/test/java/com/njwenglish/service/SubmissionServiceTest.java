package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.SubmissionMediaKeys;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.homework.MediaUploadUrlResponse;
import com.njwenglish.dto.homework.ParentHomeworkResponse;
import com.njwenglish.dto.homework.PhotoRegisterRequest;
import com.njwenglish.dto.homework.PhotoUploadUrlRequest;
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
import com.njwenglish.repository.FeedbackRepository;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionPhotoRepository photoRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
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
            feedbackRepository, homeworkService, studentAccessGuard, presignedUrlProvider,
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
    @DisplayName("CHECKED 상태에서는 사진을 수정할 수 없다")
    void CHECKED_상태에서는_사진을_수정할_수_없다() {
        Submission submission = givenMySubmission(FUTURE_DUE);
        submission.submit(OffsetDateTime.now(), false);
        submission.check();

        assertThatThrownBy(() -> submissionService.issueUploadUrl(720L,
            new PhotoUploadUrlRequest("image/webp", 1024)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SUBMISSION_ALREADY_CHECKED);

        assertThatThrownBy(() -> submissionService.deletePhoto(720L, 8812L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SUBMISSION_ALREADY_CHECKED);

        assertThatThrownBy(() -> submissionService.submit(720L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SUBMISSION_ALREADY_CHECKED);
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
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
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
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
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
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
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

    @Test
    @DisplayName("학부모는 자녀의 숙제만 조회할 수 있다")
    void 학부모는_자녀의_숙제만_조회할_수_있다() {
        given(studentAccessGuard.requireAccessible(999L))
            .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));

        assertThatThrownBy(() -> submissionService.childHomeworks(999L, null, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);

        // 권한 검증이 첫 줄이라 조회 자체가 일어나지 않는다
        verify(submissionRepository, never()).findByStudent(anyLong(), any(), any());
    }

    @Test
    @DisplayName("학부모 응답에는 사진·피드백·숙제 내용이 없다")
    void 학부모_응답에는_사진과_피드백이_없다() {
        // 학생 DTO를 재사용하면 그대로 새어 나간다. 필드 목록 자체를 고정한다
        assertThat(Arrays.stream(ParentHomeworkResponse.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactly("homeworkId", "title", "classRoomName", "dueAt",
                "status", "isLate", "checked");
    }

    // ---------- 헬퍼 ----------

    /** 서명이 붙은 진짜 발급 키를 얻는다. 등록 대조를 통과시키려면 이 경로여야 한다. */
    private String issuedVideoKey() {
        return submissionService
            .issueVideoUploadUrl(720L, new VideoUploadUrlRequest("video/mp4", 10_000_000))
            .s3Key();
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
