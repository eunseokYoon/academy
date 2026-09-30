package com.njwenglish.repository;

import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyTestRepository extends JpaRepository<WeeklyTest, Long> {

    /**
     * 미응시 후보(2026-09-29). 그 반에서 <b>누군가는 성적이 있는데</b> 이 학생만 없는 시험.
     * 누군가 있어야 하는 이유 — 선생님이 헤더(전체 문항 수)만 미리 적어 둔 주차까지
     * 반 전원이 미응시가 되면 안 된다. 재원 기간·지난 주 여부는 서비스가 거른다.
     */
    @Query("""
        SELECT t FROM WeeklyTest t
        WHERE t.classRoom.id IN :classRoomIds
          AND EXISTS (SELECT 1 FROM WeeklyTestScore s WHERE s.weeklyTest = t)
          AND NOT EXISTS (SELECT 1 FROM WeeklyTestScore s
                          WHERE s.weeklyTest = t AND s.student.id = :studentId)
        """)
    List<WeeklyTest> findTakenByOthersOnly(@Param("classRoomIds") Collection<Long> classRoomIds,
                                           @Param("studentId") Long studentId);

    /** 그리드 한 장의 헤더 전부. 종류가 4개뿐이라 최대 4행이다. */
    List<WeeklyTest> findByClassRoomIdAndYearAndMonthAndWeek(
        Long classRoomId, Short year, Short month, Short week);

    /** uq_weekly_tests와 같은 키다. 저장 전에 이걸로 찾아 갱신한다 — 409를 던지지 않는다. */
    Optional<WeeklyTest> findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
        Long classRoomId, WeeklyTestType testType, Short year, Short month, Short week);
}
