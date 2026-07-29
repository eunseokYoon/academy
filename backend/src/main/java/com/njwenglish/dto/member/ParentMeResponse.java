package com.njwenglish.dto.member;

import java.util.List;

/**
 * P-5 화면용. phone은 본인 번호이고 이 화면에서 직접 수정하므로 마스킹하지 않는다
 * (/auth/me의 phone은 표시 전용이라 마스킹된 값이다).
 */
public record ParentMeResponse(Long id, String name, String phone, List<ChildResponse> children) {
}
