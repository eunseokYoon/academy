package com.njwenglish.repository;

import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    boolean existsByClassRoomIdAndLessonDate(Long classRoomId, LocalDate lessonDate);

    @Query("SELECT l FROM Lesson l JOIN FETCH l.classRoom WHERE l.id = :lessonId")
    Optional<Lesson> findWithClassRoom(@Param("lessonId") Long lessonId);

    /** bulk 생성에서 이미 만들어진 날짜를 건너뛰기 위해 쓴다. */
    @Query("""
        SELECT l.lessonDate FROM Lesson l
        WHERE l.classRoom.id = :classRoomId AND l.lessonDate BETWEEN :from AND :to
        """)
    List<LocalDate> findLessonDates(@Param("classRoomId") Long classRoomId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /**
     * 수업일을 지울 수 있는지. 출석·시청·숙제가 걸려 있으면 FK로 막히기 전에 409로 돌려준다.
     * 그 수업은 이미 운영된 날이라 지우면 학생의 기록이 사라진다.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM attendances  WHERE lesson_id = :lessonId)
            OR EXISTS (SELECT 1 FROM lesson_views WHERE lesson_id = :lessonId)
            OR EXISTS (SELECT 1 FROM homeworks    WHERE lesson_id = :lessonId)
        """, nativeQuery = true)
    boolean hasRecords(@Param("lessonId") Long lessonId);

    /** T-4 목록. 반·기간·주차가 전부 선택이라 null이면 조건을 통과시킨다. */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE (:classRoomId IS NULL OR c.id = :classRoomId)
          AND (:from IS NULL OR l.lessonDate >= :from)
          AND (:to IS NULL OR l.lessonDate <= :to)
          AND (:year IS NULL OR l.year = :year)
          AND (:month IS NULL OR l.month = :month)
          AND (:week IS NULL OR l.week = :week)
        ORDER BY l.lessonDate DESC, c.name ASC
        """)
    List<Lesson> search(@Param("classRoomId") Long classRoomId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to,
                        @Param("year") Short year,
                        @Param("month") Short month,
                        @Param("week") Short week);
}
