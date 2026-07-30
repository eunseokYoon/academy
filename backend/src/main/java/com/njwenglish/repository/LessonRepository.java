package com.njwenglish.repository;

import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 미확정 수업. 미래 수업일도 PENDING이라 오늘까지로 잘라야 한다 —
     * 안 자르면 아직 오지도 않은 날이 "출석 확정해야 할 수업"으로 뜬다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate <= :today AND l.attendanceStatus = 'PENDING'
        ORDER BY l.lessonDate DESC, c.name ASC
        """)
    List<Lesson> findPendingUntil(@Param("today") LocalDate today);

    /**
     * T-1 대시보드의 오늘 수업. 시작 시각은 lessons에 없고 반에 있다(class_rooms.start_time).
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate = :date
        ORDER BY c.startTime NULLS LAST, c.name
        """)
    List<Lesson> findByDateWithClassRoom(@Param("date") LocalDate date);

    /**
     * S-1·P-1 홈의 다음 수업 하나. 오늘 포함이다 — 오늘 저녁 수업이 아침에 사라지면 안 된다.
     *
     * <p>재원 중인 반(leftAt IS NULL)만 본다. 퇴원한 반의 다음 수업이 뜨면 안 된다.
     * 여러 반에 속한 학생이 같은 수업을 두 번 만나지 않도록 JOIN이 아니라 EXISTS다.
     *
     * <p>P-1은 여기서 날짜만 꺼내 쓴다. 학부모에게는 수업 제목·내용·영상을 노출하지 않는다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate >= :today
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.leftAt IS NULL)
        ORDER BY l.lessonDate, c.startTime NULLS LAST, c.name
        LIMIT 1
        """)
    Optional<Lesson> findNextForStudent(@Param("studentId") Long studentId,
                                        @Param("today") LocalDate today);

    /** T-1 할 일: 출석 미확정. findPendingUntil과 조건이 같아야 숫자와 목록이 어긋나지 않는다. */
    @Query("""
        SELECT COUNT(l) FROM Lesson l
        WHERE l.lessonDate <= :today AND l.attendanceStatus = 'PENDING'
        """)
    long countPendingUntil(@Param("today") LocalDate today);

    /**
     * T-1 할 일: 내용 미작성. 지난 수업만 센다 —
     * 미래 수업은 아직 안 쓴 게 정상이라 세면 숫자가 영원히 0이 되지 않는다.
     */
    @Query("""
        SELECT COUNT(l) FROM Lesson l
        WHERE l.lessonDate <= :today AND l.content IS NULL
        """)
    long countUnwrittenUntil(@Param("today") LocalDate today);

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

    /**
     * S-5 목록. 조건 네 개가 전부 필요하다.
     *
     * <ol>
     *   <li>학생이 속한(속했던) 반의 수업만
     *   <li>published_at IS NOT NULL — 작성 중인 초안은 안 보인다
     *   <li>수업일이 그 반 재원 기간 안 — <b>이게 빠지면 5월 입반 학생이 3월 영상을 본다</b>
     *   <li>퇴원 이후 수업도 제외 (leftAt 조건)
     * </ol>
     *
     * <p>여러 반에 속한 학생이 같은 수업을 두 번 보지 않도록 JOIN이 아니라 EXISTS다.
     */
    @Query(value = """
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.publishedAt IS NOT NULL
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.joinedAt <= l.lessonDate
                        AND (e.leftAt IS NULL OR e.leftAt > l.lessonDate))
          AND (:year IS NULL OR l.year = :year)
          AND (:month IS NULL OR l.month = :month)
          AND (:week IS NULL OR l.week = :week)
        ORDER BY l.lessonDate DESC, l.id DESC
        """,
        countQuery = """
        SELECT COUNT(l) FROM Lesson l
        WHERE l.publishedAt IS NOT NULL
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.joinedAt <= l.lessonDate
                        AND (e.leftAt IS NULL OR e.leftAt > l.lessonDate))
          AND (:year IS NULL OR l.year = :year)
          AND (:month IS NULL OR l.month = :month)
          AND (:week IS NULL OR l.week = :week)
        """)
    Page<Lesson> findForStudent(@Param("studentId") Long studentId,
                                @Param("year") Short year,
                                @Param("month") Short month,
                                @Param("week") Short week,
                                Pageable pageable);

    /** S-5 상세. 목록과 같은 조건이라 접근 불가한 수업은 아예 조회되지 않는다(404). */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.id = :lessonId
          AND l.publishedAt IS NOT NULL
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.joinedAt <= l.lessonDate
                        AND (e.leftAt IS NULL OR e.leftAt > l.lessonDate))
        """)
    Optional<Lesson> findForStudent(@Param("lessonId") Long lessonId,
                                    @Param("studentId") Long studentId);
}
