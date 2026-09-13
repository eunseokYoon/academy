package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.OnlineTestSubmission;

/**
 * 화면에 보이는 응시 상태다. NOT_STARTED는 <b>행이 없는 상태</b>라
 * DB enum(OnlineTestStatus)에는 없다 — ck_online_test_sub_status가 두 값만 허용한다.
 *
 * <p>엔티티 enum에 NOT_STARTED를 추가하지 마라. 저장되지 않는 값이 섞이면
 * "행이 없음"과 "NOT_STARTED로 저장됨"이라는 두 가지 표현이 공존하게 된다.
 */
public enum OnlineTestTakeStatus {
    NOT_STARTED, IN_PROGRESS, SUBMITTED,
    /**
     * 오프라인으로 봤다(2026-09-10). 온라인 제출 행이 없지만 그 주차 클리닉 칸에
     * 선생님이 적은 성적이 있는 학생이다.
     *
     * <p>온라인 테스트는 오프라인 테스트의 <b>대체본</b>이라, 반 전체가 종이로 본 주에는
     * 아무도 온라인으로 내지 않는다. 그때 전원이 「미응시」로 뜨는 게 이 값이 생긴 이유다.
     *
     * <p><b>{@code of(submission)}이 이 값을 돌려주지 않는다.</b> 판정 지점이 다르다 —
     * 제출 행이 아니라 weekly_test_scores를 봐야 나온다. DB enum에도 없다.
     */
    OFFLINE;

    public static OnlineTestTakeStatus of(OnlineTestSubmission submission) {
        if (submission == null) {
            return NOT_STARTED;
        }
        return submission.isSubmitted() ? SUBMITTED : IN_PROGRESS;
    }
}
