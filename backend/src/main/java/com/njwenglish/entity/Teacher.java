package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teachers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Teacher extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 질의응답 게시판을 마지막으로 연 시각. T-1의 「새 질문」이 이 뒤를 센다(V26). */
    @Column(name = "qna_seen_at", nullable = false)
    private OffsetDateTime qnaSeenAt;

    /** 게시판을 열었다. 앞의 시각을 돌려준다 — 목록이 그 뒤의 글에 「새 글」을 붙인다. */
    public OffsetDateTime markQnaSeen(OffsetDateTime now) {
        OffsetDateTime previous = qnaSeenAt;
        qnaSeenAt = now;
        return previous;
    }
}
