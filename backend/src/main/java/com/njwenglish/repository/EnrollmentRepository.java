package com.njwenglish.repository;

import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Student;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /**
     * 이후 모든 Phase가 재사용하는 재원생 조회다. students를 직접 조회하지 마라.
     * 기간 조건이 빠지면 퇴원생이 출석부에 남아 결석으로 쌓인다.
     *
     * <p>정렬은 e.student.name이다. e.student.user.name으로 쓰면 nullable 연관을 경로로 타서
     * 암묵적 INNER JOIN이 생기고, 미가입 학생이 명단에서 통째로 사라진다. 에러도 나지 않는다.
     */
    @Query("""
        SELECT e.student FROM Enrollment e
        WHERE e.classRoom.id = :classRoomId
          AND e.joinedAt <= :targetDate
          AND (e.leftAt IS NULL OR e.leftAt > :targetDate)
          AND e.student.status = 'ENROLLED'
        ORDER BY e.student.name
        """)
    List<Student> findActiveStudents(@Param("classRoomId") Long classRoomId,
                                     @Param("targetDate") LocalDate targetDate);

    /** 같은 조건이되 배정일(joinedAt)까지 필요한 화면(T-3 명단)에서 쓴다. */
    @Query("""
        SELECT e FROM Enrollment e JOIN FETCH e.student s
        WHERE e.classRoom.id = :classRoomId
          AND e.joinedAt <= :targetDate
          AND (e.leftAt IS NULL OR e.leftAt > :targetDate)
          AND s.status = 'ENROLLED'
        ORDER BY s.name
        """)
    List<Enrollment> findActiveEnrollments(@Param("classRoomId") Long classRoomId,
                                           @Param("targetDate") LocalDate targetDate);

    @Query("""
        SELECT COUNT(e) FROM Enrollment e
        WHERE e.classRoom.id = :classRoomId
          AND e.leftAt IS NULL
          AND e.student.status = 'ENROLLED'
        """)
    long countActiveStudents(@Param("classRoomId") Long classRoomId);

    /**
     * 특정 날짜 기준 재원 인원. 미확정 수업 목록이 "그날 몇 명이었는지"를 보여줘야 하는데
     * 오늘 기준으로 세면 지난달 수업의 인원이 지금 인원으로 뜬다.
     */
    @Query("""
        SELECT COUNT(e) FROM Enrollment e
        WHERE e.classRoom.id = :classRoomId
          AND e.joinedAt <= :targetDate
          AND (e.leftAt IS NULL OR e.leftAt > :targetDate)
          AND e.student.status = 'ENROLLED'
        """)
    long countActiveStudentsOn(@Param("classRoomId") Long classRoomId,
                               @Param("targetDate") LocalDate targetDate);

    /** 목록 화면에서 학생별 반 이름을 채운다. 한 번에 가져와야 20행 × 1쿼리가 안 된다. */
    @Query("""
        SELECT e FROM Enrollment e JOIN FETCH e.classRoom c
        WHERE e.student.id IN :studentIds AND e.leftAt IS NULL
        ORDER BY c.name
        """)
    List<Enrollment> findActiveByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    List<Enrollment> findByStudentIdAndLeftAtIsNull(Long studentId);

    /**
     * D-day·시험 일정의 대상 반. 퇴원한 반이 섞이면 지난 학기 시험이 D-day로 뜬다.
     * 학생이 여러 반에 속할 수 있어 목록이다.
     */
    @Query("""
        SELECT e.classRoom.id FROM Enrollment e
        WHERE e.student.id = :studentId AND e.leftAt IS NULL
        """)
    List<Long> findActiveClassRoomIds(@Param("studentId") Long studentId);

    long countByStudentId(Long studentId);

    Optional<Enrollment> findByStudentIdAndClassRoomIdAndLeftAtIsNull(Long studentId,
                                                                      Long classRoomId);

    /** 제3자 삭제 시 함께 정리한다. 운영 기록이 없음이 이미 확인된 뒤에만 호출된다. */
    void deleteByStudentId(Long studentId);
}
