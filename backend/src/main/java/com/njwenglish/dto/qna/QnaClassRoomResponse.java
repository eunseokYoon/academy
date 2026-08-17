package com.njwenglish.dto.qna;

/** 글쓰기에서 반을 고르기 위한 목록. 글이 하나도 없는 반도 포함한다. */
public record QnaClassRoomResponse(Long classRoomId, String name) {
}
