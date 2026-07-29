package com.njwenglish.repository;

import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.StudentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    /** 자녀 목록. 이름은 students.name이다 — user.name으로 정렬하면 미가입 학생이 사라진다. */
    List<Student> findByParentIdAndStatusOrderByNameAsc(Long parentId, StudentStatus status);
}
