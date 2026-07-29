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

    /**
     * 운영이 시작된 반은 지울 수 없다. 하나라도 있으면 409이고 close를 안내한다.
     * 억지로 지우면 학생의 과거 출석·숙제 기록이 함께 사라진다.
     */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM lessons     WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM enrollments WHERE class_room_id = :classRoomId)
            OR EXISTS (SELECT 1 FROM homeworks   WHERE class_room_id = :classRoomId)
        """, nativeQuery = true)
    boolean hasRecords(@Param("classRoomId") Long classRoomId);
}
