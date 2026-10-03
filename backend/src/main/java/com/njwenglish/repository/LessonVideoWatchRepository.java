package com.njwenglish.repository;

import com.njwenglish.entity.LessonVideoWatch;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonVideoWatchRepository extends JpaRepository<LessonVideoWatch, Long> {

    /**
     * 재생 보고 1단계 — 행이 없으면 빈 비트맵으로 만든다. 이미 있으면 아무것도 안 한다.
     *
     * <p>「찾아서 없으면 save」는 첫 보고 둘(15초 주기 + 멈춤)이 겹치면 둘 다 INSERT해서 한쪽이
     * {@code uq_lesson_video_watches} 위반으로 500이 되고 그 칸이 사라졌다(2026-09-30 리뷰).
     * ON CONFLICT 대상 열은 그 제약과 같아야 한다. 칸 계산은 여기서 하지 않는다 —
     * {@link LessonVideoWatch#markWatched} 한 곳이다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO lesson_video_watches
          (lesson_id, student_id, video_url, bucket_count, watched, updated_at)
        VALUES (:lessonId, :studentId, :videoUrl, :bucketCount,
                decode(repeat('00', (:bucketCount + 7) / 8), 'hex'), now())
        ON CONFLICT (lesson_id, student_id, video_url) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("lessonId") Long lessonId,
                       @Param("studentId") Long studentId,
                       @Param("videoUrl") String videoUrl,
                       @Param("bucketCount") int bucketCount);

    /**
     * 재생 보고 2단계 — 행을 잠그고 읽는다. 겹친 보고는 앞 보고가 커밋할 때까지 기다렸다가
     * <b>칠해진 비트맵</b>을 읽는다. 잠금 없이 읽으면 둘 다 같은 옛 비트맵에 칠하고 나중에
     * 커밋한 쪽이 앞의 칸을 덮는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT w FROM LessonVideoWatch w
        WHERE w.lesson.id = :lessonId AND w.student.id = :studentId AND w.videoUrl = :videoUrl
        """)
    Optional<LessonVideoWatch> findForUpdate(@Param("lessonId") Long lessonId,
                                             @Param("studentId") Long studentId,
                                             @Param("videoUrl") String videoUrl);

    List<LessonVideoWatch> findByLessonId(Long lessonId);

    List<LessonVideoWatch> findByLessonIdAndStudentId(Long lessonId, Long studentId);
}
