package com.njwenglish.entity.enums;

/**
 * 선생님이 오프라인 채점 후 직접 고른다. 맞힌 개수로 자동 판정하지 마라.
 *
 * <p>"재시험 미통과" 상태는 없다. 재시험을 봤는데 또 떨어지면 선생님이 체크를 안 하므로
 * FAIL + retestPassed=false 그대로 남고 화면에는 "재시험 예정"이 유지된다.
 */
public enum TestResult {
    PASS, FAIL
}
