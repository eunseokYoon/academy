package com.njwenglish.repository;

import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.enums.ExamType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

    boolean existsByClassRoomIdAndYearAndSemesterAndExamType(Long classRoomId, Short year,
                                                             Short semester, ExamType examType);

    @Query("SELECT e FROM ExamSchedule e JOIN FETCH e.classRoom WHERE e.id = :examScheduleId")
    Optional<ExamSchedule> findWithClassRoom(@Param("examScheduleId") Long examScheduleId);

    /** T-11 격자. 반·연도가 둘 다 선택이라 null이면 조건을 통과시킨다. */
    @Query("""
        SELECT e FROM ExamSchedule e JOIN FETCH e.classRoom c
        WHERE (:classRoomId IS NULL OR c.id = :classRoomId)
          AND (:year IS NULL OR e.year = :year)
        ORDER BY e.startDate, c.name
        """)
    List<ExamSchedule> search(@Param("classRoomId") Long classRoomId,
                              @Param("year") Short year);

    /**
     * 학생·학부모 조회. 재원 중인 반의 일정만이다.
     * 퇴원한 반이 섞이면 지난 학기 시험이 목록에 남는다.
     */
    @Query("""
        SELECT e FROM ExamSchedule e
        WHERE e.classRoom.id IN :classRoomIds
        ORDER BY e.startDate
        """)
    List<ExamSchedule> findByClassRoomIds(@Param("classRoomIds") Collection<Long> classRoomIds);

    /**
     * D-day 하나. 학생이 여러 반에 속할 수 있어 반 목록 전체에서 가장 가까운 일정을 고른다.
     * 오늘 이후(오늘 포함)만 본다 — 이미 끝난 시험이 D-day로 뜨면 안 된다.
     */
    @Query("""
        SELECT e FROM ExamSchedule e
        WHERE e.classRoom.id IN :classRoomIds AND e.startDate >= :today
        ORDER BY e.startDate
        LIMIT 1
        """)
    Optional<ExamSchedule> findNext(@Param("classRoomIds") Collection<Long> classRoomIds,
                                    @Param("today") LocalDate today);
}
