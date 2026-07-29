package com.njwenglish.dto.classroom;

import com.njwenglish.entity.ClassRoom;

public record JoinCodeResponse(String joinCode, boolean joinCodeActive) {

    public static JoinCodeResponse from(ClassRoom classRoom) {
        return new JoinCodeResponse(classRoom.getJoinCode(), classRoom.isJoinCodeActive());
    }
}
