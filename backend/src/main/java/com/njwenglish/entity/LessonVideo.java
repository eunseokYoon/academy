package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
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
 * 수업 영상 한 줄. YouTube 링크 문자열만 담는다 — 파일도 트랜스코딩도 범위 밖이다.
 *
 * <p>수업당 여러 개다(2026-09-04 확정). 재생목록 임베드가 「일부 공개」 목록에서
 * 재생되지 않아 학생이 영상을 못 보던 것을 이 구조가 비켜 간다 — 영상 단독 임베드는
 * 공개 설정에 걸리지 않는다.
 *
 * <p>title은 비어도 된다. 화면이 "영상 1"로 채운다 — 선생님이 매번 이름을 짓게 하면
 * 링크만 붙이고 싶은 날에 걸림돌이 된다.
 */
@Entity
@Table(name = "lesson_videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonVideo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String title;

    /** 정렬용일 뿐이라 UNIQUE가 없다. 중복돼도 동점은 id로 갈린다 — V22 주석을 봐라. */
    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public static LessonVideo of(Lesson lesson, String url, String title, short sortOrder) {
        LessonVideo video = new LessonVideo();
        video.lesson = lesson;
        video.url = url;
        video.title = title;
        video.sortOrder = sortOrder;
        return video;
    }
}
