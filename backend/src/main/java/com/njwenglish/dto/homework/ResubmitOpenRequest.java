package com.njwenglish.dto.homework;

import java.time.OffsetDateTime;

/**
 * 재제출 요청. dueAt은 <b>선택</b>이다.
 *
 * <p>생략하면 서버가 요청 시점(KST 오늘) 이후 그 반의 가장 가까운 수업일 21:00으로 잡는다.
 * 그 열이 붙은 수업일이 아니라 오늘 기준이다 — 지난 수업 숙제를 뒤늦게 채점하는 경우가 있다.
 */
public record ResubmitOpenRequest(OffsetDateTime dueAt) {
}
