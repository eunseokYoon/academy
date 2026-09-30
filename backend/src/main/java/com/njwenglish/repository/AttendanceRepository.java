package com.njwenglish.repository;

import com.njwenglish.entity.Attendance;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * 출석 확정. UNIQUE (student_id, lesson_id)가 있으므로 ON CONFLICT로 재확정을 흡수한다.
     * 확정 버튼을 두 번 눌러도 행이 중복되지 않고 정정으로 동작한다 — 409를 던지지 마라.
     *
     * <p>재확정 시 checked_by/checked_at은 최초 확정자를 유지하고 updated_by/updated_at만
     * 갱신한다. 학부모 정정 요청("어제 병원 간다고 말씀드렸는데요")에 답하려면
     * 최초 확정자와 정정자가 구분돼야 한다.
     *
     * <p>attend_date에 lessons.lesson_date를 복사한다. 캘린더 조회가 이 컬럼에 의존한다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO attendances
          (lesson_id, class_room_id, student_id, attend_date, status, memo,
           checked_by, checked_at, created_at, online_auto_blocked)
        VALUES (:lessonId, :classRoomId, :studentId, :attendDate, :status, :memo,
                :teacherId, now(), now(), :onlineAutoBlocked)
        ON CONFLICT (student_id, lesson_id) DO UPDATE
        SET status = EXCLUDED.status,
            memo = EXCLUDED.memo,
            online_auto_blocked = EXCLUDED.online_auto_blocked,
            updated_by = EXCLUDED.checked_by,
            updated_at = now()
        """, nativeQuery = true)
    void upsert(@Param("lessonId") Long lessonId,
                @Param("classRoomId") Long classRoomId,
                @Param("studentId") Long studentId,
                @Param("attendDate") LocalDate attendDate,
                @Param("status") String status,
                @Param("memo") String memo,
                @Param("teacherId") Long teacherId,
                @Param("onlineAutoBlocked") boolean onlineAutoBlocked);

    @Query("""
        SELECT a FROM Attendance a
        JOIN FETCH a.student
        WHERE a.lesson.id = :lessonId
        """)
    List<Attendance> findByLessonId(@Param("lessonId") Long lessonId);

    /**
     * 캘린더 원본. 수업일이 먼저고 출석 행은 없을 수 있다(PENDING).
     * enrollments 기간 조건이 빠지면 퇴원 이후 수업일까지 캘린더에 뜬다.
     *
     * <p>수업일 기준 재원 여부로 거른다. 오늘 기준이 아니다.
     */
    @Query("""
        SELECT l.id AS lessonId,
               l.lessonDate AS lessonDate,
               l.attendanceStatus AS lessonStatus,
               a.status AS attendStatus
        FROM Lesson l
        JOIN Enrollment e ON e.classRoom.id = l.classRoom.id
             AND e.student.id = :studentId
             AND e.joinedAt <= l.lessonDate
             AND (e.leftAt IS NULL OR e.leftAt > l.lessonDate)
        LEFT JOIN Attendance a ON a.lesson.id = l.id AND a.student.id = :studentId
        WHERE l.lessonDate BETWEEN :from AND :to
        ORDER BY l.lessonDate, l.id
        """)
    List<CalendarRow> findCalendarRows(@Param("studentId") Long studentId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * 캘린더 한 줄. attendStatus가 null이면 확정 전이고, 그날은 출석이 아니라 "미확인"이다.
     * lessonStatus까지 함께 보는 이유는 확정 판정이 두 값의 조합이기 때문이다.
     */
    interface CalendarRow {
        Long getLessonId();

        LocalDate getLessonDate();

        LessonAttendanceStatus getLessonStatus();

        AttendanceStatus getAttendStatus();
    }

    @Query("""
        SELECT a FROM Attendance a
        JOIN FETCH a.student
        JOIN FETCH a.classRoom
        WHERE a.id = :attendanceId
        """)
    Optional<Attendance> findWithStudent(@Param("attendanceId") Long attendanceId);

    /** S-5 상세의 그날 출석. 확정 전이면 행이 없고, 그건 출석이 아니라 미확인이다. */
    Optional<Attendance> findByLessonIdAndStudentId(Long lessonId, Long studentId);

    /**
     * P-1 홈의 이번 달 출석 집계.
     *
     * <p><b>수업이 CONFIRMED인 것만</b> 센다. PENDING인 날은 아직 확정되지 않은 날이고
     * 출석이 아니다 — 기본값이 출석이라 이 조건이 빠지면 선생님이 깜빡한 날까지
     * 학부모에게 "출석 11회"로 보인다.
     */
    @Query("""
        SELECT a.status FROM Attendance a
        WHERE a.student.id = :studentId
          AND a.attendDate BETWEEN :from AND :to
          AND a.lesson.attendanceStatus = 'CONFIRMED'
        """)
    List<AttendanceStatus> findConfirmedStatuses(@Param("studentId") Long studentId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
