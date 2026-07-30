package com.njwenglish.dto.member;

import java.util.List;

/**
 * S-7 상단 학생 정보 카드. 성적은 /student/scores가 따로 내려준다.
 *
 * <p>이름은 <b>students.name</b>이다. users.name이 아니다.
 * 전화번호는 서버에서 마스킹한다.
 *
 * <p>classRooms에 joinCode를 넣지 마라. 학생이 코드를 알면 반 밖으로 퍼진다.
 *
 * <p>parentLinked가 false면 화면에 "보호자 계정이 아직 연결되지 않았습니다"를 띄운다.
 * 학생이 알아야 선생님께 문의해 조치가 된다.
 */
public record StudentMeResponse(
    Long studentId,
    String name,
    List<ClassRoomRef> classRooms,
    String phone,
    boolean parentLinked
) {
    public record ClassRoomRef(Long classRoomId, String name) {
    }
}
