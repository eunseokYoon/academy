package com.njwenglish.dto.attendance;

import com.njwenglish.dto.clinic.ClinicListItemResponse;
import java.util.List;

/**
 * T-5 주차 조회. 확정 여부와 무관하게 그 주의 수업·클리닉을 함께 내려준다(2026-09-01 확정) —
 * 이미 확정한 출석을 고치려면 들어갈 입구가 필요하다.
 *
 * <p>클리닉은 T-13 목록 DTO를 그대로 쓴다. 같은 주차·같은 확정 판정을 이미 계산하고 있어서
 * 새로 만들면 두 곳이 갈라진다 — attendanceConfirmed의 뜻이 어긋나는 순간
 * 한 화면은 확정이라 하고 다른 화면은 아니라고 한다.
 *
 * <p>둘을 한 배열로 합치지 않는 이유는 {@link PendingAttendanceResponse}와 같다.
 * 확정 API가 다르다 — 수업은 attendances에, 클리닉은 clinic_reservations.attend_status에 쓴다.
 */
public record WeekAttendanceResponse(List<WeekLessonResponse> lessons,
                                     List<ClinicListItemResponse> clinics) {
}
