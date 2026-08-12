package com.njwenglish.entity.enums;

/**
 * 온라인 제출 축. 출제 시점에 대상 전원이 NOT_SUBMITTED로 미리 생성된다.
 *
 * <p>채점 축({@link HomeworkResult})과 독립이다. GRID에서 ⭕를 받은 학생은 온라인으로
 * 낼 것이 없어 NOT_SUBMITTED로 남는다 — 정상이다.
 *
 * <p>CHECKED는 V12에서 없앴다. 선생님이 확인해서 올리는 단계가 사라졌기 때문이다.
 * 되살리지 마라 — 제출 여부와 채점 결과 둘로 충분하다.
 */
public enum SubmissionStatus {
    NOT_SUBMITTED, SUBMITTED
}
