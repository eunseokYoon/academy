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

    /** CLASS일 때만 값이 있다 (ck_notices_target). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    /**
     * STUDENT일 때만 값이 있다 (ck_notices_target). 그 학생과 학부모만 이 공지를 본다.
     *
     * <p>수업일 변경 승인이 만드는 공지가 여기 해당한다. 본문에 변경 사유가 들어가므로
     * <b>CLASS로 보내면 같은 반 전원에게 사유가 노출된다.</b>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

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

    /**
     * 개인 공지. <b>초안 단계 없이 바로 발행된다</b> — 수업일 변경 승인 시점에 알리는 것이
     * 목적이라 선생님이 발행 버튼을 한 번 더 누를 이유가 없다. 안 누르면 아무에게도 안 간다.
     *
     * <p>pinned는 false 고정이다. 개인 공지가 반 공지 위로 올라가면 목록 정렬이 뒤집힌다.
     */
    public static Notice publishedForStudent(String title, String content, Student student,
                                             Teacher createdBy, OffsetDateTime now) {
        Notice notice = new Notice();
        notice.title = title;
        notice.content = content;
        notice.scope = NoticeScope.STUDENT;
        notice.student = student;
        notice.pinned = false;
        notice.createdBy = createdBy;
        notice.publishedAt = now;
        return notice;
    }

    public void edit(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    /**
     * 대상 변경. scope와 대상은 항상 함께 바뀐다 — 따로 두면 ck_notices_target에 걸린다.
     * ALL·CLASS 사이의 이동만 쓴다. student를 비우는 것은 그래서다.
     */
    public void retarget(NoticeScope scope, ClassRoom classRoom) {
        this.scope = scope;
        this.classRoom = classRoom;
        this.student = null;
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
