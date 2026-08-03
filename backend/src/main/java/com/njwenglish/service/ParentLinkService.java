package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학부모 계정과 자녀를 잇는다.
 *
 * <p>학부모용 가입 코드는 없다. 학생이 반 코드로 가입하거나 선생님이 직접 등록하는 시점에
 * 보호자 번호로 계정이 바로 만들어진다. 학부모는 그 번호와 초기 비밀번호 0000으로 로그인한다.
 */
@Service
@RequiredArgsConstructor
public class ParentLinkService {

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 같은 번호의 PARENT 계정이 이미 있으면 새로 만들지 않고 자녀만 추가로 연결한다(다자녀).
     * 이 분기가 없으면 형제·자매를 둔 학부모가 아이마다 계정을 따로 만들게 된다.
     *
     * <p>기존 계정의 비밀번호는 건드리지 않는다. 0000으로 되돌리면 이미 쓰던 계정이 잠긴다.
     *
     * @param name 새로 만들 때만 쓰인다. 이미 계정이 있으면 무시된다.
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
