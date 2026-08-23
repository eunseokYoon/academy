package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.Ratings;
import com.njwenglish.dto.review.CourseReviewListResponse;
import com.njwenglish.dto.review.CourseReviewSaveRequest;
import com.njwenglish.dto.review.MyCourseReviewResponse;
import com.njwenglish.dto.review.TeacherCourseReviewResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.CourseReview;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.CourseReviewRepository;
import com.njwenglish.repository.EnrollmentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수강 후기. 학생당 한 개이고 studentId를 파라미터로 받지 않는다 —
 * requireSelf()로 본인을 찾는다. 학부모 경로는 없다.
 */
@Service
@RequiredArgsConstructor
public class CourseReviewService {

    private final CourseReviewRepository reviewRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * 작성·수정 공통. creating이면 이미 있을 때 409, 아니면 없을 때 404다.
     *
     * <p><b>별점 검증을 빼지 마라.</b> 클라이언트가 보내는 값이다.
     * ck_course_reviews_rating이 DB에서 한 번 더 막지만, 거기서 걸리면 500이 된다.
     */
    @Transactional
    public MyCourseReviewResponse save(CourseReviewSaveRequest request, boolean creating) {
        Student me = studentAccessGuard.requireSelf();
        Optional<CourseReview> existing = reviewRepository.findByStudentId(me.getId());

        if (creating && existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if (!creating && existing.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Ratings.isValid(request.rating())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        short rating = Ratings.toStored(request.rating());

        if (existing.isPresent()) {
            CourseReview review = existing.get();
            review.edit(rating, request.content());
            return MyCourseReviewResponse.from(review);
        }
        return MyCourseReviewResponse.from(reviewRepository.save(
            CourseReview.of(me, firstActiveClassRoom(me.getId()), rating, request.content())));
    }

    /**
     * 작성 시점의 활성 배정 하나. <b>학생에게 반을 고르게 하지 않는다</b> —
     * 대부분 반이 하나고, 후기는 선생님에 대한 평가라 반이 본질이 아니다.
     * 배정이 없으면 null이다. 후기 작성을 막지 마라.
     */
    private ClassRoom firstActiveClassRoom(Long studentId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        return ids.isEmpty() ? null : classRoomRepository.findById(ids.get(0)).orElse(null);
    }

    /** 없으면 null이다. 404가 아니다 — 버튼 상태를 정하는 호출이다. */
    @Transactional(readOnly = true)
    public MyCourseReviewResponse myReview() {
        return reviewRepository.findByStudentId(studentAccessGuard.requireSelf().getId())
            .map(MyCourseReviewResponse::from)
            .orElse(null);
    }

    @Transactional
    public void delete() {
        reviewRepository.delete(
            reviewRepository.findByStudentId(studentAccessGuard.requireSelf().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    /** T-15 후기 탭. 평균은 목록과 같은 반 필터를 따라간다. */
    @Transactional(readOnly = true)
    public CourseReviewListResponse listForTeacher(Long classRoomId, Pageable pageable) {
        Page<CourseReview> page = reviewRepository.findForTeacher(classRoomId, pageable);
        return new CourseReviewListResponse(
            Ratings.averageToDisplay(reviewRepository.averageRating(classRoomId)),
            page.getTotalElements(),
            PageResponse.from(page.map(TeacherCourseReviewResponse::from)));
    }
}
