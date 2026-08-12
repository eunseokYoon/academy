package com.njwenglish.dto.weeklytest;

/**
 * 온라인 클리닉 테스트 결과가 성적 기입 탭(T-8)의 클리닉 칸으로 자동 반영되는지,
 * 안 되면 왜 안 되는지 (2026-08-10 확정).
 *
 * <p>판정은 실제로 반영하는 {@code WeeklyTestService.reflectClinicScore}와
 * <b>같은 곳에 산다.</b> 두 벌로 갈라지면 "반영됨"이라고 떠 있는데 칸은 비어 있게 된다.
 */
public enum ClinicReflection {

    /** 반영된다(또는 이미 됐다). */
    REFLECTED,

    /**
     * 출제 시 내부지문 문항 수를 안 넣었거나 내부·외부 한쪽이 0이다.
     * 클리닉 헤더는 내부·외부가 둘 다 1 이상이어야 한다({@code ck_weekly_tests_shape}).
     */
    NO_INTERNAL_SPLIT,

    /**
     * 그 주차 클리닉 헤더의 문항 수와 다르다. 헤더는 <b>반 공통</b>이라 한 명 때문에 덮으면
     * 나머지 학생의 정답률이 통째로 어긋난다.
     */
    TOTAL_MISMATCH
}
