package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.score.StudentExamScheduleResponse;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.service.ExamScheduleService;
import com.njwenglish.service.StudentScoreQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-7. 학부모 응답(P-4)과 <b>완전히 같은 응답</b>이고 대상만 본인으로 고정된다.
 *
 * <p>학생이 자기 성적을 수정하는 경로를 만들지 마라. 조회만이다.
 * 정기고사는 여기에 내려가지 않는다 — 선생님만 보기로 확정된 데이터다.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentScoreController {

    private final StudentScoreQueryService studentScoreQueryService;
    private final ExamScheduleService examScheduleService;

    @GetMapping("/scores")
    public ApiResponse<StudentScoreResponse> scores() {
        return ApiResponse.ok(studentScoreQueryService.forMe());
    }

    @GetMapping("/exam-schedules")
    public ApiResponse<List<StudentExamScheduleResponse>> examSchedules() {
        return ApiResponse.ok(examScheduleService.mySchedules());
    }
}
