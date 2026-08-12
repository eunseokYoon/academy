package com.njwenglish.dto.homework;

/**
 * S-4.
 *
 * <p>resubmitRequired는 제출 UI(사진·영상 추가, 제출 버튼)를 여는 <b>유일한 근거</b>다.
 * homework.kind·submission.status·homework.dueAt만으로는 판정할 수 없어 서버가 따로 내려준다.
 * GRID는 제출하는 순간 ⭕가 되어 여기가 false로 바뀌고, 그게 곧 수정 잠금이다.
 */
public record StudentHomeworkDetailResponse(StudentHomeworkResponse homework,
                                            StudentSubmissionResponse submission,
                                            boolean resubmitRequired) {
}
