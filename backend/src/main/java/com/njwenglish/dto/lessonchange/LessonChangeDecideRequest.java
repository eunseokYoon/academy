package com.njwenglish.dto.lessonchange;

import jakarta.validation.constraints.NotNull;

/**
 * T-13 승인·거절. 승인하면 그 순간 학생·학부모에게 개인 공지가 발행된다.
 * 거절이면 아무에게도 알리지 않는다 — 학생만 자기 목록에서 상태를 본다.
 */
public record LessonChangeDecideRequest(@NotNull Boolean approve) {
}
