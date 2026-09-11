package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import jakarta.persistence.Column;
import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
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
 * 영상은 {@link LessonVideo}에 여러 줄로 담긴다(2026-09-04). YouTube 링크 문자열만
 * 저장하고 영상 파일은 다루지 않는다.
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

    /**
     * 수업 영상들. 수업당 여러 개다(2026-09-04 확정).
     *
     * <p>전량 교체 방식이라({@link #replaceVideos}) orphanRemoval이 켜져 있다.
     * sort_order에 UNIQUE가 없는 이유는 V22 주석에 있다 — 있으면 flush 순서 때문에
     * 지우고 다시 넣는 순간 제약에 걸린다.
     */
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<LessonVideo> videos = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "key_points", columnDefinition = "TEXT")
    private String keyPoints;

    /**
     * 이번 수업에서 낸 숙제. 학생이 <b>다음 수업까지 해올 것</b>이라, 숙제 탭의
     * 「이번 주에 낼 것」이 이 글을 그대로 보여준다(2026-09-10).
     *
     * <p>예전 이름은 next_preview였다. 실제로 적히던 내용이 숙제였고,
     * 클리닉 안내가 같은 칸에 섞여 있었다.
     */
    @Column(name = "homework_note", columnDefinition = "TEXT")
    private String homeworkNote;

    /** 이번 주 클리닉 안내. 숙제와 한 칸에 섞으면 학생이 골라 읽어야 한다. */
    @Column(name = "clinic_note", columnDefinition = "TEXT")
    private String clinicNote;

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

    /** 수업일만 먼저 만든다. 내용은 비어 있고 PENDING·미공개 상태다. */
    public static Lesson create(ClassRoom classRoom, LocalDate lessonDate,
                                short year, short month, short week) {
        Lesson lesson = new Lesson();
        lesson.classRoom = classRoom;
        lesson.lessonDate = lessonDate;
        lesson.year = year;
        lesson.month = month;
        lesson.week = week;
        lesson.attendanceStatus = LessonAttendanceStatus.PENDING;
        return lesson;
    }

    public void changeWeek(short year, short month, short week) {
        this.year = year;
        this.month = month;
        this.week = week;
    }

    /** 내용 수정은 공개 상태를 건드리지 않는다. 공개는 publish()로만 이루어진다. */
    public void writeContent(String title, String content, String keyPoints,
                             String homeworkNote, String clinicNote) {
        this.title = title;
        this.content = content;
        this.keyPoints = keyPoints;
        this.homeworkNote = homeworkNote;
        this.clinicNote = clinicNote;
    }

    /**
     * 영상 목록을 통째로 갈아 끼운다. 링크별 추가·삭제 API를 따로 두지 않는 이유는
     * 순서 재배열 때문이다 — 화면이 어차피 전체를 들고 있어서 통째로 보내는 게 단순하다.
     *
     * <p>호출부가 URL 유효성과 개수 상한을 이미 검사한 뒤에 부른다.
     */
    public void replaceVideos(List<LessonVideo> next) {
        this.videos.clear();
        this.videos.addAll(next);
    }

    /** 영상이 하나라도 있는가. 목록·레포트가 "영상 있음"을 판정할 때 쓴다. */
    public boolean hasVideo() {
        return !videos.isEmpty();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /**
     * 출석 확정. 재확정도 정상 흐름이라 409를 던지지 않고 확정 시각만 갱신한다.
     * 이 값이 CONFIRMED가 되어야 캘린더가 색을 입힌다.
     */
    public void confirmAttendance(Teacher teacher, OffsetDateTime now) {
        this.attendanceStatus = LessonAttendanceStatus.CONFIRMED;
        this.attendanceConfirmedAt = now;
        this.attendanceConfirmedBy = teacher;
    }

    public boolean isAttendanceConfirmed() {
        return attendanceStatus == LessonAttendanceStatus.CONFIRMED;
    }

    public void publish(OffsetDateTime now) {
        if (publishedAt == null) {
            this.publishedAt = now;
        }
    }
}
