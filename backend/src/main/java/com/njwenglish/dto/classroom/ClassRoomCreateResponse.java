package com.njwenglish.dto.classroom;

import com.njwenglish.entity.ClassRoom;

/** 생성 직후 코드를 바로 보여준다. 수업에서 반 전체에 구두로 전달하는 값이다. */
public record ClassRoomCreateResponse(Long classRoomId, String name,
                                      String joinCode, boolean joinCodeActive) {

    public static ClassRoomCreateResponse from(ClassRoom classRoom) {
        return new ClassRoomCreateResponse(classRoom.getId(), classRoom.getName(),
            classRoom.getJoinCode(), classRoom.isJoinCodeActive());
    }
}
