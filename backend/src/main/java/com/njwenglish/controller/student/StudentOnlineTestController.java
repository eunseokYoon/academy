package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.onlinetest.OnlineTestAnswerSaveRequest;
import com.njwenglish.dto.onlinetest.OnlineTestResultResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeResponse;
import com.njwenglish.dto.onlinetest.StudentOnlineTestListItemResponse;
import com.njwenglish.service.OnlineTestSubmissionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-10. 응시 경로의 응답에는 <b>정답도 해설지 URL도 없다.</b>
 * 결과는 제출 후에만 내려간다.
 */
@RestController
@RequestMapping("/api/student/online-tests")
@RequiredArgsConstructor
public class StudentOnlineTestController {

    private final OnlineTestSubmissionService onlineTestSubmissionService;

    @GetMapping
    public ApiResponse<List<StudentOnlineTestListItemResponse>> list() {
        return ApiResponse.ok(onlineTestSubmissionService.myTests());
    }

    @GetMapping("/{testId}")
    public ApiResponse<OnlineTestTakeResponse> take(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestSubmissionService.take(testId));
    }

    /** 임시 저장. 답을 고를 때마다 또는 30초마다 호출한다. */
    @PutMapping("/{testId}/answers")
    public ApiResponse<Void> saveAnswers(@PathVariable Long testId,
                                         @Valid @RequestBody OnlineTestAnswerSaveRequest request) {
        onlineTestSubmissionService.saveAnswers(testId, request);
        return ApiResponse.ok();
    }

    /** 본문 없음. 마지막으로 임시 저장한 답안으로 채점한다. */
    @PostMapping("/{testId}/submit")
    public ApiResponse<OnlineTestResultResponse> submit(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestSubmissionService.submit(testId));
    }

    @GetMapping("/{testId}/result")
    public ApiResponse<OnlineTestResultResponse> result(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestSubmissionService.result(testId));
    }
}
