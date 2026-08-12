package com.njwenglish.repository;

import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTestScore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyTestScoreRepository extends JpaRepository<WeeklyTestScore, Long> {

    /** 그리드 한 장의 셀 전부. testIds는 최대 4개다. */
    List<WeeklyTestScore> findByWeeklyTestIdIn(Collection<Long> weeklyTestIds);

    Optional<WeeklyTestScore> findByWeeklyTestIdAndStudentId(Long weeklyTestId, Long studentId);

    /**
     * 명단 합집합의 오른쪽 — 지금은 재원생이 아니지만 그 주차에 성적이 남아 있는 학생.
     * 주차에는 날짜가 없어 findActiveStudents의 기준일을 만들 수 없어서 이렇게 보완한다.
     *
     * <p>weeklyTest.classRoom으로 반이 이미 좁혀져 있다. 정렬은 s.student.name이다 —
     * s.student.user.name을 쓰면 미가입 학생이 통째로 사라진다.
     */
    @Query("""
        SELECT DISTINCT s.student FROM WeeklyTestScore s
        WHERE s.weeklyTest.classRoom.id = :classRoomId
          AND s.weeklyTest.year = :year
          AND s.weeklyTest.month = :month
          AND s.weeklyTest.week = :week
        ORDER BY s.student.name
        """)
    List<Student> findStudentsWithScores(@Param("classRoomId") Long classRoomId,
                                         @Param("year") Short year,
                                         @Param("month") Short month,
                                         @Param("week") Short week);

    /**
     * S-7 · P-4 시계열. <b>year·month·week 오름차순</b>이 그래프 가로축 순서 그대로다.
     * 프론트에서 정렬하게 두면 달이 바뀌는 지점에서 어긋난다.
     *
     * <p>시험을 안 본 주는 애초에 행이 없다. 빈 점을 채워 넣지 마라 —
     * 선이 0으로 떨어져 "0점 맞았다"로 읽힌다.
     */
    @Query("""
        SELECT s FROM WeeklyTestScore s JOIN FETCH s.weeklyTest t
        WHERE s.student.id = :studentId
        ORDER BY t.year, t.month, t.week, t.id
        """)
    List<WeeklyTestScore> findByStudentOrderedByWeek(@Param("studentId") Long studentId);
}
