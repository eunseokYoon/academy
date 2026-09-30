package com.njwenglish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생 한 명이 수업 영상 한 편을 어디까지 봤나(V28, 2026-09-29).
 *
 * <p><b>재생 위치가 실제로 지나간 10초 칸만 칠한다.</b> V14 에서 없앤 lesson_views 는 화면을 열어
 * 두면 30초마다 「봤다」를 보내는 타이머라 값이 맞은 적이 없었다. 여기는 플레이어의 재생 위치를
 * 칸으로 바꿔 보내므로 건너뛴 구간·멈춰 둔 시간은 안 쌓이고, 다시 봐도 두 번 세지 않는다.
 * 그래도 학생 기기가 보내는 값이다 — 소리를 끄고 틀어 두는 것은 막지 못한다.
 *
 * <p>영상은 URL 로 묶는다 — {@link Lesson#replaceVideos} 가 저장할 때마다 영상 행을 새로 만든다.
 */
@Entity
@Table(name = "lesson_video_watches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonVideoWatch {

    /** 칸 하나의 길이(초). 바꾸면 이미 쌓인 비트맵의 뜻이 달라진다. */
    public static final int BUCKET_SECONDS = 10;

    /** 6시간. ck_lesson_video_watches_buckets 와 같다. */
    public static final int MAX_BUCKETS = 2160;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "bucket_count", nullable = false)
    private short bucketCount;

    /** 칸 하나가 1비트. i 번째 칸 = watched[i / 8] 의 (i % 8) 번째 비트. */
    @Column(name = "watched", nullable = false)
    private byte[] watched;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static LessonVideoWatch start(Lesson lesson, Student student, String videoUrl,
                                         int bucketCount, OffsetDateTime now) {
        LessonVideoWatch watch = new LessonVideoWatch();
        watch.lesson = lesson;
        watch.student = student;
        watch.videoUrl = videoUrl;
        watch.bucketCount = (short) bucketCount;
        watch.watched = new byte[(bucketCount + 7) / 8];
        watch.updatedAt = now;
        return watch;
    }

    /** 길이(초)에서 칸 수. 1~{@link #MAX_BUCKETS} 로 자른다. */
    public static int bucketsOf(double durationSeconds) {
        int buckets = (int) Math.ceil(durationSeconds / BUCKET_SECONDS);
        return Math.max(1, Math.min(MAX_BUCKETS, buckets));
    }

    /**
     * 본 칸을 더한다. 범위 밖 번호는 버린다(기기가 보내는 값이다).
     * 영상 길이가 늘었으면(다른 기기가 조금 다르게 재면) 칸 수를 키운다 — 줄이지는 않는다.
     */
    public void markWatched(Collection<Integer> buckets, int reportedBucketCount,
                            OffsetDateTime now) {
        if (reportedBucketCount > bucketCount) {
            bucketCount = (short) Math.min(MAX_BUCKETS, reportedBucketCount);
            watched = Arrays.copyOf(watched, (bucketCount + 7) / 8);
        }
        for (Integer bucket : buckets) {
            if (bucket == null || bucket < 0 || bucket >= bucketCount) {
                continue;
            }
            watched[bucket / 8] |= (byte) (1 << (bucket % 8));
        }
        updatedAt = now;
    }

    /** 본 비율(0~100, 내림). */
    public int percent() {
        int count = 0;
        for (int i = 0; i < bucketCount; i++) {
            if ((watched[i / 8] & (1 << (i % 8))) != 0) {
                count++;
            }
        }
        return count * 100 / bucketCount;
    }
}
