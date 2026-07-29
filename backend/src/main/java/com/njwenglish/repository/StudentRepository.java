package com.njwenglish.repository;

import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.StudentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    /** 자녀 목록. 이름은 students.name이다 — user.name으로 정렬하면 미가입 학생이 사라진다. */
    List<Student> findByParentIdAndStatusOrderByNameAsc(Long parentId, StudentStatus status);

    long countByParentIdAndStatus(Long parentId, StudentStatus status);

    /**
     * T-2 목록. 세 필터가 모두 선택이라 null이면 조건을 통과시킨다.
     *
     * <p>status를 문자열로 비교하는 이유는 enum 파라미터에 IS NULL을 걸면 타입 추론이 갈리기 때문이다.
     *
     * <p>keyword는 부분 일치다. 선생님이 번호 뒷자리 4개로 찾는 일이 많다. 번호는 하이픈이
     * 섞여 들어오므로 숫자만 남긴 phoneKeyword로 따로 본다. 미가입 학생은 login_id가 없어서
     * 발급된 코드의 대조 번호까지 함께 봐야 번호로 찾을 수 있다.
     */
    @Query(value = """
        SELECT s FROM Student s
        LEFT JOIN FETCH s.user
        LEFT JOIN FETCH s.parent p
        LEFT JOIN FETCH p.user
        WHERE (:status IS NULL OR CAST(s.status AS string) = :status)
          AND (:classRoomId IS NULL OR EXISTS (
                SELECT 1 FROM Enrollment e
                WHERE e.student = s AND e.classRoom.id = :classRoomId AND e.leftAt IS NULL))
          AND (:keyword IS NULL
                OR LOWER(s.name) LIKE :keyword
                OR (:phoneKeyword IS NOT NULL AND (
                     EXISTS (SELECT 1 FROM User u WHERE u = s.user AND u.loginId LIKE :phoneKeyword)
                  OR EXISTS (SELECT 1 FROM SignupCode c
                             WHERE c.student = s AND c.phone LIKE :phoneKeyword))))
        """,
        countQuery = """
        SELECT COUNT(s) FROM Student s
        WHERE (:status IS NULL OR CAST(s.status AS string) = :status)
          AND (:classRoomId IS NULL OR EXISTS (
                SELECT 1 FROM Enrollment e
                WHERE e.student = s AND e.classRoom.id = :classRoomId AND e.leftAt IS NULL))
          AND (:keyword IS NULL
                OR LOWER(s.name) LIKE :keyword
                OR (:phoneKeyword IS NOT NULL AND (
                     EXISTS (SELECT 1 FROM User u WHERE u = s.user AND u.loginId LIKE :phoneKeyword)
                  OR EXISTS (SELECT 1 FROM SignupCode c
                             WHERE c.student = s AND c.phone LIKE :phoneKeyword))))
        """)
    Page<Student> search(@Param("classRoomId") Long classRoomId,
                         @Param("status") String status,
                         @Param("keyword") String keyword,
                         @Param("phoneKeyword") String phoneKeyword,
                         Pageable pageable);

    /**
     * 물리 삭제 차단 조건. 하나라도 있으면 STUDENT_HAS_RECORDS다.
     *
     * <p>submissions는 출제 시 대상 전원의 행이 NOT_SUBMITTED로 미리 깔린다. "행이 있으면"으로
     * 판단하면 아무도 지울 수 없으니 반드시 status로 걸러라.
     *
     * <p>여섯 테이블은 아직 엔티티만 있고 리포지토리가 없다(Phase 4~6). 한 번의 EXISTS 질의로
     * 끝내는 편이 리포지토리 여섯 개를 미리 만드는 것보다 읽기 쉽다.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM attendances             WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM submissions             WHERE student_id = :studentId
                                                               AND status <> 'NOT_SUBMITTED')
            OR EXISTS (SELECT 1 FROM scores                  WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM online_test_submissions WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM clinic_reservations     WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM lesson_views            WHERE student_id = :studentId)
        """, nativeQuery = true)
    boolean hasOperationalRecords(@Param("studentId") Long studentId);

    /**
     * 삭제 1단계. 위 검사를 통과했으므로 NOT_SUBMITTED 행만 남아 있다.
     *
     * <p>사진은 제출 전에도 올릴 수 있어서(Phase 5) NOT_SUBMITTED 제출물에 붙어 있을 수 있다.
     * 먼저 지우지 않으면 FK 제약에 걸린다.
     */
    @Modifying
    @Query(value = """
        DELETE FROM submission_photos
        WHERE submission_id IN (SELECT id FROM submissions WHERE student_id = :studentId)
        """, nativeQuery = true)
    void deleteSubmissionPhotosOf(@Param("studentId") Long studentId);

    @Modifying
    @Query(value = "DELETE FROM submissions WHERE student_id = :studentId", nativeQuery = true)
    void deleteSubmissionsOf(@Param("studentId") Long studentId);
}
