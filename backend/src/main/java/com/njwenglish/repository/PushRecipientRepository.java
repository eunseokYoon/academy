package com.njwenglish.repository;

import com.njwenglish.entity.Student;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 푸시 수신자 산출. StudentRepository 와 따로 둔 이유는 거기가 제3자 삭제 판정 같은
 * 무거운 규칙을 들고 있어서다 — 알림 쪽 조회가 섞이면 고칠 때 서로를 건드린다.
 */
public interface PushRecipientRepository extends Repository<Student, Long> {

    /**
     * 학생과 학부모의 계정 id. <b>둘 다 LEFT JOIN 이다</b> — 미가입 학생(user 없음)이나
     * 학부모 없는 학생도 행이 나와야 나머지 한쪽이 받는다. null 은 호출부가 건너뛴다.
     * 이름은 students.name 이다(users.name 이 아니다).
     */
    @Query("""
        SELECT s.id AS studentId, s.name AS studentName,
               u.id AS studentUserId, pu.id AS parentUserId
        FROM Student s
        LEFT JOIN s.user u
        LEFT JOIN s.parent p
        LEFT JOIN p.user pu
        WHERE s.id IN :studentIds
          AND s.status = 'ENROLLED'
        ORDER BY s.name, s.id
        """)
    List<RecipientRow> findRecipients(@Param("studentIds") Collection<Long> studentIds);

    /** 전체 공지(scope = ALL)의 대상. */
    @Query("SELECT s.id FROM Student s WHERE s.status = 'ENROLLED'")
    List<Long> findEnrolledIds();

    interface RecipientRow {
        Long getStudentId();

        String getStudentName();

        Long getStudentUserId();

        Long getParentUserId();
    }
}
