package com.njwenglish.repository;

import com.njwenglish.entity.Homework;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {

    /**
     * T-6 목록. 반은 선택이라 null이면 조건을 통과시킨다.
     *
     * <p>기간은 <b>서비스에서 항상 채워 넣는다.</b> 타임스탬프 파라미터에 null을 넣고
     * `:from IS NULL`로 걸면 PostgreSQL이 타입을 추론하지 못해
     * "could not determine data type of parameter"로 죽는다. 여기서 IS NULL을 되살리지 마라.
     */
    @Query(value = """
        SELECT h FROM Homework h
        JOIN FETCH h.classRoom
        LEFT JOIN FETCH h.lesson
        WHERE (:classRoomId IS NULL OR h.classRoom.id = :classRoomId)
          AND h.dueAt BETWEEN :from AND :to
        ORDER BY h.dueAt DESC, h.id DESC
        """,
        countQuery = """
        SELECT COUNT(h) FROM Homework h
        WHERE (:classRoomId IS NULL OR h.classRoom.id = :classRoomId)
          AND h.dueAt BETWEEN :from AND :to
        """)
    Page<Homework> search(@Param("classRoomId") Long classRoomId,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to,
                          Pageable pageable);

    @Query("""
        SELECT h FROM Homework h
        JOIN FETCH h.classRoom
        LEFT JOIN FETCH h.lesson
        WHERE h.id = :homeworkId
        """)
    Optional<Homework> findWithClassRoom(@Param("homeworkId") Long homeworkId);

    /**
     * S-5에서 수업에 딸린 숙제를 붙인다. 한 수업에 숙제가 둘 이상일 수 있으므로
     * 서비스에서 마감이 이른 것 하나만 고른다.
     */
    @Query("""
        SELECT h FROM Homework h
        WHERE h.lesson.id IN :lessonIds
        ORDER BY h.dueAt ASC, h.id ASC
        """)
    List<Homework> findByLessonIds(@Param("lessonIds") Collection<Long> lessonIds);

    /**
     * T-1 대시보드의 미제출 요약. <b>아직 안 낸 학생이 남은 숙제만</b> 나온다.
     *
     * <p>확인 단계가 없어져(2026-08-09) "손댈 것"은 미제출 하나뿐이다. 낸 것은 그 순간
     * ⭕가 되어 선생님이 할 일이 없다. 그래서 GRID는 재제출 대상 칸만 남기고
     * HAVING으로 미제출이 0인 숙제를 떨군다 — 이게 없으면 전원이 낸 열도 계속 뜬다.
     *
     * <p><b>재제출을 안 연 GRID 열은 통째로 빠진다.</b> ⭕를 받은 학생까지 미제출로 잡혀
     * T-1이 처리할 일 없는 항목으로 가득 찬다. 미채점 열은 여기 띄우지 않는다 —
     * 선생님은 그리드 화면에서 직접 확인한다.
     *
     * <p>숙제 수가 연 수백 건이라 전체를 훑어도 문제가 없다.
     *
     * <p>GRID는 status를 보지 않는다 — {@code SubmissionRepository.countPendingHomeworks}의
     * 주석을 봐라.
     */
    @Query("""
        SELECT h.id AS homeworkId,
               h.title AS title,
               h.classRoom.name AS classRoomName,
               h.dueAt AS dueAt,
               COUNT(s) AS notSubmitted
        FROM Homework h JOIN Submission s ON s.homework = h
        WHERE ((h.kind = com.njwenglish.entity.enums.HomeworkKind.ONLINE
            AND s.status = com.njwenglish.entity.enums.SubmissionStatus.NOT_SUBMITTED)
           OR (h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID
               AND h.dueAt IS NOT NULL
               AND s.result IN (com.njwenglish.entity.enums.HomeworkResult.PARTIAL,
                                com.njwenglish.entity.enums.HomeworkResult.NOT_DONE)))
        GROUP BY h.id, h.title, h.classRoom.name, h.dueAt
        ORDER BY h.dueAt DESC
        """)
    List<PendingRow> findPendingSummaries();

    interface PendingRow {
        Long getHomeworkId();

        String getTitle();

        String getClassRoomName();

        OffsetDateTime getDueAt();

        long getNotSubmitted();
    }

    /** 그리드의 열 목록. sort_order 순이고 GRID만 나온다 — ONLINE 숙제는 그리드에 안 뜬다. */
    @Query("""
        SELECT h FROM Homework h
        WHERE h.lesson.id = :lessonId AND h.kind = com.njwenglish.entity.enums.HomeworkKind.GRID
        ORDER BY h.sortOrder ASC, h.id ASC
        """)
    List<Homework> findGridColumns(@Param("lessonId") Long lessonId);
}
