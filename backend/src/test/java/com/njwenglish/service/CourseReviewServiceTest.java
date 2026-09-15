package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.review.CourseReviewSaveRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.CourseReview;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.CourseReviewRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 수강 후기. <b>학생당 한 개</b>이고 학부모 경로가 없다.
 * studentId를 파라미터로 받지 않는다 — requireSelf()로 본인을 찾는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseReviewServiceTest {

    @Mock
    private CourseReviewRepository reviewRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Student me = Fixtures.student(88L, "서동환");

    private CourseReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new CourseReviewService(reviewRepository, classRoomRepository,
            enrollmentRepository, studentAccessGuard);
        Fixtures.login(Fixtures.studentUser(7L));
        given(studentAccessGuard.requireSelf()).willReturn(me);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CourseReview review(Long id, short rating) {
        CourseReview review = CourseReview.of(me, classRoom, rating, "설명이 이해하기 쉬워요");
        ReflectionTestUtils.setField(review, "id", id);
        return review;
    }

    // ---------- 작성 ----------

    @Test
    @DisplayName("이미 후기가 있으면 409다")
    void 두_번째_후기는_409다() {
        // student_id UNIQUE가 DB에서도 막지만, 409로 돌려줘야 화면이 안내할 수 있다
        given(reviewRepository.findByStudentId(88L))
            .willReturn(Optional.of(review(1L, (short) 9)));

        assertThatThrownBy(() -> reviewService.save(
            new CourseReviewSaveRequest(4.5, "좋아요"), true))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("0.5 단위가 아닌 별점은 400이다")
    void 반개_단위가_아닌_별점은_400이다() {
        // 클라이언트가 보내는 값이라 서버가 반드시 검사한다
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.save(
            new CourseReviewSaveRequest(4.3, "좋아요"), true))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("범위를 벗어난 별점은 400이다")
    void 범위_밖_별점은_400이다() {
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.save(
            new CourseReviewSaveRequest(5.5, "좋아요"), true))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("4.5는 9로 저장된다")
    void 별점은_2배로_저장된다() {
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(reviewRepository.save(any(CourseReview.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        var response = reviewService.save(
            new CourseReviewSaveRequest(4.5, "설명이 이해하기 쉬워요"), true);

        assertThat(response.rating()).isEqualTo(4.5);

        // 표시값 왕복(4.5 → 저장 → 4.5)만 보면 저장값이 뒤바뀌어도 대칭이면 통과한다.
        // 실제로 DB에 들어가는 short 값을 직접 잡아 확인한다
        ArgumentCaptor<CourseReview> captor = ArgumentCaptor.forClass(CourseReview.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo((short) 9);
    }

    @Test
    @DisplayName("활성 배정이 없는 학생도 후기를 쓸 수 있다")
    void 배정이_없어도_후기를_쓸_수_있다() {
        // class_room_id는 선생님 화면의 필터용일 뿐이다.
        // 여기서 막으면 가입 직후 학생이 500을 본다
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of());
        given(reviewRepository.save(any(CourseReview.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        var response = reviewService.save(new CourseReviewSaveRequest(3.0, "괜찮아요"), true);

        assertThat(response.rating()).isEqualTo(3.0);
        verify(classRoomRepository, never()).findById(any());
    }

    // ---------- 수정·삭제 ----------

    @Test
    @DisplayName("수정은 내 후기가 없으면 404다")
    void 없는_후기를_수정하면_404다() {
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.save(
            new CourseReviewSaveRequest(4.0, "수정"), false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("수정은 내 후기만 고친다")
    void 수정은_내_후기만_고친다() {
        CourseReview existing = review(1L, (short) 9);
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.of(existing));

        var response = reviewService.save(new CourseReviewSaveRequest(3.5, "다시 씀"), false);

        assertThat(response.rating()).isEqualTo(3.5);
        assertThat(response.content()).isEqualTo("다시 씀");
        // 새 행을 만들지 않는다. 학생당 1개다
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 후기가 없으면 조회는 null이다")
    void 내_후기가_없으면_null이다() {
        // 404가 아니다. 버튼 상태를 정하는 호출이라 없는 것이 정상 응답이다
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());

        assertThat(reviewService.myReview()).isNull();
    }

    @Test
    @DisplayName("내 후기가 있으면 표시값으로 돌려준다")
    void 내_후기가_있으면_표시값으로_돌려준다() {
        given(reviewRepository.findByStudentId(88L))
            .willReturn(Optional.of(review(1L, (short) 9)));

        var response = reviewService.myReview();

        assertThat(response.rating()).isEqualTo(4.5);
        assertThat(response.content()).isEqualTo("설명이 이해하기 쉬워요");
    }

    @Test
    @DisplayName("삭제는 내 후기를 지운다")
    void 삭제는_내_후기를_지운다() {
        CourseReview existing = review(1L, (short) 9);
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.of(existing));

        reviewService.delete();

        verify(reviewRepository).delete(existing);
    }

    @Test
    @DisplayName("삭제할 후기가 없으면 404다")
    void 없는_후기를_삭제하면_404다() {
        given(reviewRepository.findByStudentId(88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.delete())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(reviewRepository, never()).delete(any());
    }

    // ---------- 선생님 목록 ----------

    @Test
    @DisplayName("평균이 반 필터를 따라간다")
    void 평균이_반_필터를_따라간다() {
        given(reviewRepository.findForTeacher(eq(3L), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(review(1L, (short) 9))));
        given(reviewRepository.averageRating(3L)).willReturn(9.0);

        var response = reviewService.listForTeacher(3L, PageRequest.of(0, 20));

        assertThat(response.averageRating()).isEqualTo(4.5);
        verify(reviewRepository).averageRating(3L);
    }

    @Test
    @DisplayName("후기가 없으면 평균은 null이다")
    void 후기가_없으면_평균은_null이다() {
        given(reviewRepository.findForTeacher(any(), any(Pageable.class)))
            .willReturn(Page.empty());
        given(reviewRepository.averageRating(null)).willReturn(null);

        var response = reviewService.listForTeacher(null, PageRequest.of(0, 20));

        assertThat(response.averageRating()).isNull();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    @DisplayName("작성자 이름은 students.name이다")
    void 작성자_이름은_students_name이다() {
        // users.name이 아니다. 미가입 학생은 users 행이 없다
        given(reviewRepository.findForTeacher(any(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(review(1L, (short) 9))));
        given(reviewRepository.averageRating(null)).willReturn(9.0);

        var response = reviewService.listForTeacher(null, PageRequest.of(0, 20));

        assertThat(response.reviews().items().get(0).studentName()).isEqualTo("서동환");
    }
}
