package com.njwenglish.dto.lesson;

/**
 * 학부모가 보는 수업 영상 시청 현황(2026-09-29). <b>비율(%)은 보내지 않는다</b> — 기기가 보내는 값이라
 * 틀릴 수 있어서, 숫자가 보이면 「85%인데 왜 결석이냐」가 된다. 비율은 선생님 화면에만 있다.
 */
public enum VideoWatchState {
    /** 온라인 기준(80%) 이상 */
    WATCHED,
    /** 조금이라도 봤다 */
    PARTIAL,
    NOT_WATCHED;

    public static VideoWatchState of(int percent) {
        if (percent >= com.njwenglish.service.VideoWatchService.ONLINE_THRESHOLD) {
            return WATCHED;
        }
        return percent > 0 ? PARTIAL : NOT_WATCHED;
    }
}
