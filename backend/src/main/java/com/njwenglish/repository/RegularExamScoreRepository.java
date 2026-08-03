package com.njwenglish.repository;

import com.njwenglish.entity.RegularExamScore;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.RegularExamSlot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegularExamScoreRepository extends JpaRepository<RegularExamScore, Long> {

    List<RegularExamScore> findByStudentIdInAndYear(Collection<Long> studentIds, Short year);

    Optional<RegularExamScore> findByStudentIdAndYearAndExamSlot(
        Long studentId, Short year, RegularExamSlot examSlot);

    /**
     * 명단 합집합의 오른쪽. regular_exam_scores에는 반 정보가 없으므로 그 반에
     * 배정 이력이 있는 학생으로 좁힌다 — 안 그러면 학원 전체 학생이 딸려 나온다.
     * leftAt 조건을 넣지 않는 게 의도다. 퇴원생도 그 해 점수가 있으면 표에 보여야 한다.
     */
    @Query("""
        SELECT DISTINCT e.student FROM Enrollment e
        WHERE e.classRoom.id = :classRoomId
          AND EXISTS (SELECT 1 FROM RegularExamScore r
                      WHERE r.student.id = e.student.id AND r.year = :year)
        ORDER BY e.student.name
        """)
    List<Student> findStudentsWithScores(@Param("classRoomId") Long classRoomId,
                                         @Param("year") Short year);
}
