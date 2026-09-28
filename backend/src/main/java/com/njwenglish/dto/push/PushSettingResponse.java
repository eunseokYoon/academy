package com.njwenglish.dto.push;

/** 알림 전체 켜기·끄기. 종류별 토글은 없다. */
public record PushSettingResponse(boolean enabled) {
}
