package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import com.njwenglish.entity.enums.NoticeScope;
import java.time.OffsetDateTime;

/**
 * T-10 선생님 목록·상세. <b>초안까지 전부 보인다</b> —
 * publishedAt이 null이면 아직 학생·학부모에게 안 나간 글이다.
 *
 * <p>scope가 STUDENT면 수업일 변경 승인이 만든 개인 공지다. 그 학생과 학부모만 본다.
 * 목록에서 반 공지와 구분되도록 studentName을 함께 내려준다.
 */
public record NoticeResponse(
    Long noticeId,
    String title,
    String content,
    NoticeScope scope,
    Long classRoomId,
    String classRoomName,
    Long studentId,
    String studentName,
    boolean pinned,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
            notice.getId(), notice.getTitle(), notice.getContent(), notice.getScope(),
            notice.getClassRoom() == null ? null : notice.getClassRoom().getId(),
            notice.getClassRoom() == null ? null : notice.getClassRoom().getName(),
            notice.getStudent() == null ? null : notice.getStudent().getId(),
            // 학생 이름은 students.name이다. users.name이 아니다 — 미가입 학생은 users 행이 없다
            notice.getStudent() == null ? null : notice.getStudent().getName(),
            notice.isPinned(), notice.getPublishedAt(), notice.getCreatedAt());
    }
}
