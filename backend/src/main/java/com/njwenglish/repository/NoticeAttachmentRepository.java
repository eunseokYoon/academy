package com.njwenglish.repository;

import com.njwenglish.entity.NoticeAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeAttachmentRepository extends JpaRepository<NoticeAttachment, Long> {

    List<NoticeAttachment> findByNoticeIdOrderBySortOrder(Long noticeId);

    /**
     * 같은 s3Key를 쓰는 첨부 수. <b>0일 때만 S3 객체를 지운다.</b>
     * 지금은 공지 하나에만 붙지만, 세지 않고 지우는 코드는 반드시 나중에 사고가 된다.
     */
    long countByS3Key(String s3Key);

    void deleteByNoticeId(Long noticeId);

    /**
     * 목록 화면의 hasAttachment 표시용. 20건 × 쿼리를 피하려고 noticeId 묶음으로 한 번에 센다.
     * 존재 여부만 필요해서 개수 대신 첨부가 있는 noticeId만 돌려준다.
     */
    @Query("""
        SELECT a.notice.id FROM NoticeAttachment a
        WHERE a.notice.id IN :noticeIds
        GROUP BY a.notice.id
        """)
    List<Long> findNoticeIdsWithAttachment(@Param("noticeIds") Collection<Long> noticeIds);
}
