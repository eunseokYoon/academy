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
     * <p><b>GRID 숙제는 재제출 대상만 나온다.</b> 반 전원을 담으면 ⭕를 받아 낼 필요가
     * 없는 학생까지 "미제출"로 보인다.
     *
     * <p>둘째 줄의 {@code h.dueAt IS NOT NULL AND s.result IN (...)}는
     * {@link com.njwenglish.entity.Submission#isResubmitTarget()}
     * ({@code homework.isResubmitOpen() && result IN (PARTIAL, NOT_DONE)})를 그대로 SQL로
     * 옮긴 것이다. dueAt 조건을 빼면 채점만 하고 재제출을 아직 안 연 열(출제·채점과 재제출
     * 오픈은 별개 동작이다)까지 여기 걸려, 아무도 제출한 적 없는 칸이 "미제출"로 잡힌다.
     * 두 절을 분리해서 하나만 고치지 마라 — 나머지 절이 다시 같은 버그를 만든다.
     *
     * <p>셋째 줄의 {@code s.status <> NOT_SUBMITTED}는 확인이 끝나 ⭕가 된 학생을 위한
     * 별도 조건이다. 이게 없으면 선생님이 방금 확인한 결과가 목록에서 사라진다.
     *
     * <p>정렬은 처리할 것이 위로 온다: 확인대기 → 미제출 → 완료.
     * 이름은 students.name이다. user.name으로 쓰면 미가입 학생이 통째로 사라진다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.student st
        JOIN s.homework h
        WHERE h.id = :homeworkId
          AND (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
               OR (h.dueAt IS NOT NULL
                   AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                    com.njwenglish.entity.enums.HomeworkResult.NOT_DONE))
               OR s.status <> com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED)
        ORDER BY CASE s.status WHEN 'SUBMITTED' THEN 0 WHEN 'NOT_SUBMITTED' THEN 1 ELSE 2 END,
                 st.name
        """)
    List<Submission> findByHomeworkForTeacher(@Param("homeworkId") Long homeworkId);

    /**
     * 숙제 삭제 시 사진 정리용 id 전체 조회. {@link #findByHomeworkForTeacher}는 GRID를
     * 재제출 대상으로 좁혀 놓았다 — 삭제에 그걸 쓰면 대상 밖 학생의 사진이 S3에 남는다.
     */
    @Query("SELECT s.id FROM Submission s WHERE s.homework.id = :homeworkId")
    List<Long> findAllIdsByHomeworkId(@Param("homeworkId") Long homeworkId);

    /**
     * S-2 · P-3 목록. <b>해야 할 일이 남은 것</b>이 위로 온다.
     *
     * <p>GRID는 재제출 대상, ONLINE은 미제출이 "할 일"이다. 그 안에서는 마감이 가까운 순이고,
     * 나머지는 수업일 최신순이다.
     *
     * <p>1번째·2번째 정렬 키의 CASE 조건이 글자 그대로 같다. JPQL의 ORDER BY는 SELECT 별칭이나
     * 계산된 버킷을 참조할 방법이 없어서, "할 일" 판정을 두 번 그대로 반복하는 것 말고는 방법이
     * 없다. 2번째 키(마감 오름차순)를 조건 없이 두면 ONLINE의 dueAt은 항상 NOT NULL이라
     * 이미 낸 ONLINE 행이 재제출 안 열린 GRID 열(dueAt NULL)보다 앞서 버려, 3번째 키(수업일)가
     * "나머지" 그룹 안에서 거의 동작하지 않는다. 둘 중 하나만 고쳐서 "간단히" 만들지 마라 —
     * 같은 버그가 조용히 되돌아온다.
     *
     * <p>lesson은 반드시 LEFT JOIN이다. INNER로 들어가면 lesson_id가 null인 ONLINE 숙제가
     * 목록에서 통째로 사라진다 — 에러도 안 난다.
     */
    @Query(value = """
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        LEFT JOIN FETCH h.lesson l
        WHERE s.student.id = :studentId
          AND (:status IS NULL OR CAST(s.status AS string) = :status)
        ORDER BY CASE WHEN (h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID
                            AND h.dueAt IS NOT NULL
                            AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                             com.njwenglish.entity.enums.HomeworkResult.NOT_DONE))
                        OR (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
                            AND s.status = com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED)
                      THEN 0 ELSE 1 END,
                 CASE WHEN (h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID
                            AND h.dueAt IS NOT NULL
                            AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                             com.njwenglish.entity.enums.HomeworkResult.NOT_DONE))
                        OR (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
                            AND s.status = com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED)
                      THEN h.dueAt END ASC,
                 l.lessonDate DESC NULLS LAST,
                 h.id DESC
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
     *
     * <p>재제출 취소(closeResubmit) 전용이다. 삭제 차단은 채점 결과도 봐야 하므로
     * {@link #countGradedOrSubmitted}를 쓴다 — 지우지 않는다.
     */
    long countByHomeworkIdAndStatusNot(Long homeworkId, SubmissionStatus status);

    /**
     * 숙제 삭제 차단 판정. "행이 있으면 409"로 짜면 어떤 숙제도 못 지운다 —
     * 출제·열 생성 시 대상 전원의 행이 미리 깔리기 때문이다.
     *
     * <p>제출물뿐 아니라 <b>채점 결과</b>도 지켜야 한다. result 조건을 빼면
     * 선생님이 채운 ⭕🔺❌가 경고 없이 사라진다.
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s
        WHERE s.homework.id = :homeworkId
          AND (s.status <> com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED
               OR s.result IS NOT NULL)
        """)
    long countGradedOrSubmitted(@Param("homeworkId") Long homeworkId);

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
     * <p><b>근거는 status가 아니라 result다.</b> GRID에서 ⭕를 받은 학생은 온라인 제출을
     * 하지 않아 status가 영원히 NOT_SUBMITTED다. status로 세면 숙제를 다 해온 학생의
     * 캘린더가 새빨개진다.
     *
     * <p>⭕=100, 🔺=퍼센트, ❌=0으로 누적한 합이 scoreSum이다.
     * ONLINE은 기존대로 제출했으면 100이다.
     *
     * <p><b>미채점(result IS NULL)인 GRID 칸은 아예 제외한다.</b> 채점 전에 0%로 잡히면
     * 빨간 띠가 뜬다. 대상이 0이면 행 자체가 안 나오고 서비스가 null을 내린다 —
     * coalesce(...,0)을 붙이지 마라.
     */
    @Query("""
        SELECT h.lesson.id AS lessonId,
               SUM(CASE
                     WHEN h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID THEN
                       CASE s.result
                         WHEN com.njwenglish.entity.enums.HomeworkResult.DONE THEN 100
                         WHEN com.njwenglish.entity.enums.HomeworkResult.PARTIAL
                              THEN s.completionRate
                         ELSE 0 END
                     WHEN s.status IN (com.njwenglish.entity.enums.SubmissionStatus.SUBMITTED,
                                       com.njwenglish.entity.enums.SubmissionStatus.CHECKED)
                          THEN 100
                     ELSE 0 END) AS scoreSum,
               COUNT(s) AS targetCount
        FROM Submission s JOIN s.homework h
        WHERE s.student.id = :studentId AND h.lesson.id IN :lessonIds
          AND (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
               OR s.result IS NOT NULL)
        GROUP BY h.lesson.id
        """)
    List<HomeworkRateRow> findHomeworkRates(@Param("studentId") Long studentId,
                                            @Param("lessonIds") Collection<Long> lessonIds);

    interface HomeworkRateRow {
        Long getLessonId();

        /** ⭕=100, 🔺=퍼센트, ❌=0의 합. targetCount로 나누면 완료율이다. */
        long getScoreSum();

        long getTargetCount();
    }

    /** T-1 할 일: 확인 대기 제출물. SUBMITTED는 학생이 냈지만 선생님이 아직 안 본 것이다. */
    long countByStatus(SubmissionStatus status);

    /**
     * P-1·S-1 홈의 "안 낸 숙제" 수.
     *
     * <p>GRID는 <b>재제출 대상인데 아직 안 낸 것</b>만 센다. ⭕를 받은 칸은 status가
     * 영원히 NOT_SUBMITTED라 status만 보면 다 해온 학생의 홈에 큰 숫자가 뜬다.
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s JOIN s.homework h
        WHERE s.student.id = :studentId
          AND s.status = com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED
          AND (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
               OR (h.dueAt IS NOT NULL
                   AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                    com.njwenglish.entity.enums.HomeworkResult.NOT_DONE)))
        """)
    long countPendingHomeworks(@Param("studentId") Long studentId);

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
     *
     * <p><b>GRID는 재제출 대상만 담는다.</b> ⭕를 받은 칸은 status가 영원히 NOT_SUBMITTED라
     * 이 조건이 없으면 다 해온 학생의 홈에도 "할 일"이 남아 보인다. 미채점(재제출 미오픈)
     * 열도 같은 이유로 뺀다 — 선생님이 아직 확인하지 않은 것이지 학생이 할 일이 아니다.
     */
    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.homework h
        JOIN FETCH h.classRoom
        WHERE s.student.id = :studentId AND s.status = 'NOT_SUBMITTED'
          AND (h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
               OR (h.dueAt IS NOT NULL
                   AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                    com.njwenglish.entity.enums.HomeworkResult.NOT_DONE)))
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
