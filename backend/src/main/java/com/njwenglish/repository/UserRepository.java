package com.njwenglish.repository;

import com.njwenglish.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** login_id는 정규화된 전화번호다. 조회 전에 반드시 PhoneNumbers.normalize를 거칠 것. */
    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
