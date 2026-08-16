package com.njwenglish.dto.qna;

import java.time.OffsetDateTime;
import java.util.List;

public record QnaDetailResponse(
    Long postId,
    String title,
    String authorName,
    Long classRoomId,
    String classRoomName,
    boolean isPublic,
    String content,
    List<QnaPhotoResponse> photos,
    boolean editable,
    OffsetDateTime createdAt,
    List<QnaAnswerResponse> answers) {
}
