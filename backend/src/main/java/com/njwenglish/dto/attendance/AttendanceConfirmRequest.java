package com.njwenglish.dto.attendance;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 안 온 학생만 담는다. 빈 배열은 "전원 출석"이라는 유효한 요청이다 — 400으로 막지 마라.
 * null도 빈 배열과 같게 다룬다.
 *
 * <p>loadedAt은 명단 응답({@link AttendanceRosterResponse#loadedAt()})을 그대로 돌려보낸
 * 것이다. 화면을 연 뒤 영상 시청으로 온라인이 된 학생을 알아보는 데 쓴다
 * ({@code AttendanceService.confirm}). 옛 화면은 보내지 않는다(null) — 그때는 예전처럼 동작한다.
 */
public record AttendanceConfirmRequest(@Valid List<AttendanceExceptionRequest> exceptions,
                                       OffsetDateTime loadedAt) {

    public List<AttendanceExceptionRequest> exceptionsOrEmpty() {
        return exceptions == null ? List.of() : exceptions;
    }
}
