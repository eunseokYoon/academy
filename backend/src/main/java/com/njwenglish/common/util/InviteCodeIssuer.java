package com.njwenglish.common.util;

import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.SignupCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개인 코드(signup_codes.code)와 반 코드(class_rooms.join_code)를 발급하는 한 곳이다.
 *
 * <p>가입 화면은 입력란이 하나뿐이고 서버가 코드를 보고 종류를 판별한다. 두 테이블에
 * 같은 값이 있으면 어느 쪽인지 정할 수 없으므로, 발급할 때 양쪽을 모두 조회해야 한다.
 * 세 곳(반 생성·학생 등록·코드 재발급)에서 쓰기 때문에 여기 하나로 모았다.
 */
@Component
@RequiredArgsConstructor
public class InviteCodeIssuer {

    private final SignupCodeRepository signupCodeRepository;
    private final ClassRoomRepository classRoomRepository;

    public String issue() {
        String code;
        do {
            code = InviteCodes.generate();
        } while (isTaken(code));
        return code;
    }

    private boolean isTaken(String code) {
        return signupCodeRepository.existsByCode(code)
            || classRoomRepository.existsByJoinCode(code);
    }
}
