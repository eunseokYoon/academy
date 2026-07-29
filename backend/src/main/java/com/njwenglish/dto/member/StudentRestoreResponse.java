package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.StudentStatus;

/**
 * restoredEnrollments는 항상 0이다. 퇴원 후 그 반이 끝났거나 다른 반으로 갈 수 있어
 * 반 배정은 자동 복구하지 않는다. 화면에 "반 배정을 다시 해 주세요"를 띄운다.
 */
public record StudentRestoreResponse(Long studentId, StudentStatus status,
                                     int restoredEnrollments) {
}
