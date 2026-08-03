package com.njwenglish.repository;

import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyTestRepository extends JpaRepository<WeeklyTest, Long> {

    /** 그리드 한 장의 헤더 전부. 종류가 4개뿐이라 최대 4행이다. */
    List<WeeklyTest> findByClassRoomIdAndYearAndMonthAndWeek(
        Long classRoomId, Short year, Short month, Short week);

    /** uq_weekly_tests와 같은 키다. 저장 전에 이걸로 찾아 갱신한다 — 409를 던지지 않는다. */
    Optional<WeeklyTest> findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
        Long classRoomId, WeeklyTestType testType, Short year, Short month, Short week);
}
