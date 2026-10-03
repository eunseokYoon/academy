package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.dto.auth.AccountDeleteRequest;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학생·학부모 본인의 계정 삭제(2026-10-03 사용자 결정). 앱스토어(5.1.1(v))·구글플레이가 앱 안에서
 * 계정을 지울 수 있어야 한다고 요구한다.
 *
 * <p><b>로그인 계정만 지우고 학습 기록은 남긴다.</b> 지우는 것은 {@code users} 행(전화번호 = 아이디)과
 * 리프레시 토큰이고, 기기 토큰·푸시 발송 기록은 FK CASCADE로 함께 사라진다. 학생 행과
 * 출석·숙제·성적은 남아 선생님 화면에 <b>미가입 학생</b>으로 보인다 — 이 시스템은 원래 계정 없는
 * 학생({@code students.user_id = null})을 지원한다. 보관은 처리방침의 「퇴원 후 12개월」을 따른다.
 *
 * <p>학부모가 지우면 학부모 계정과 {@code parents} 행만 사라지고 자녀와의 연결이 풀린다.
 * 자녀의 계정은 건드리지 않는다.
 *
 * <p><b>비밀번호 변경 강제 상태에서는 부를 수 없다</b>({@code PasswordChangeRequiredFilter} 의 허용
 * 목록에 넣지 마라). 초기 비밀번호 {@code 0000}은 모두가 알아서, 넣으면 누구나 남의 학부모 계정을
 * 지울 수 있다. 선생님 계정은 지울 수 없다 — 강사 1명이 사라지면 서비스가 멈춘다.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteMyAccount(AccountDeleteRequest request) {
        User user = userRepository.findById(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        switch (user.getRole()) {
            case STUDENT -> studentRepository.findByUserId(user.getId())
                .ifPresent(Student::unlinkUser);
            case PARENT -> parentRepository.findByUserId(user.getId()).ifPresent(parent -> {
                studentRepository.findByParentId(parent.getId()).forEach(Student::unlinkParent);
                parentRepository.delete(parent);
            });
            default -> throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }

        // revoke 가 아니라 delete — 행이 남으면 FK 때문에 users 를 못 지운다(StudentService.delete 와 같다)
        tokenService.deleteAllOf(user.getId());
        userRepository.delete(user);
    }
}
