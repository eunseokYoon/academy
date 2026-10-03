package com.njwenglish.entity.enums;

/**
 * 클리닉 출석(clinic_reservations.attend_status)도 이 enum을 쓴다.
 *
 * <p>MAKEUP(대체 등원)은 원래 요일에 못 와서 <b>다른 날 수업에 들어온</b> 경우다
 * (2026-09-01 확정). <b>출석으로 친다</b> — 결석 쪽으로 세지 마라.
 * 선생님이 고를 수 있는 화면은 수업 출석(T-5)뿐이다. 클리닉은 「이동」이 같은 일을 한다.
 */
public enum AttendanceStatus {
    PRESENT, LATE, ABSENT, SICK, EXCUSED, MAKEUP,
    /**
     * 온라인(2026-09-29). 수업에 못 오고 영상으로 들었다. <b>출석으로 친다</b>(MAKEUP 과 같은 쪽).
     * 선생님이 T-5 에서 시청률을 보고 직접 고른다 — 시청으로 자동으로 바뀌지 않는다(2026-09-30). 수업에만 있다 —
     * 클리닉에는 영상이 없다. CHECK 제약 두 곳(V28)을 같이 고쳐라.
     */
    ONLINE
}
