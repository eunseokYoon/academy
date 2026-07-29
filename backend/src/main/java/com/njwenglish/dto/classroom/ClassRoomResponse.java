package com.njwenglish.dto.classroom;

import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.enums.ClassRoomStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 목록·상세 공용. joinCode와 joinCodeActive를 항상 함께 내린다 —
 * 열린 코드를 학기 내내 방치하는 것이 가장 흔한 사고 경로라 화면에서 늘 보여야 한다.
 */
public record ClassRoomResponse(
    Long classRoomId,
    String name,
    Short dayOfWeek,
    LocalTime startTime,
    LocalDate termStart,
    LocalDate termEnd,
    ClassRoomStatus status,
    String joinCode,
    boolean joinCodeActive,
    String memo,
    long studentCount
) {
    public static ClassRoomResponse of(ClassRoom classRoom, long studentCount) {
        return new ClassRoomResponse(
            classRoom.getId(), classRoom.getName(), classRoom.getDayOfWeek(),
            classRoom.getStartTime(), classRoom.getTermStart(), classRoom.getTermEnd(),
            classRoom.getStatus(), classRoom.getJoinCode(), classRoom.isJoinCodeActive(),
            classRoom.getMemo(), studentCount);
    }
}
