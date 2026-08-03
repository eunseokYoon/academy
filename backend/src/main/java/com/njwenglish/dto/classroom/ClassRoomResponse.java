package com.njwenglish.dto.classroom;

import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.enums.ClassRoomStatus;
import java.util.Comparator;
import java.util.List;

/**
 * 목록·상세 공용. joinCode와 joinCodeActive를 항상 함께 내린다 —
 * 열린 코드를 학기 내내 방치하는 것이 가장 흔한 사고 경로라 화면에서 늘 보여야 한다.
 *
 * <p>schedules 정렬은 서버가 책임진다(ClassRoom의 @OrderBy). 프론트에서 정렬하면
 * 목록·상세·대시보드에서 순서가 갈린다.
 */
public record ClassRoomResponse(
    Long classRoomId,
    String name,
    List<ClassRoomScheduleDto> schedules,
    ClassRoomStatus status,
    String joinCode,
    boolean joinCodeActive,
    String memo,
    long studentCount
) {
    public static ClassRoomResponse of(ClassRoom classRoom, long studentCount) {
        return new ClassRoomResponse(
            classRoom.getId(), classRoom.getName(),
            // 엔티티의 @OrderBy는 DB에서 읽을 때만 듣는다. 방금 수정한 트랜잭션 안에서는
            // 메모리상 순서가 그대로라 여기서 한 번 맞춘다 — 정렬 책임은 이 한 곳이다.
            classRoom.getSchedules().stream()
                .sorted(Comparator.comparing(ClassRoomSchedule::getDayOfWeek)
                    .thenComparing(ClassRoomSchedule::getStartTime))
                .map(ClassRoomScheduleDto::from).toList(),
            classRoom.getStatus(), classRoom.getJoinCode(), classRoom.isJoinCodeActive(),
            classRoom.getMemo(), studentCount);
    }
}
