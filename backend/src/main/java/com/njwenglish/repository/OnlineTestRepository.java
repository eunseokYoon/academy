package com.njwenglish.repository;

import com.njwenglish.entity.OnlineTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlineTestRepository extends JpaRepository<OnlineTest, Long> {

    @Query("SELECT t FROM OnlineTest t JOIN FETCH t.classRoom WHERE t.id = :testId")
    Optional<OnlineTest> findWithClassRoom(@Param("testId") Long testId);

    /** T-14 목록. 반·주차가 전부 선택이라 null이면 조건을 통과시킨다. */
    @Query("""
        SELECT t FROM OnlineTest t JOIN FETCH t.classRoom c
        WHERE (:classRoomId IS NULL OR c.id = :classRoomId)
          AND (:year IS NULL OR t.year = :year)
          AND (:month IS NULL OR t.month = :month)
          AND (:week IS NULL OR t.week = :week)
        ORDER BY t.year DESC, t.month DESC, t.week DESC, t.id DESC
        """)
    List<OnlineTest> search(@Param("classRoomId") Long classRoomId,
                            @Param("year") Short year,
                            @Param("month") Short month,
                            @Param("week") Short week);

    /**
     * S-10 목록. 공개됨 + 학생이 그 반 재원생 + opens_at 경과, 셋 다 필요하다.
     *
     * <p>재원 판정은 <b>오늘</b> 기준이다. 수업 영상(S-5)이 수업일 기준인 것과 다른데,
     * 시험은 지금 푸는 것이라 지금 그 반에 다니고 있어야 응시 대상이다.
     */
    @Query("""
        SELECT t FROM OnlineTest t JOIN FETCH t.classRoom c
        WHERE t.publishedAt IS NOT NULL
          AND (t.opensAt IS NULL OR t.opensAt <= :now)
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = c.id
                        AND e.student.id = :studentId
                        AND e.leftAt IS NULL)
        ORDER BY t.closesAt ASC NULLS LAST, t.id DESC
        """)
    List<OnlineTest> findOpenForStudent(@Param("studentId") Long studentId,
                                        @Param("now") OffsetDateTime now);

    /** S-10 응시 화면. 목록과 같은 조건이라 대상이 아니면 404다. */
    @Query("""
        SELECT t FROM OnlineTest t JOIN FETCH t.classRoom c
        WHERE t.id = :testId
          AND t.publishedAt IS NOT NULL
          AND (t.opensAt IS NULL OR t.opensAt <= :now)
          AND EXISTS (SELECT 1 FROM Enrollment e
                      WHERE e.classRoom.id = c.id
                        AND e.student.id = :studentId
                        AND e.leftAt IS NULL)
        """)
    Optional<OnlineTest> findOpenForStudent(@Param("testId") Long testId,
                                            @Param("studentId") Long studentId,
                                            @Param("now") OffsetDateTime now);
}
