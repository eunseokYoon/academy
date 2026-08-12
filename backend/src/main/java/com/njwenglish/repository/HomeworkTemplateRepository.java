package com.njwenglish.repository;

import com.njwenglish.entity.HomeworkTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkTemplateRepository extends JpaRepository<HomeworkTemplate, Long> {

    /** 자주 쓰는 숙제가 위로. 사용 횟수가 같으면 최근에 만든 것이 위다. */
    List<HomeworkTemplate> findByTeacherIdOrderByUseCountDescIdDesc(Long teacherId);
}
