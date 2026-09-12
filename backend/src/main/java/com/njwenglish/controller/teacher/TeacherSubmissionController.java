package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.homework.SubmissionDetailResponse;
import com.njwenglish.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-7 상세 뷰어. <b>보기 전용이다.</b>
 *
 * <p>확인·피드백 엔드포인트는 2026-08-09에 없앴다. GRID 재제출은 학생이 내는 순간 ⭕가
 * 되므로 선생님이 눌러야 하는 것이 없다. <b>다시 만들지 마라</b> — 강사가 1명이라
 * 200명분 확인 절차가 그대로 병목이 된다.
 *
 * <p>상세 응답의 prevSubmissionId·nextSubmissionId로 목록에 돌아가지 않고 옆 학생의
 * 사진·영상으로 넘어간다.
 */
@RestController
@RequestMapping("/api/teacher/submissions")
@RequiredArgsConstructor
public class TeacherSubmissionController {

    private final SubmissionService submissionService;

    @GetMapping("/{submissionId}")
    public ApiResponse<SubmissionDetailResponse> detail(@PathVariable Long submissionId) {
        return ApiResponse.ok(submissionService.detail(submissionId));
    }

    /**
     * 「미흡」. 그 칸을 ❌로 되돌리고 <b>사진·영상을 지운다.</b> 되돌릴 수 없다 —
     * 화면이 확인 한 단계를 띄운 뒤에 부른다.
     */
    @PostMapping("/{submissionId}/mark-not-done")
    public ApiResponse<Void> markNotDone(@PathVariable Long submissionId) {
        submissionService.markNotDone(submissionId);
        return ApiResponse.ok();
    }
}
