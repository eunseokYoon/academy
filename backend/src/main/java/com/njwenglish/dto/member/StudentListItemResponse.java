package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.StudentStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * studentSignedUp·parentLinked가 false인 학생을 화면에서 눈에 띄게 표시한다.
 * 200명 중 아직 가입하지 않은 사람을 찾아 코드를 다시 알려주는 것이 선생님의 실제 업무다.
 *
 * <p>createdAt은 students.created_at이다. 반 코드 자가 가입이면 곧 가입 시각이고,
 * sort=recent와 짝을 이뤄 제3자 탐지 경로가 된다.
 *
 * <p>loginId는 내려주지 않는다. 가입 전에는 없고, 가입 후에는 studentPhone과 같은 값이다.
 */
public record StudentListItemResponse(
    Long studentId,
    String name,
    List<String> classRooms,
    String studentPhone,
    boolean studentSignedUp,
    String parentPhone,
    boolean parentLinked,
    StudentStatus status,
    OffsetDateTime createdAt
) {
}
