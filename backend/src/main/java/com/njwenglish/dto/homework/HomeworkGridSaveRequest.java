package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.HomeworkResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 그리드 한 장 통째로 저장. 성적 그리드(T-8)와 같은 패턴이다.
 *
 * <p><b>배열에서 빠진 열을 지우지 않는다.</b> 삭제는 DELETE /teacher/homeworks/{id} 하나뿐이다.
 * PUT이 열을 지우면 통신이 끊긴 저장 한 번에 학생 제출물이 날아간다.
 */
public record HomeworkGridSaveRequest(@NotNull Long lessonId,
                                      @NotNull @Valid List<Column> columns) {

    /** homeworkId가 null이면 새 열이다. 저장하면서 대상 전원의 칸을 미리 깐다. */
    public record Column(Long homeworkId,
                         @NotBlank @Size(max = 200) String title,
                         @NotNull Short sortOrder,
                         @NotNull @Valid List<Cell> cells) {
    }

    /**
     * 칸 하나. result가 null이면 "미채점으로 되돌린다"는 뜻이고, 행은 남는다.
     * 퍼센트는 PARTIAL에만 붙는다 — 다른 결과와 함께 와도 서버가 버린다.
     */
    public record Cell(@NotNull Long studentId,
                       HomeworkResult result,
                       @Min(1) @Max(99) Short completionRate) {
    }
}
