package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수강 후기. <b>학생당 한 행</b>이고 student_id UNIQUE가 그것을 강제한다.
 *
 * <p><b>rating은 별 개수의 2배다</b>(0.5 → 1, 5.0 → 10). 변환·검증의 정본은
 * {@link com.njwenglish.common.util.Ratings}다 — 서비스나 프론트에 복사하지 마라.
 *
 * <p>답글이 없다. 선생님은 읽기만 한다 — 답글이 달리는 순간 학생이 더 못 쓴다.
 * 사진도 없다.
 *
 * <p>classRoom은 null일 수 있다. 선생님 화면의 반 필터용일 뿐이라,
 * 활성 배정이 없는 학생이 후기를 쓰는 걸 막지 않는다.
 *
 * <p>content는 사용자 입력 텍스트다. 프론트에서 HTML로 렌더링하지 마라.
 */
@Entity
@Table(name = "course_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** null일 수 있다. 선생님 화면의 반 필터용일 뿐이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static CourseReview of(Student student, ClassRoom classRoom, short rating,
                                  String content) {
        CourseReview review = new CourseReview();
        review.student = student;
        review.classRoom = classRoom;
        review.rating = rating;
        review.content = content;
        return review;
    }

    public void edit(short rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}
