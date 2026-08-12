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
 * DB에 ON DELETE CASCADE가 걸린 두 곳 중 하나다 (다른 하나는 feedbacks).
 * 엔티티에는 cascade를 걸지 않는다.
 *
 * <p>파일은 presigned URL로 S3에 직접 올린다. 여기엔 s3Key만 남는다.
 */
@Entity
@Table(name = "submission_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionPhoto extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    private Integer bytes;

    public static SubmissionPhoto of(Submission submission, String s3Key, short sortOrder,
                                     Integer bytes) {
        SubmissionPhoto photo = new SubmissionPhoto();
        photo.submission = submission;
        photo.s3Key = s3Key;
        photo.sortOrder = sortOrder;
        photo.bytes = bytes;
        return photo;
    }
}
