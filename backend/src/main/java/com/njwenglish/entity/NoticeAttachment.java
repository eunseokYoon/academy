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
 * 공지에 붙은 파일 하나. 공지당 5개까지다.
 *
 * <p>s3Key는 자료실이 쓰던 {@link com.njwenglish.common.s3.MaterialKeys}가 발급한다 —
 * 프리픽스도 materials/ 그대로다. 서명이 <b>teacherId에 묶인다</b>: 첨부는 공지가
 * 만들어지기 전에 올라가므로 묶을 공지가 없다.
 *
 * <p>fileName은 선생님이 올린 원본 이름이다. 다운로드 시 Content-Disposition에 들어간다.
 */
@Entity
@Table(name = "notice_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeAttachment extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    private Long bytes;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    public static NoticeAttachment of(Notice notice, String s3Key, String fileName, Long bytes,
                                      short sortOrder) {
        NoticeAttachment attachment = new NoticeAttachment();
        attachment.notice = notice;
        attachment.s3Key = s3Key;
        attachment.fileName = fileName;
        attachment.bytes = bytes;
        attachment.sortOrder = sortOrder;
        return attachment;
    }
}
