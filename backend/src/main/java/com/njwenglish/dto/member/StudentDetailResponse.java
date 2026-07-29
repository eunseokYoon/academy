package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.StudentStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-2 상세. 미사용 코드를 함께 내려 선생님이 "다시 알려주기"를 바로 할 수 있게 한다.
 * 이미 가입한 대상의 코드는 목록에 남지 않는다(가입 시 used로 바뀐다).
 */
public record StudentDetailResponse(
    Long studentId,
    String name,
    String memo,
    StudentStatus status,
    LocalDate withdrawnAt,
    OffsetDateTime createdAt,
    String studentPhone,
    boolean studentSignedUp,
    String parentPhone,
    boolean parentLinked,
    String parentName,
    List<EnrolledClassRoom> classRooms,
    List<SignupCodeIssueResponse> signupCodes
) {
    public record EnrolledClassRoom(Long classRoomId, String name, LocalDate joinedAt) {
    }
}
