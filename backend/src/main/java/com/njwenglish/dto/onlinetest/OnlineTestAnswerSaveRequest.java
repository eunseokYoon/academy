package com.njwenglish.dto.onlinetest;

import jakarta.validation.constraints.NotNull;

/**
 * 임시 저장. 배열 길이는 questionCount와 같아야 하고 안 푼 문항은 null이다.
 *
 * <p>출석(T-5)과 달리 서버에 저장한다. 25문항을 푸는 데 20~30분이 걸려서
 * 브라우저가 닫히면 처음부터 다시 해야 하기 때문이다.
 * 프론트는 답을 고를 때마다 또는 30초마다 호출한다.
 */
public record OnlineTestAnswerSaveRequest(@NotNull Short[] chosenChoices) {
}
