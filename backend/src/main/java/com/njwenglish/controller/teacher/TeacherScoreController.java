package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.score.ScoreBulkCreateRequest;
import com.njwenglish.dto.score.ScoreBulkCreateResponse;
import com.njwenglish.dto.score.ScoreCreateRequest;
import com.njwenglish.dto.score.ScoreResponse;
import com.njwenglish.dto.score.ScoreUpdateRequest;
import com.njwenglish.service.ScoreService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-8. 실사용 경로는 bulk다. 200명을 한 명씩 폼으로 저장하게 하면 화면이 죽는다.
 *
 * <p>성적 입력을 엑셀 업로드로 만들지 마라. bulk API + 표 입력 화면으로 충분하다.
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherScoreController {

    private final ScoreService scoreService;

    @GetMapping("/students/{studentId}/scores")
    public ApiResponse<List<ScoreResponse>> list(@PathVariable Long studentId) {
        return ApiResponse.ok(scoreService.teacherScores(studentId));
    }

    @PostMapping("/students/{studentId}/scores")
    public ApiResponse<ScoreResponse> create(@PathVariable Long studentId,
                                             @Valid @RequestBody ScoreCreateRequest request) {
        return ApiResponse.ok(scoreService.create(studentId, request));
    }

    /** 반 명단 표에서 점수만 순서대로 입력한 뒤 한 번에 저장한다. */
    @PostMapping("/scores/bulk")
    public ApiResponse<ScoreBulkCreateResponse> bulkCreate(
        @Valid @RequestBody ScoreBulkCreateRequest request) {
        return ApiResponse.ok(scoreService.bulkCreate(request));
    }

    @PatchMapping("/scores/{scoreId}")
    public ApiResponse<ScoreResponse> update(@PathVariable Long scoreId,
                                             @RequestBody ScoreUpdateRequest request) {
        return ApiResponse.ok(scoreService.update(scoreId, request));
    }

    @DeleteMapping("/scores/{scoreId}")
    public ApiResponse<Void> delete(@PathVariable Long scoreId) {
        scoreService.delete(scoreId);
        return ApiResponse.ok();
    }
}
