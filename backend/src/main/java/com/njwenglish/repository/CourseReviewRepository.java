package com.njwenglish.repository;

import com.njwenglish.entity.CourseReview;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    Optional<CourseReview> findByStudentId(Long studentId);

    /**
     * T-15 후기 탭. classRoomId가 null이면 전체다.
     * 작성자 이름을 쓰므로 student를 함께 가져온다 — 20행마다 쿼리를 날리지 않는다.
     */
    @EntityGraph(attributePaths = {"student", "classRoom"})
    @Query("""
        SELECT r FROM CourseReview r
        WHERE (:classRoomId IS NULL OR r.classRoom.id = :classRoomId)
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    Page<CourseReview> findForTeacher(@Param("classRoomId") Long classRoomId,
                                      Pageable pageable);

    /**
     * 저장값(1~10)의 평균이다. <b>화면 값으로 바꾸는 건 Ratings가 한다.</b>
     * 후기가 없으면 null이 온다 — 0.0으로 접지 마라. 화면이 "별점 0점"으로 읽는다.
     */
    @Query("""
        SELECT AVG(r.rating) FROM CourseReview r
        WHERE (:classRoomId IS NULL OR r.classRoom.id = :classRoomId)
        """)
    Double averageRating(@Param("classRoomId") Long classRoomId);
}
