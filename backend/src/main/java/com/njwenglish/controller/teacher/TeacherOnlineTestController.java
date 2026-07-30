package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.onlinetest.AnswerUploadUrlRequest;
import com.njwenglish.dto.onlinetest.AnswerUploadUrlResponse;
import com.njwenglish.dto.onlinetest.OnlineTestCreateRequest;
import com.njwenglish.dto.onlinetest.OnlineTestCreateResponse;
import com.njwenglish.dto.onlinetest.OnlineTestDetailResponse;
import com.njwenglish.dto.onlinetest.OnlineTestListItemResponse;
import com.njwenglish.dto.onlinetest.OnlineTestResultsResponse;
import com.njwenglish.dto.onlinetest.OnlineTestUpdateRequest;
import com.njwenglish.service.OnlineTestService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-14. 정답이 오가는 경로라 전부 TEACHER 전용이다.
 * 여기 DTO를 학생 컨트롤러에서 재사용하지 마라.
 */
@RestController
@RequestMapping("/api/teacher/online-tests")
@RequiredArgsConstructor
public class TeacherOnlineTestController {

    private final OnlineTestService onlineTestService;

    @GetMapping
    public ApiResponse<List<OnlineTestListItemResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week) {
        return ApiResponse.ok(onlineTestService.list(classRoomId, year, month, week));
    }

    @PostMapping
    public ApiResponse<OnlineTestCreateResponse> create(
        @Valid @RequestBody OnlineTestCreateRequest request) {
        return ApiResponse.ok(onlineTestService.create(request));
    }

    /** 해설지 업로드 URL. 파일을 먼저 올리고 받은 s3Key로 출제한다. */
    @PostMapping("/upload-url")
    public ApiResponse<AnswerUploadUrlResponse> uploadUrl(
        @Valid @RequestBody AnswerUploadUrlRequest request) {
        return ApiResponse.ok(onlineTestService.issueAnswerUploadUrl(request));
    }

    @GetMapping("/{testId}")
    public ApiResponse<OnlineTestDetailResponse> detail(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestService.detail(testId));
    }

    @PatchMapping("/{testId}")
    public ApiResponse<OnlineTestDetailResponse> update(@PathVariable Long testId,
                                                        @RequestBody
                                                        OnlineTestUpdateRequest request) {
        return ApiResponse.ok(onlineTestService.update(testId, request));
    }

    @PostMapping("/{testId}/publish")
    public ApiResponse<OnlineTestDetailResponse> publish(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestService.publish(testId));
    }

    /** 제출이 1건이라도 있으면 409다. */
    @DeleteMapping("/{testId}")
    public ApiResponse<Void> delete(@PathVariable Long testId) {
        onlineTestService.delete(testId);
        return ApiResponse.ok();
    }

    /** 미응시 학생도 포함된다. */
    @GetMapping("/{testId}/results")
    public ApiResponse<OnlineTestResultsResponse> results(@PathVariable Long testId) {
        return ApiResponse.ok(onlineTestService.results(testId));
    }
}
