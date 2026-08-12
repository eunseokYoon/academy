package com.njwenglish.dto.weeklytest;

import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 그리드 한 장 저장. <b>tests에 들어온 종류만 처리한다.</b> 안 들어온 종류는 건드리지 않는다.
 *
 * <p>셀의 값이 전부 null/false면 그 행을 삭제한다. 요청에 없는 학생의 행은 건드리지 않는다.
 *
 * <p>loadedAt은 그리드 응답이 준 서버 시각을 그대로 돌려보낸 것이다.
 * <b>온라인 테스트 자동 반영 때문에 필요하다</b> — 선생님이 화면을 열어 둔 사이 학생이
 * 클리닉 테스트를 내면 서버에는 칸이 생기지만 화면의 그 칸은 비어 있다. 그대로 저장하면
 * 방금 반영된 성적이 조용히 지워진다. 이 값이 있으면 서버가 그 뒤에 바뀐 칸의 삭제만
 * 건너뛴다. 없으면(옛 클라이언트) 기존대로 동작한다.
 */
public record WeeklyTestSaveRequest(
    @NotNull Long classRoomId,
    @NotNull Short year,
    @NotNull @Min(1) @Max(12) Short month,
    @NotNull @Min(1) @Max(5) Short week,
    OffsetDateTime loadedAt,
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
