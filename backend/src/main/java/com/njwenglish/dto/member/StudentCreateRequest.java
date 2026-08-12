package com.njwenglish.dto.member;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 보조 경로다. 주 경로는 반 코드로 학생이 직접 가입하는 것이고, 여기는 폰이 없거나
 * 코드를 못 쓰는 학생을 선생님이 대신 넣어 주는 용도다.
 *
 * <p>loginId·initialPassword는 없다. 가입할 때 입력한 전화번호가 아이디가 되고,
 * 비밀번호는 서버가 0000으로 설정한다.
 */
public record StudentCreateRequest(
    @NotBlank String name,
    @NotBlank String studentPhone,
    @NotBlank String parentPhone,
    String memo,
    List<Long> classRoomIds
) {
}
