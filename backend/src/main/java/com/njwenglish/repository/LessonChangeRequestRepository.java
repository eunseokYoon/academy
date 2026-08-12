package com.njwenglish.repository;

import com.njwenglish.entity.LessonChangeRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonChangeRequestRepository extends JpaRepository<LessonChangeRequest, Long> {

    /**
     * 같은 수업에 두 번 요청하는 것을 막는다. DB에도 부분 유니크 인덱스가 있지만
     * 여기서 먼저 걸러야 409 메시지를 제대로 돌려줄 수 있다.
     */
    boolean existsByStudentIdAndFromLessonIdAndStatus(Long studentId, Long fromLessonId,
                                                      ChangeRequestStatus status);

    /**
     * S-9 내 요청 목록. 결과가 확인되기 전까지 학생이 볼 곳이 여기뿐이다.
     * 반 이름과 수업일을 같이 그리므로 두 수업의 반까지 fetch한다.
     */
    @Query("""
        SELECT r FROM LessonChangeRequest r
        JOIN FETCH r.fromLesson fl JOIN FETCH fl.classRoom
        JOIN FETCH r.toLesson tl JOIN FETCH tl.classRoom
        WHERE r.student.id = :studentId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    List<LessonChangeRequest> findAllByStudent(@Param("studentId") Long studentId);

    /**
     * T-13 대기 목록. 페이징 없음 — 강사 1명이 처리하는 큐라 쌓여도 몇 건이다.
     *
     * <p>student를 fetch하는 것은 이름을 그리기 위해서다. <b>student.user가 아니라
     * students.name이다</b> — 미가입 학생은 users 행이 없어서 경로를 타면 명단에서 사라진다.
     */
    @Query("""
        SELECT r FROM LessonChangeRequest r
        JOIN FETCH r.student
        JOIN FETCH r.fromLesson fl JOIN FETCH fl.classRoom
        JOIN FETCH r.toLesson tl JOIN FETCH tl.classRoom
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt ASC, r.id ASC
        """)
    List<LessonChangeRequest> findAllWithDetail(@Param("status") ChangeRequestStatus status);

    @Query("""
        SELECT r FROM LessonChangeRequest r
        JOIN FETCH r.student
        JOIN FETCH r.fromLesson fl JOIN FETCH fl.classRoom
        JOIN FETCH r.toLesson tl JOIN FETCH tl.classRoom
        WHERE r.id = :requestId
        """)
    Optional<LessonChangeRequest> findWithDetail(@Param("requestId") Long requestId);
}
