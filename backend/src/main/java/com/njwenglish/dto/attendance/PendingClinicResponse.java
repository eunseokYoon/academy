package com.njwenglish.dto.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * T-5 미확정 클리닉 한 줄. 수업(PendingLessonResponse)과 나란히 나간다.
 *
 * <p>수업에는 반 이름이 있지만 클리닉에는 없다. 클리닉은 반과 무관하게 열리는
 * 보충 시간대라 <b>시각이 그 시간대의 이름 역할을 한다.</b> 반 이름을 지어내 채우지 마라.
 *
 * <p>studentCount는 신청 인원이다. 수업의 재원 인원과 뜻이 다르다.
 */
public record PendingClinicResponse(Long clinicId,
                                    LocalDate clinicDate,
                                    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                    long studentCount) {
}
