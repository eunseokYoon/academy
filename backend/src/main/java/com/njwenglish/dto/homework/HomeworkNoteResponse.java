package com.njwenglish.dto.homework;

import java.time.LocalDate;

/**
 * S-2 숙제 탭 맨 위의 「이번 주에 낼 것」. <b>줄 목록이 아니라 선생님이 수업에 적은 글이다.</b>
 *
 * <p>기준은 <b>가장 최근 공개된 수업</b>이다. 숙제는 수업 N에서 내고 수업 N+1까지
 * 해오므로 방금 한 수업에 적힌 것이 지금 할 일이다.
 *
 * <p>여러 반에 속한 학생은 <b>반마다 한 건</b>이다. 반을 합치면 어느 반 숙제인지 모른다.
 *
 * <p><b>수업을 공개하기 전에는 내려가지 않는다.</b> 학생 조회는 전부
 * published_at IS NOT NULL이다. 선생님이 숙제만 적고 공개를 누르지 않으면 이 목록이 빈다.
 */
public record HomeworkNoteResponse(Long lessonId,
                                   LocalDate lessonDate,
                                   String classRoomName,
                                   String weekLabel,
                                   String homeworkNote) {
}
