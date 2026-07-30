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
    NOT_STARTED, IN_PROGRESS, SUBMITTED;

    public static OnlineTestTakeStatus of(OnlineTestSubmission submission) {
        if (submission == null) {
            return NOT_STARTED;
        }
        return submission.isSubmitted() ? SUBMITTED : IN_PROGRESS;
    }
}
