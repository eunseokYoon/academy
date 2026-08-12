package com.njwenglish.dto.onlinetest;

import java.util.List;

/**
 * 선생님이 학생 한 명의 <b>문항별 정오</b>를 본다. 이걸 보고 성적 기입 탭에 직접 적는다.
 *
 * <p>학생용 결과 DTO(OnlineTestResultResponse)와 분리한다. 응시 화면에는 정답이
 * 절대 나가면 안 되는데, 정답이 들어간 DTO를 재사용하면 실수로 새어 나갈 통로가 생긴다.
 */
public record OnlineTestStudentDetailResponse(
    Long studentId,
    String name,
    Short correctCount,
    Short questionCount,
    Short internalQuestionCount,
    List<QuestionResult> results
) {
    /**
     * chosen이 null이면 미체크다. 오답으로 처리하고 감점은 없다.
     * section은 INTERNAL·EXTERNAL이고, internalQuestionCount가 없으면 null이다.
     */
    public record QuestionResult(int questionNo, Short chosen, Short correct,
                                 boolean isCorrect, String section) {
    }
}
