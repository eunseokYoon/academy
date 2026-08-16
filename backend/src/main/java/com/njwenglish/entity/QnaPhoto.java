package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 질문·답글 사진. 질문과 답글이 한 테이블이라 사진 테이블도 하나다 —
 * nullable FK도 CHECK도 필요 없고, 업로드·삭제 코드가 한 벌로 끝난다.
 *
 * <p>DB에 ON DELETE CASCADE가 걸려 있다(submission_photos와 같다). 엔티티에는 cascade를 걸지 않는다.
 *
 * <p>파일은 presigned URL로 S3에 직접 올린다. 여기엔 s3Key만 남는다.
 */
@Entity
@Table(name = "qna_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaPhoto extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qna_post_id", nullable = false)
    private QnaPost post;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    private Integer bytes;

    public static QnaPhoto of(QnaPost post, String s3Key, short sortOrder, Integer bytes) {
        QnaPhoto photo = new QnaPhoto();
        photo.post = post;
        photo.s3Key = s3Key;
        photo.sortOrder = sortOrder;
        photo.bytes = bytes;
        return photo;
    }
}
