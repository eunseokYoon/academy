package com.njwenglish.dto.member;

import java.util.List;

/** P-5 화면용. phone은 본인 번호라 마스킹하지 않는다 (/auth/me와 같다). */
public record ParentMeResponse(Long id, String name, String phone, List<ChildResponse> children) {
}
