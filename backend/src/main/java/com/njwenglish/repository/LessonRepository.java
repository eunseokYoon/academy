package com.njwenglish.repository;

import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.util.Collection;
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
     * 수업일을 지울 수 있는지. 출석·숙제가 걸려 있으면 FK로 막히기 전에 409로 돌려준다.
     * 그 수업은 이미 운영된 날이라 지우면 학생의 기록이 사라진다.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM attendances WHERE lesson_id = :lessonId)
            OR EXISTS (SELECT 1 FROM homeworks   WHERE lesson_id = :lessonId)
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
     * T-5 주차 조회. <b>확정된 수업도 함께</b> 내려준다 — 지난 출석을 고치려면 들어갈
     * 입구가 있어야 한다(2026-09-01 확정).
     *
     * <p><b>오늘까지로 자르는 것은 findPendingUntil과 같다.</b> 이번 주를 열면 아직 오지
     * 않은 날이 섞이는데, 실수로 미리 확정하면 학부모 캘린더가 초록색이 된다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate BETWEEN :from AND :to AND l.lessonDate <= :today
        ORDER BY l.lessonDate DESC, c.name ASC
        """)
    List<Lesson> findForAttendanceWeek(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("today") LocalDate today);

    /**
     * T-1 대시보드의 오늘 수업. 시작 시각은 lessons에 없고 반의 요일 슬롯에 있다.
     *
     * <p>시각으로 정렬하지 않는다. 반이 주 2회면 요일마다 시각이 달라서 반 하나로 값을
     * 고를 수 없다. 그 수업 날짜에 맞는 슬롯을 골라 <b>DashboardService가 자바에서 정렬</b>한다.
     * 여기서 이름순은 그 정렬의 동점 처리다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate = :date
        ORDER BY c.name
        """)
    List<Lesson> findByDateWithClassRoom(@Param("date") LocalDate date);

    /**
     * S-1·P-1 홈의 다음 수업 하나. 오늘 포함이다 — 오늘 저녁 수업이 아침에 사라지면 안 된다.
     *
     * <p>재원 중인 반(leftAt IS NULL)만 본다. 퇴원한 반의 다음 수업이 뜨면 안 된다.
     * 여러 반에 속한 학생이 같은 수업을 두 번 만나지 않도록 JOIN이 아니라 EXISTS다.
     *
     * <p>P-1은 여기서 날짜만 꺼내 쓴다. 학부모에게는 수업 제목·내용·영상을 노출하지 않는다.
     *
     * <p>같은 날짜가 겹치면 이름순이다. 학생은 보통 반 하나에 속해서 동점이 거의 없고,
     * 시각으로 정렬하려면 반의 요일 슬롯 중 그 날짜에 맞는 것을 골라야 해서 값이 비싸다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate >= :today
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.leftAt IS NULL)
        ORDER BY l.lessonDate, c.name
        LIMIT 1
        """)
    Optional<Lesson> findNextForStudent(@Param("studentId") Long studentId,
                                        @Param("today") LocalDate today);

    /**
     * S-1 홈의 지난 수업. findNextForStudent의 거울이라 <b>수강 중인 반 조건이 같아야 한다</b> —
     * 한쪽만 고치면 홈에 남의 반 수업이 뜬다.
     *
     * <p><b>쓴 게 하나라도 있는 수업만</b> 고른다. 수업 행은 일괄 생성이라 제목·내용·영상이
     * 전부 빈 채로 널려 있고, 그걸 그대로 집으면 홈에 빈 카드가 뜬다. 조건을 빼지 마라 —
     * 대신 "가장 최근에 뭔가 적힌 수업"이라 지난주가 비었으면 그 전 수업이 올라온다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate < :today
          AND (l.content IS NOT NULL OR l.videoUrl IS NOT NULL OR l.title IS NOT NULL)
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.leftAt IS NULL)
        ORDER BY l.lessonDate DESC, c.name
        LIMIT 1
        """)
    Optional<Lesson> findLastForStudent(@Param("studentId") Long studentId,
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

    /**
     * T-4 목록. 반·기간·주차가 전부 선택이라 null이면 조건을 통과시킨다.
     *
     * <p>from·to의 {@code CAST(... AS date)}를 빼지 마라. Hibernate가 파라미터를 둘로 전개해
     * {@code $1 is null} 쪽에 타입 단서가 없어지고, PostgreSQL이 42P18로 거부한다.
     * <b>날짜를 실제로 고를 때만 터져서</b> 파라미터 없이 열어 보면 멀쩡해 보인다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE (:classRoomId IS NULL OR c.id = :classRoomId)
          AND (CAST(:from AS date) IS NULL OR l.lessonDate >= :from)
          AND (CAST(:to AS date) IS NULL OR l.lessonDate <= :to)
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

    /**
     * S-9 수업일 변경에서 "못 가는 내 수업"으로 고를 수 있는 회차.
     *
     * <p><b>published_at을 보지 않는다.</b> 옮기려는 건 앞으로 올 수업인데 그건 아직 발행
     * 전이라, 이 조건을 넣으면 목록이 늘 비어서 기능 자체가 동작하지 않는다. 대신 응답에
     * 수업 내용을 담지 않는다 — 날짜와 반 이름까지가 이 목록의 전부다.
     *
     * <p>재원 기간 조건은 S-5 목록과 같다. 이게 빠지면 퇴원 이후 수업까지 고를 수 있다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate BETWEEN :from AND :to
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = l.classRoom.id
                        AND e.student.id = :studentId
                        AND e.joinedAt <= l.lessonDate
                        AND (e.leftAt IS NULL OR e.leftAt > l.lessonDate))
        ORDER BY l.lessonDate ASC, l.id ASC
        """)
    List<Lesson> findUpcomingForStudent(@Param("studentId") Long studentId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /**
     * S-9에서 "대신 갈 수업" 후보. 같은 주(월~일)에 열리는 다른 반 수업이다.
     *
     * <p>여기도 published_at을 보지 않는다. 위와 같은 이유이고, 같은 이유로
     * <b>다른 반 수업의 제목·영상·레포트는 절대 응답에 넣지 마라.</b> 날짜·반 이름·시각까지다.
     *
     * <p>종료된 반은 뺀다. 이미 끝난 반의 수업일을 대체 수업으로 고를 수는 없다.
     *
     * <p>excludedClassRoomIds는 빈 컬렉션이면 안 된다(SQL 오류). 호출부에서 더미를 넣는다.
     */
    @Query("""
        SELECT l FROM Lesson l JOIN FETCH l.classRoom c
        WHERE l.lessonDate BETWEEN :from AND :to
          AND c.id NOT IN :excludedClassRoomIds
          AND c.status = 'ACTIVE'
        ORDER BY l.lessonDate ASC, c.name ASC
        """)
    List<Lesson> findWeekCandidates(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("excludedClassRoomIds")
                                    Collection<Long> excludedClassRoomIds);

    /**
     * 재제출 마감 기본값용. 오늘 이후 그 반의 수업일을 가까운 것부터 준다.
     * Pageable 없이 첫 값만 쓰므로 호출부가 findFirst로 꺼낸다.
     */
    @Query("""
        SELECT l.lessonDate FROM Lesson l
        WHERE l.classRoom.id = :classRoomId AND l.lessonDate > :after
        ORDER BY l.lessonDate ASC
        """)
    List<LocalDate> findNextLessonDates(@Param("classRoomId") Long classRoomId,
                                        @Param("after") LocalDate after);
}
