package com.njwenglish.repository;

import com.njwenglish.entity.LessonView;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonViewRepository extends JpaRepository<LessonView, Long> {

    /**
     * 시청 기록. UNIQUE (lesson_id, student_id)가 있으므로 ON CONFLICT로 upsert한다.
     * 30초마다 호출되는 경로라 "있으면 UPDATE 없으면 INSERT"를 코드로 나누면 동시 호출에서 깨진다.
     *
     * <p>first_viewed_at은 DEFAULT now()로 최초 1회만 박히고 갱신 대상에서 빠진다.
     * watch_seconds는 덮어쓰지 않고 <b>누적 합산</b>이다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO lesson_views (lesson_id, student_id, watch_seconds)
        VALUES (:lessonId, :studentId, :watchSeconds)
        ON CONFLICT (lesson_id, student_id) DO UPDATE
        SET last_viewed_at = now(),
            watch_seconds = lesson_views.watch_seconds + EXCLUDED.watch_seconds
        """, nativeQuery = true)
    void upsert(@Param("lessonId") Long lessonId,
                @Param("studentId") Long studentId,
                @Param("watchSeconds") int watchSeconds);

    /** 목록의 viewed 플래그. 20행마다 쿼리를 날리지 않으려고 한 번에 가져온다. */
    @Query("""
        SELECT v.lesson.id FROM LessonView v
        WHERE v.student.id = :studentId AND v.lesson.id IN :lessonIds
        """)
    List<Long> findViewedLessonIds(@Param("studentId") Long studentId,
                                   @Param("lessonIds") Collection<Long> lessonIds);

    @Query("SELECT v FROM LessonView v WHERE v.lesson.id = :lessonId")
    List<LessonView> findByLessonId(@Param("lessonId") Long lessonId);
}
