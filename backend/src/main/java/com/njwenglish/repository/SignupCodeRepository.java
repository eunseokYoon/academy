package com.njwenglish.repository;

import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.enums.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupCodeRepository extends JpaRepository<SignupCode, Long> {

    Optional<SignupCode> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * 번호 선점 검사. users.login_id뿐 아니라 아직 안 쓴 코드의 대조 번호도 봐야 한다.
     * 두 학생에게 같은 번호로 코드를 발급하면 나중 한 명은 가입 단계에서 409를 맞는데,
     * 그때는 원인을 찾기 어렵다.
     */
    boolean existsByPhoneAndUsedAtIsNull(String phone);

    /** 재발급·퇴원 시 폐기할 대상이자, 미가입자의 전화번호를 읽는 곳이다. */
    List<SignupCode> findByStudentIdAndUsedAtIsNull(Long studentId);

    List<SignupCode> findByStudentIdAndTargetRoleAndUsedAtIsNull(Long studentId,
                                                                 UserRole targetRole);

    /** 목록 화면에서 미가입 학생·학부모의 번호를 한 번에 채운다. */
    List<SignupCode> findByStudentIdInAndUsedAtIsNull(Collection<Long> studentIds);

    void deleteByStudentId(Long studentId);
}
