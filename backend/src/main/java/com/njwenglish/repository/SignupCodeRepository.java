package com.njwenglish.repository;

import com.njwenglish.entity.SignupCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupCodeRepository extends JpaRepository<SignupCode, Long> {

    Optional<SignupCode> findByCode(String code);

    boolean existsByCode(String code);
}
