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
     * T-1 대시보드의 미확인 요약. 아직 손댈 것이 남은 숙제만 나온다.
     *
     * <p>JPQL에는 FILTER 절이 없어 SUM(CASE ...)로 센다. 숙제 수가 연 수백 건이라
     * 전체를 훑어도 문제가 없다.
     */
    @Query("""
        SELECT h.id AS homeworkId,
               h.title AS title,
               h.classRoom.name AS classRoomName,
               h.dueAt AS dueAt,
               SUM(CASE WHEN s.status = 'NOT_SUBMITTED' THEN 1 ELSE 0 END) AS notSubmitted,
               SUM(CASE WHEN s.status = 'SUBMITTED' THEN 1 ELSE 0 END) AS awaitingCheck
        FROM Homework h JOIN Submission s ON s.homework = h
        GROUP BY h.id, h.title, h.classRoom.name, h.dueAt
        HAVING SUM(CASE WHEN s.status <> 'CHECKED' THEN 1 ELSE 0 END) > 0
        ORDER BY h.dueAt DESC
        """)
    List<PendingRow> findPendingSummaries();

    interface PendingRow {
        Long getHomeworkId();

        String getTitle();

        String getClassRoomName();

        OffsetDateTime getDueAt();

        long getNotSubmitted();

        long getAwaitingCheck();
    }
}
