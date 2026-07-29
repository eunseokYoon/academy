package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * videoUrl은 YouTube 미등록 링크 문자열만 저장한다. 영상 파일은 다루지 않는다.
 *
 * <p>publishedAt이 null이면 작성 중이라 선생님만 본다. 학생·학부모 조회에는
 * published_at IS NOT NULL 조건을 반드시 넣어라.
 */
@Entity
@Table(name = "lessons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    /** 주차는 계산하지 말고 선생님이 화면에서 고른 값을 그대로 저장한다. */
    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "week", nullable = false)
    private Short week;

    @Column(length = 200)
    private String title;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "key_points", columnDefinition = "TEXT")
    private String keyPoints;

    @Column(name = "next_preview", columnDefinition = "TEXT")
    private String nextPreview;

    /** PENDING인 날은 출석이 아니라 미확인이다. 집계에 넣지 마라. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private LessonAttendanceStatus attendanceStatus;

    @Column(name = "attendance_confirmed_at")
    private OffsetDateTime attendanceConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_confirmed_by")
    private Teacher attendanceConfirmedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
