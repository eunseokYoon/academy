package com.njwenglish.dto.attendance;

import java.util.List;

/**
 * T-5 "확정할 것" 한 묶음. 선생님이 매일 여는 화면이라 <b>남은 것이 전부 한 번에</b>
 * 보여야 한다 — 수업과 클리닉을 따로 호출하면 한쪽만 로딩된 화면이 잠깐 뜨고,
 * 그 순간 "다 했다"로 읽힌다.
 *
 * <p>둘을 한 배열로 합치지 않은 이유는 확정 API가 다르기 때문이다.
 * 수업은 attendances에 쓰고, 클리닉은 clinic_reservations.attend_status에 쓴다.
 */
public record PendingAttendanceResponse(List<PendingLessonResponse> lessons,
                                        List<PendingClinicResponse> clinics) {
}
