package com.njwenglish.dto.weeklytest;

import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 그리드 한 장 저장. <b>tests에 들어온 종류만 처리한다.</b> 안 들어온 종류는 건드리지 않는다.
 *
 * <p>셀의 값이 전부 null/false면 그 행을 삭제한다. 요청에 없는 학생의 행은 건드리지 않는다.
 */
public record WeeklyTestSaveRequest(
    @NotNull Long classRoomId,
    @NotNull Short year,
    @NotNull @Min(1) @Max(12) Short month,
    @NotNull @Min(1) @Max(5) Short week,
    @NotEmpty @Valid List<TestInput> tests
) {
    public record TestInput(
        @NotNull WeeklyTestType testType,
        Short totalCount,
        Short internalTotal,
        Short externalTotal,
        @Valid List<CellInput> cells
    ) {
        public List<CellInput> cellsOrEmpty() {
            return cells == null ? List.of() : cells;
        }

        /** 헤더값이 전부 비었다 — 이 종류를 통째로 지우라는 뜻이다. */
        public boolean hasNoHeader() {
            return totalCount == null && internalTotal == null && externalTotal == null;
        }
    }

    public record CellInput(
        @NotNull Long studentId,
        Short correctCount,
        Short internalCorrect,
        Short externalCorrect,
        TestResult result,
        boolean retestPassed
    ) {
        public boolean isEmpty() {
            return correctCount == null && internalCorrect == null
                && externalCorrect == null && result == null;
        }
    }
}
