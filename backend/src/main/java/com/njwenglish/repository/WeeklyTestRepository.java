package com.njwenglish.repository;

import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 온라인 클리닉 자동 반영의 헤더 만들기. 이미 있으면(누가 먼저 만들었으면) 아무것도 안 한다.
     *
     * <p>「찾아서 없으면 save」로는 같은 주차 클리닉을 두 학생이 거의 동시에 냈을 때 둘 다
     * 「없음」을 읽고 INSERT해서 한쪽이 {@code uq_weekly_tests} 위반으로 터졌다. 반영이 제출과
     * 같은 트랜잭션이라 <b>그 학생의 제출까지 롤백</b>됐다(2026-09-30 리뷰). ON CONFLICT면 뒤에 온
     * 쪽은 앞 트랜잭션의 커밋을 기다렸다가 넘어가고, 다시 읽으면 그 헤더가 보인다.
     * ON CONFLICT 대상 열은 uq_weekly_tests와 같아야 한다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO weekly_tests
          (class_room_id, test_type, year, month, week, internal_total, external_total)
        VALUES (:classRoomId, 'CLINIC', :year, :month, :week, :internalTotal, :externalTotal)
        ON CONFLICT (class_room_id, test_type, year, month, week) DO NOTHING
        """, nativeQuery = true)
    int insertClinicHeaderIfAbsent(@Param("classRoomId") Long classRoomId,
                                   @Param("year") short year,
                                   @Param("month") short month,
                                   @Param("week") short week,
                                   @Param("internalTotal") short internalTotal,
                                   @Param("externalTotal") short externalTotal);
}
