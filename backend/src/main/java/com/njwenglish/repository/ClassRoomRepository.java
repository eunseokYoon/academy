package com.njwenglish.repository;

import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.enums.ClassRoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {

    Optional<ClassRoom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    /** 이름 중복은 활성 반끼리만 본다. 종료된 반의 이름은 다시 쓸 수 있다(부분 유니크 인덱스). */
    boolean existsByNameAndStatus(String name, ClassRoomStatus status);

    List<ClassRoom> findByStatusOrderByNameAsc(ClassRoomStatus status);

    /** ACTIVE가 먼저 오도록 status 문자열 순서를 그대로 쓴다 (ACTIVE < CLOSED). */
    List<ClassRoom> findAllByOrderByStatusAscNameAsc();

    List<ClassRoom> findByIdIn(Collection<Long> ids);

    /** T-1 통계: 운영 중인 반 수. */
    long countByStatus(ClassRoomStatus status);

    /**
     * T-1 점검: 가입 코드가 열려 있는 반.
     *
     * <p>학기 중 내내 코드를 열어두는 것이 가장 흔한 사고 경로다. 등록 기간이 끝났는데
     * 0이 아니면 T-3에서 닫아야 한다. <b>0이어도 화면에서 숨기지 마라</b> — 할 일이 아니라
     * 매일 확인하는 점검 항목이다.
     */
    long countByStatusAndJoinCodeActiveTrue(ClassRoomStatus status);

    /**
     * 운영이 시작된 반은 지울 수 없다. 하나라도 있으면 409이고 close를 안내한다.
     * 억지로 지우면 학생의 과거 출석·숙제 기록이 함께 사라진다.
     *
     * <p><b>ON DELETE가 없는(RESTRICT) class_room_id는 전부 여기 있어야 한다</b>(2026-09-30 리뷰).
     * 학생을 받기 전에 시험 일정·반 공지·온라인 테스트·주차 성적 헤더만 만들어 둔 반을 지우면
     * FK 위반 500이 났다. attendances·qna_posts·course_reviews는 수업·배정이 먼저 있어야 생기지만
     * 판정이 다른 테이블의 생성 순서에 기대지 않게 같이 둔다. class_room_schedules는 CASCADE다.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM lessons        WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM enrollments    WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM homeworks      WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM attendances    WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM notices        WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM exam_schedules WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM online_tests   WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM weekly_tests   WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM qna_posts      WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM course_reviews WHERE class_room_id = :classRoomId)
        """, nativeQuery = true)
    boolean hasRecords(@Param("classRoomId") Long classRoomId);
}
