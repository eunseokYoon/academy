package com.njwenglish.entity.enums;

/**
 * 대상 컬럼과 항상 짝이다 (ck_notices_target).
 *
 * <ul>
 *   <li>ALL — class_room_id, student_id 둘 다 NULL
 *   <li>CLASS — class_room_id NOT NULL
 *   <li>STUDENT — student_id NOT NULL. 그 학생과 학부모만 본다
 * </ul>
 *
 * <p>STUDENT는 수업일 변경 승인이 만든다. 선생님이 손으로 쓰는 공지는 ALL·CLASS 둘뿐이라
 * NoticeService가 STUDENT를 400으로 막는다 — 개인 공지 작성 UI를 만들지 마라.
 */
public enum NoticeScope {
    ALL, CLASS, STUDENT
}
