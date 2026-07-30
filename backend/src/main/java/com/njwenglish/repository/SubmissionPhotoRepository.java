package com.njwenglish.repository;

import com.njwenglish.entity.SubmissionPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionPhotoRepository extends JpaRepository<SubmissionPhoto, Long> {

    /** sortOrder는 클라이언트가 보낸 값이라 겹칠 수 있다. id로 순서를 고정한다. */
    List<SubmissionPhoto> findBySubmissionIdOrderBySortOrderAscIdAsc(Long submissionId);

    /**
     * T-7 썸네일용. 20명 각각 조회하면 21쿼리가 나간다. 한 번에 가져와 매핑한다.
     * 반 전체라도 최대 30명 × 10장이라 300행이 넘지 않는다.
     */
    @Query("""
        SELECT p FROM SubmissionPhoto p
        WHERE p.submission.id IN :submissionIds
        ORDER BY p.submission.id, p.sortOrder, p.id
        """)
    List<SubmissionPhoto> findBySubmissionIds(
        @Param("submissionIds") Collection<Long> submissionIds);

    long countBySubmissionId(Long submissionId);

    /** 목록 화면은 매수만 필요하다. 사진 행을 전부 가져올 이유가 없다. */
    @Query("""
        SELECT p.submission.id AS submissionId, COUNT(p) AS photoCount
        FROM SubmissionPhoto p
        WHERE p.submission.id IN :submissionIds
        GROUP BY p.submission.id
        """)
    List<PhotoCountRow> countBySubmissionIds(
        @Param("submissionIds") Collection<Long> submissionIds);

    interface PhotoCountRow {
        Long getSubmissionId();

        long getPhotoCount();
    }

    /** 사진 삭제는 반드시 소유 제출물과 함께 조회한다. photoId만으로 지우면 남의 사진이 지워진다. */
    Optional<SubmissionPhoto> findByIdAndSubmissionId(Long id, Long submissionId);
}
