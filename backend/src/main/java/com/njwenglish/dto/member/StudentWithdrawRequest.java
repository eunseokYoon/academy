package com.njwenglish.dto.member;

import java.time.LocalDate;

/** 생략하면 오늘로 처리한다. */
public record StudentWithdrawRequest(LocalDate withdrawnAt) {
}
