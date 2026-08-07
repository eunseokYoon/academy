package com.njwenglish.repository;

import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        WHERE h.id = :homeworkId AND s.student.id = :studentId
        """)
    Optional<Submission> findByHomeworkAndStudent(@Param("homeworkId") Long homeworkId,
                                                  @Param("studentId") Long studentId);

    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.student
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        WHERE s.id = :submissionId
        """)
    Optional<Submission> findWithStudentAndHomework(@Param("submissionId") Long submissionId);

    /**
     * T-7 명단. 페이징 없이 반 전체(30명 내외)를 한 번에 준다.
     *
     * <p>정렬은 처리할 것이 위로 온다: 확인대기 → 미제출 → 완료.
     * 이름은 students.name이다. user.name으로 쓰면 미가입 학생이 통째로 사라진다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.student st
        WHERE s.homework.id = :homeworkId
        ORDER BY CASE s.status WHEN 'SUBMITTED' THEN 0 WHEN 'NOT_SUBMITTED' THEN 1 ELSE 2 END,
                 st.name
        """)
    List<Submission> findByHomeworkForTeacher(@Param("homeworkId") Long homeworkId);

    /**
     * S-2 · P-3 목록. 미제출이면서 마감 임박한 것이 위로 온다.
     *
     * <p>두 번째 키가 CASE인 이유는 그룹별로 방향이 반대이기 때문이다.
     * 미제출은 마감이 가까운 순(ASC), 나머지는 최근 것부터(DESC)다.
     * 미제출이 아닌 행은 두 번째 키가 NULL이 되어 세 번째 키로 넘어간다.
     */
    @Query(value = """
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        WHERE s.student.id = :studentId
          AND (:status IS NULL OR CAST(s.status AS string) = :status)
        ORDER BY CASE WHEN s.status = 'NOT_SUBMITTED' THEN 0 ELSE 1 END,
                 CASE WHEN s.status = 'NOT_SUBMITTED' THEN h.dueAt END ASC,
                 h.dueAt DESC
        """,
        countQuery = """
        SELECT COUNT(s) FROM Submission s
        WHERE s.student.id = :studentId
          AND (:status IS NULL OR CAST(s.status AS string) = :status)
        """)
    Page<Submission> findByStudent(@Param("studentId") Long studentId,
                                   @Param("status") String status,
                                   Pageable pageable);

    /**
     * 숙제 삭제 차단 판정. "행이 있으면 409"로 짜면 어떤 숙제도 못 지운다 —
     * 출제 시 대상 전원의 행이 NOT_SUBMITTED로 미리 깔리기 때문이다.
     */
    long countByHomeworkIdAndStatusNot(Long homeworkId, SubmissionStatus status);

    /**
     * T-6 목록과 T-7 상단의 집계. 목록 한 페이지의 숙제 id를 한 번에 넘겨 1쿼리로 끝낸다.
     * 숙제마다 세면 20행에 20쿼리다.
     */
    @Query("""
        SELECT s.homework.id AS homeworkId,
               COUNT(s) AS total,
               SUM(CASE WHEN s.status = 'NOT_SUBMITTED' THEN 1 ELSE 0 END) AS notSubmitted,
               SUM(CASE WHEN s.status = 'SUBMITTED' THEN 1 ELSE 0 END) AS submitted,
               SUM(CASE WHEN s.status = 'CHECKED' THEN 1 ELSE 0 END) AS checked
        FROM Submission s
        WHERE s.homework.id IN :homeworkIds
        GROUP BY s.homework.id
        """)
    List<CountRow> countsByHomeworkIds(@Param("homeworkIds") Collection<Long> homeworkIds);

    interface CountRow {
        Long getHomeworkId();

        long getTotal();

        long getNotSubmitted();

        long getSubmitted();

        long getChecked();
    }

    /** 전원 미제출이 확인된 뒤에만 호출된다. homework_id에 CASCADE가 없어 먼저 지운다. */
    @Modifying
    @Query("DELETE FROM Submission s WHERE s.homework.id = :homeworkId")
    void deleteByHomeworkId(@Param("homeworkId") Long homeworkId);

    /**
     * T-7의 이전·다음. 목록과 같은 정렬 위에서 앞뒤를 찾아야 순서가 어긋나지 않는다.
     * next는 아직 확인하지 않은(SUBMITTED) 것 중 다음이다 — 확인 완료를 다시 열 이유가 없다.
     */
    @Query("""
        SELECT s.id FROM Submission s
        WHERE s.homework.id = :homeworkId AND s.status = 'SUBMITTED'
        ORDER BY s.student.name
        """)
    List<Long> findAwaitingCheckIds(@Param("homeworkId") Long homeworkId);

    /**
     * 캘린더 색띠(Phase 4)의 원본. lesson에 연결된 숙제만 센다 —
     * h.lesson.id 경로가 내부 조인이라 lesson_id가 null인 숙제는 자동으로 빠진다.
     *
     * <p>행이 없는 수업일은 "그날 숙제가 없었다"이지 0%가 아니다.
     * 서비스에서 0으로 바꾸지 마라. 캘린더에 빨간 띠가 뜬다.
     */
    @Query("""
        SELECT h.lesson.id AS lessonId,
               SUM(CASE WHEN s.status IN ('SUBMITTED','CHECKED') THEN 1 ELSE 0 END) AS doneCount,
               COUNT(s) AS totalCount
        FROM Submission s JOIN s.homework h
        WHERE s.student.id = :studentId AND h.lesson.id IN :lessonIds
        GROUP BY h.lesson.id
        """)
    List<HomeworkRateRow> findHomeworkRates(@Param("studentId") Long studentId,
                                            @Param("lessonIds") Collection<Long> lessonIds);

    interface HomeworkRateRow {
        Long getLessonId();

        long getDoneCount();

        long getTotalCount();
    }

    /** T-1 할 일: 확인 대기 제출물. SUBMITTED는 학생이 냈지만 선생님이 아직 안 본 것이다. */
    long countByStatus(SubmissionStatus status);

    /** P-3 홈의 미제출 숙제 수. 마감이 지난 것도 포함한다 — 여전히 안 낸 것이다. */
    long countByStudentIdAndStatus(Long studentId, SubmissionStatus status);

    /**
     * S-1 홈의 "지금 할 숙제". 미제출 <b>전부</b>다.
     *
     * <p>기간 하한이 없다. 홈에서 사라지면 학생이 잊고, 잊으면 아무도 다시 알려주지 않는다
     * (미제출 독려 발송 수단이 범위 밖이다). "최근 N일" 같은 기준을 넣으면 그 밖의 미제출이
     * 조용히 없는 일이 된다.
     *
     * <p>파라미터로 열어 두지 않은 이유가 하나 더 있다. {@code (:from IS NULL OR h.dueAt >= :from)}에
     * null을 넘기면 PostgreSQL이 파라미터 타입을 추론하지 못해 42P18로 실패한다
     * (Short·LocalDate와 달리 OffsetDateTime은 통하지 않는다).
     *
     * <p>정렬은 마감 오름차순이라 <b>가장 오래 밀린 것이 맨 위</b>다. S-2 목록과 같은 순서다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        WHERE s.student.id = :studentId AND s.status = 'NOT_SUBMITTED'
        ORDER BY h.dueAt, h.id
        """)
    List<Submission> findOpenByStudent(@Param("studentId") Long studentId);

    /**
     * 그리드 한 장의 칸 전부. 열마다 따로 조회하면 열 N개에 N쿼리다.
     * 수업일 하나에 열이 3~4개, 학생이 30명이라 100행 남짓이다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH s.student
        WHERE h.lesson.id = :lessonId
          AND h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID
        """)
    List<Submission> findByLessonForGrid(@Param("lessonId") Long lessonId);

    /**
     * 재제출 대상. 열이 열려 있는지는 호출부가 이미 알고 있으므로 여기서는 result만 본다.
     * 목록으로 받는 이유는 수를 세는 것 말고 화면에 이름을 띄울 여지를 남기기 위해서다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.student
        WHERE s.homework.id = :homeworkId
          AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                           com.njwenglish.entity.enums.HomeworkResult.NOT_DONE)
        """)
    List<Submission> findResubmitTargets(@Param("homeworkId") Long homeworkId);

}
