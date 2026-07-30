package com.njwenglish.repository;

import com.njwenglish.entity.Feedback;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** submission_id가 UNIQUE라 제출 1건당 1개다. 수정은 UPDATE로 처리한다. */
    Optional<Feedback> findBySubmissionId(Long submissionId);

    /** 목록에서 hasFeedback을 채운다. 제출물마다 조회하면 N+1이 된다. */
    @Query("SELECT f.submission.id FROM Feedback f WHERE f.submission.id IN :submissionIds")
    List<Long> findSubmissionIdsIn(@Param("submissionIds") Collection<Long> submissionIds);
}
