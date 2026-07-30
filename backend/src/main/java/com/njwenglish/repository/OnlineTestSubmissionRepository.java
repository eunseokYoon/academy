package com.njwenglish.repository;

import com.njwenglish.entity.OnlineTestSubmission;
import com.njwenglish.entity.enums.OnlineTestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlineTestSubmissionRepository
    extends JpaRepository<OnlineTestSubmission, Long> {

    /** UNIQUE (online_test_id, student_id). 학생당 한 행이라 재응시가 스키마로 막힌다. */
    Optional<OnlineTestSubmission> findByOnlineTestIdAndStudentId(Long onlineTestId,
                                                                  Long studentId);

    /** 제출이 하나라도 있으면 테스트를 지울 수 없다. */
    boolean existsByOnlineTestIdAndStatus(Long onlineTestId, OnlineTestStatus status);

    /**
     * 응시만 시작하고 제출은 안 한 행을 함께 지운다. 제출이 있으면 호출부가 이미 409로 막았다.
     *
     * <p>파생 삭제라 엔티티를 읽어 하나씩 지운다. 그래서 같은 트랜잭션에서
     * online_tests를 지우기 전에 호출해야 FK 순서가 맞는다.
     */
    void deleteByOnlineTestId(Long onlineTestId);

    /** T-14 결과 화면. 반 인원이 30명 내외라 페이징이 없다. */
    @Query("""
        SELECT s FROM OnlineTestSubmission s JOIN FETCH s.student
        WHERE s.onlineTest.id = :onlineTestId
        """)
    List<OnlineTestSubmission> findByOnlineTestId(@Param("onlineTestId") Long onlineTestId);

    /** S-10 목록의 상태 열. 테스트마다 쿼리를 날리지 않으려고 한 번에 가져온다. */
    @Query("""
        SELECT s FROM OnlineTestSubmission s
        WHERE s.student.id = :studentId AND s.onlineTest.id IN :testIds
        """)
    List<OnlineTestSubmission> findByStudentAndTests(@Param("studentId") Long studentId,
                                                     @Param("testIds") Collection<Long> testIds);
}
