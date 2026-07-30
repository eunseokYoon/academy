package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.NoticeScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * publishedAt이 null이면 초안이다. 학생·학부모 조회에는
 * published_at IS NOT NULL 조건을 반드시 넣어라. 작성 중인 글이 학부모에게 보이면 안 된다.
 *
 * <p>content는 사용자 입력 텍스트다. 프론트에서 HTML로 렌더링하지 마라.
 */
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeScope scope;

    /** ALL이면 null이어야 한다 (ck_notices_target). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Teacher createdBy;

    /**
     * 초안으로 만들어진다. publishedAt이 채워질 때까지 학생·학부모에게 보이지 않는다.
     *
     * <p>classRoom은 scope와 짝이다. CLASS면 not null, ALL이면 null이어야 한다
     * (ck_notices_target). 호출부에서 검증한 뒤 넘긴다.
     */
    public static Notice draft(String title, String content, NoticeScope scope,
                               ClassRoom classRoom, boolean pinned, Teacher createdBy) {
        Notice notice = new Notice();
        notice.title = title;
        notice.content = content;
        notice.scope = scope;
        notice.classRoom = classRoom;
        notice.pinned = pinned;
        notice.createdBy = createdBy;
        return notice;
    }

    public void edit(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    /** 대상 변경. scope와 classRoom은 항상 함께 바뀐다 — 따로 두면 CHECK 제약에 걸린다. */
    public void retarget(NoticeScope scope, ClassRoom classRoom) {
        this.scope = scope;
        this.classRoom = classRoom;
    }

    /** 이미 발행된 공지를 다시 발행해도 최초 발행 시각을 유지한다. 목록 정렬 기준이라 흔들리면 안 된다. */
    public void publish(OffsetDateTime now) {
        if (publishedAt == null) {
            this.publishedAt = now;
        }
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
