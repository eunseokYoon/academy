package com.njwenglish.repository;

import com.njwenglish.entity.LessonVideoWatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonVideoWatchRepository extends JpaRepository<LessonVideoWatch, Long> {

    Optional<LessonVideoWatch> findByLessonIdAndStudentIdAndVideoUrl(Long lessonId,
                                                                     Long studentId,
                                                                     String videoUrl);

    List<LessonVideoWatch> findByLessonId(Long lessonId);

    List<LessonVideoWatch> findByLessonIdAndStudentId(Long lessonId, Long studentId);
}
