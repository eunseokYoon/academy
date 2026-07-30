package com.njwenglish.repository;

import com.njwenglish.entity.Score;
import com.njwenglish.entity.enums.ScoreType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    /** 시험 일정 삭제 가능 여부. 내신 성적이 물려 있으면 FK로 막히기 전에 409로 돌려준다. */
    boolean existsByExamScheduleId(Long examScheduleId);

    /**
     * uq_scores와 같은 키다. 일괄 저장을 두 번 눌러도 행이 중복되지 않도록
     * 저장 전에 이걸로 찾아 갱신한다 — 409를 던지는 게 아니라 덮어쓴다.
     */
    Optional<Score> findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
        Long studentId, ScoreType scoreType, String subject, String examName, LocalDate examDate);

    /** T-8 관리 목록. 학생 한 명의 성적은 연 수십 건이라 페이징이 필요 없다. */
    @Query("""
        SELECT s FROM Score s LEFT JOIN FETCH s.examSchedule
        WHERE s.student.id = :studentId
        ORDER BY s.examDate DESC, s.id DESC
        """)
    List<Score> findByStudent(@Param("studentId") Long studentId);

    /**
     * P-4 단어 시험 시계열. <b>year·month·week 오름차순</b>이 그래프 가로축 순서 그대로다.
     * 프론트에서 정렬하게 두면 달이 바뀌는 지점에서 어긋난다.
     *
     * <p>시험을 안 본 주는 애초에 행이 없다. 빈 점을 채워 넣지 마라 —
     * 선이 0으로 떨어져 "0점 맞았다"로 읽힌다.
     */
    @Query("""
        SELECT s FROM Score s
        WHERE s.student.id = :studentId AND s.scoreType = 'WORD'
        ORDER BY s.year, s.month, s.week, s.examDate
        """)
    List<Score> findWordSeries(@Param("studentId") Long studentId);

    /** 내신·모의는 examDate 내림차순 목록이다. */
    @Query("""
        SELECT s FROM Score s
        WHERE s.student.id = :studentId AND s.scoreType = :scoreType
        ORDER BY s.examDate DESC, s.id DESC
        """)
    List<Score> findByType(@Param("studentId") Long studentId,
                           @Param("scoreType") ScoreType scoreType);
}
