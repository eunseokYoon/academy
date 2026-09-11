package com.njwenglish.entity.enums;

/**
 * 공지를 누가 보는가. `ck_notices_audience`가 DB에서 같은 세 값을 막는다.
 *
 * <ul>
 *   <li>ALL — 학생과 학부모 둘 다 본다. 기본값
 *   <li>STUDENT_ONLY — 학생만. 학부모 목록·상세·홈 배너에서 빠진다
 *   <li>PARENT_ONLY — 학부모만. 학생 목록·상세·홈 배너에서 빠진다
 * </ul>
 *
 * <p><b>불리언 둘로 쪼개지 마라.</b> students_only와 parents_only가 함께 있으면
 * 둘 다 켜져 아무도 못 보는 공지가 만들어진다 — 그래서 한 컬럼이다.
 *
 * <p><b>scope = STUDENT는 언제나 ALL이다.</b> 수업일·클리닉 변경이 자동 발행하는
 * 개인 공지이고, 학생이나 학부모 한쪽이 못 받으면 알림 자체가 무의미하다.
 * `ck_notices_audience_scope`가 DB에서 막는다.
 */
public enum NoticeAudience {
    ALL, STUDENT_ONLY, PARENT_ONLY
}
