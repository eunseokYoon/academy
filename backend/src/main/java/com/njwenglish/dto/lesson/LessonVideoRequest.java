package com.njwenglish.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 영상 링크 한 줄. 배열로 받아 <b>통째로 교체</b>한다.
 *
 * <p>title은 비워도 된다 — 화면이 "영상 1"로 채운다. 매번 이름을 짓게 하면
 * 링크만 붙이고 싶은 날에 걸림돌이 된다.
 */
public record LessonVideoRequest(@NotBlank @Size(max = 500) String url,
                                 @Size(max = 100) String title) {
}
