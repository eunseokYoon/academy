package com.njwenglish.repository;

import com.njwenglish.entity.QnaPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QnaPhotoRepository extends JpaRepository<QnaPhoto, Long> {

    List<QnaPhoto> findByPostIdOrderBySortOrderAsc(Long postId);

    /** 상세 한 번에 질문·답글 사진을 모두 가져온다. postId별로 서비스에서 묶는다. */
    @Query("""
        SELECT p FROM QnaPhoto p
        WHERE p.post.id IN :postIds
        ORDER BY p.post.id, p.sortOrder
        """)
    List<QnaPhoto> findByPostIds(@Param("postIds") Collection<Long> postIds);

    long countByPostId(Long postId);
}
