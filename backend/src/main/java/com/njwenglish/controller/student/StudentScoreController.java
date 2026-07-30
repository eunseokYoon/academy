package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.score.ScoreChartResponse;
import com.njwenglish.dto.score.StudentExamScheduleResponse;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.service.ExamScheduleService;
import com.njwenglish.service.ScoreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-7. 학부모 응답(P-4)과 형식이 같고 대상이 본인으로 고정된다.
 *
 * <p>학생이 자기 성적을 수정하는 경로를 만들지 마라. 조회만이다.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentScoreController {

    private final ScoreService scoreService;
    private final ExamScheduleService examScheduleService;

    @GetMapping("/scores")
    public ApiResponse<ScoreChartResponse> scores(
        @RequestParam(required = false) ScoreType scoreType) {
        return ApiResponse.ok(scoreService.myScores(scoreType));
    }

    @GetMapping("/exam-schedules")
    public ApiResponse<List<StudentExamScheduleResponse>> examSchedules() {
        return ApiResponse.ok(examScheduleService.mySchedules());
    }
}
