package com.njwenglish.repository;

import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.StudentStatus;
import java.time.OffsetDateTime;
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

    /** 퇴원 여부를 가리지 않는다. 학부모 계정을 지워도 되는지 판단할 때 쓴다. */
    long countByParentId(Long parentId);

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

    /** T-1 통계: 재원생 수. */
    long countByStatus(StudentStatus status);

    /**
     * T-1 할 일: 회원가입 안 한 학생. 가입하지 않으면 선생님이 입력한 출석·숙제·성적이
     * 아무에게도 전달되지 않는다. 오픈 초기에 이 숫자를 0으로 만드는 게 실제 운영 업무다.
     */
    long countByStatusAndUserIsNull(StudentStatus status);

    /** T-1 할 일: 학부모가 연결되지 않은 학생. */
    long countByStatusAndParentIsNull(StudentStatus status);

    /**
     * T-1 점검: 최근 7일 신규 가입.
     *
     * <p>반 코드는 전화번호 대조가 없어 코드를 아는 사람 누구나 가입한다. 막을 수단이 없으니
     * <b>가입 후 발견해서 지운다.</b> 이 숫자가 그 탐지 경로의 입구다.
     *
     * <p>status로 걸러내지 않는다. 퇴원 처리된 학생도 "그 기간에 들어온 사람"이라
     * 선생님이 확인해야 하는 대상이다.
     */
    long countByCreatedAtGreaterThanEqual(OffsetDateTime from);

    /**
     * 물리 삭제 차단 조건. 하나라도 있으면 STUDENT_HAS_RECORDS다.
     *
     * <p>submissions는 출제 시 대상 전원의 행이 NOT_SUBMITTED로 미리 깔린다. "행이 있으면"으로
     * 판단하면 아무도 지울 수 없으니 반드시 걸러야 한다.
     *
     * <p><b>거르는 축이 둘이다.</b> status(온라인 제출)만 보면 그리드에서 ⭕를 받은 학생이
     * 빠져나간다 — ⭕는 온라인 제출을 안 하므로 status가 영원히 NOT_SUBMITTED다.
     * 한 학기 채점을 받았는데 그날들 출석이 아직 PENDING이면(확정 전에는 attendances 행이 없다)
     * 다른 조건도 전부 비어서, 성적이 통째로 하드 삭제될 수 있다.
     * SubmissionRepository.countGradedOrSubmitted와 같은 모양이어야 한다.
     *
     * <p>여러 테이블을 한 번의 EXISTS 질의로 끝낸다 — 리포지토리를 그만큼 미리 만드는 것보다
     * 읽기 쉽다. 다만 <b>네이티브 쿼리라 테이블을 드롭해도 컴파일이 통과한다.</b>
     * 실제로 V9에서 없앤 scores와 V14에서 없앤 lesson_views가 한동안 여기 남아 있었다.
     * 마이그레이션으로 테이블을 지울 때 이 목록을 반드시 같이 봐라.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM attendances             WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM submissions             WHERE student_id = :studentId
                                                               AND (status <> 'NOT_SUBMITTED'
                                                                    OR result IS NOT NULL))
            OR EXISTS (SELECT 1 FROM weekly_test_scores      WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM regular_exam_scores     WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM online_test_submissions WHERE student_id = :studentId)
            OR EXISTS (SELECT 1 FROM clinic_reservations     WHERE student_id = :studentId)
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
