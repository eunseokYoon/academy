package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.SignupCodeRepository;
import com.njwenglish.repository.UserRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학부모 계정과 자녀를 잇는다. 학부모는 반 코드를 쓸 수 없어서
 * (어느 학생의 부모인지 알 수 없다) 언제나 개인 코드를 거친다.
 */
@Service
@RequiredArgsConstructor
public class ParentLinkService {

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final SignupCodeRepository signupCodeRepository;
    private final InviteCodeIssuer inviteCodeIssuer;
    private final PasswordEncoder passwordEncoder;

    /**
     * 학부모용 개인 코드 1장을 발급한다. 학생이 반 코드로 가입할 때 자동 호출되고,
     * 선생님이 T-2에서 확인해 학부모에게 전달한다. 이것이 학부모 연결의 시작점이다.
     */
    @Transactional
    public SignupCode issueParentCode(Student student, String normalizedParentPhone) {
        return signupCodeRepository.save(SignupCode.issue(
            student, UserRole.PARENT, inviteCodeIssuer.issue(), normalizedParentPhone,
            OffsetDateTime.now()));
    }

    /**
     * 같은 번호의 PARENT 계정이 이미 있으면 새로 만들지 않고 자녀만 추가로 연결한다(다자녀).
     * 이 분기가 없으면 형제·자매를 둔 학부모가 아이마다 계정을 따로 만들게 된다.
     *
     * <p>기존 계정의 비밀번호는 건드리지 않는다. 0000으로 되돌리면 이미 쓰던 계정이 잠긴다.
     */
    @Transactional
    public Parent linkParent(Student student, String normalizedPhone, String name) {
        Parent parent = userRepository.findByLoginId(normalizedPhone)
            .map(this::existingParentOf)
            .orElseGet(() -> createParent(normalizedPhone, name));

        student.linkParent(parent);
        return parent;
    }

    private Parent existingParentOf(User user) {
        // 같은 번호라도 PARENT면 연결, STUDENT·TEACHER면 오류다
        if (user.getRole() != UserRole.PARENT) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return parentRepository.findByUserId(user.getId())
            .orElseGet(() -> parentRepository.save(Parent.create(user)));
    }

    private Parent createParent(String normalizedPhone, String name) {
        User user = userRepository.save(User.create(UserRole.PARENT, normalizedPhone, name,
            passwordEncoder.encode(User.INITIAL_PASSWORD)));
        return parentRepository.save(Parent.create(user));
    }
}
