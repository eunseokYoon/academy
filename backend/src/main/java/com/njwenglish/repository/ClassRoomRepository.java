package com.njwenglish.repository;

import com.njwenglish.entity.ClassRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {

    Optional<ClassRoom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
