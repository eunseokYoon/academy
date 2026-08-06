package com.njwenglish.dto.lessonchange;

import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 수업일 변경 화면의 수업 한 칸. 내 수업 목록과 다른 반 후보 목록이 같은 모양을 쓴다.
 *
 * <p><b>수업 내용은 여기 오지 않는다.</b> 제목·영상·레포트·출석 상태 어느 것도 넣지 마라 —
 * 다른 반 수업까지 나가는 목록이라 필드를 하나 늘리는 순간 남의 반 수업 내용이 새어 나간다.
 * 날짜·반 이름·시각까지가 전부다.
 *
 * <p>시각은 lessons가 아니라 반의 요일 슬롯에서 온다. 슬롯이 없으면 null이다 —
 * 값을 지어내면 틀린 시각이 그럴듯하게 보인다.
 */
public record LessonSlotResponse(
    Long lessonId,
    Long classRoomId,
    String classRoomName,
    LocalDate lessonDate,
    LocalTime startTime,
    LocalTime endTime
) {
    public static LessonSlotResponse from(Lesson lesson) {
        ClassRoom classRoom = lesson.getClassRoom();
        ClassRoomSchedule schedule = classRoom.scheduleOn(lesson.getLessonDate()).orElse(null);
        return new LessonSlotResponse(
            lesson.getId(), classRoom.getId(), classRoom.getName(), lesson.getLessonDate(),
            schedule == null ? null : schedule.getStartTime(),
            schedule == null ? null : schedule.getEndTime());
    }
}
