package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.homework.HomeworkCreateRequest;
import com.njwenglish.dto.homework.HomeworkCreateResponse;
import com.njwenglish.dto.homework.HomeworkDetailResponse;
import com.njwenglish.dto.homework.HomeworkListItemResponse;
import com.njwenglish.dto.homework.HomeworkSubmissionsResponse;
import com.njwenglish.dto.homework.HomeworkTemplateCreateRequest;
import com.njwenglish.dto.homework.HomeworkTemplateResponse;
import com.njwenglish.dto.homework.HomeworkUpdateRequest;
import com.njwenglish.dto.homework.PendingHomeworkResponse;
import com.njwenglish.service.HomeworkService;
import com.njwenglish.service.HomeworkTemplateService;
import com.njwenglish.service.SubmissionService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** T-6 숙제 출제·관리와 숙제 템플릿. */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherHomeworkController {

    private final HomeworkService homeworkService;
    private final HomeworkTemplateService templateService;
    private final SubmissionService submissionService;

    // ---------- 템플릿 ----------

    @GetMapping("/homework-templates")
    public ApiResponse<List<HomeworkTemplateResponse>> templates() {
        return ApiResponse.ok(templateService.list());
    }

    @PostMapping("/homework-templates")
    public ApiResponse<HomeworkTemplateResponse> createTemplate(
        @Valid @RequestBody HomeworkTemplateCreateRequest request) {
        return ApiResponse.ok(templateService.create(request));
    }

    @DeleteMapping("/homework-templates/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.delete(templateId);
        return ApiResponse.ok(null);
    }

    // ---------- 숙제 ----------

    @GetMapping("/homeworks")
    public ApiResponse<PageResponse<HomeworkListItemResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(homeworkService.list(classRoomId, from, to, pageable));
    }

    @PostMapping("/homeworks")
    public ApiResponse<HomeworkCreateResponse> create(
        @Valid @RequestBody HomeworkCreateRequest request) {
        return ApiResponse.ok(homeworkService.create(request));
    }

    /** 경로가 /homeworks/{homeworkId}와 겹치지 않도록 상세보다 먼저 선언한다. */
    @GetMapping("/homeworks/pending")
    public ApiResponse<List<PendingHomeworkResponse>> pending() {
        return ApiResponse.ok(homeworkService.pending());
    }

    @GetMapping("/homeworks/{homeworkId}")
    public ApiResponse<HomeworkDetailResponse> detail(@PathVariable Long homeworkId) {
        return ApiResponse.ok(homeworkService.detail(homeworkId));
    }

    @PatchMapping("/homeworks/{homeworkId}")
    public ApiResponse<HomeworkDetailResponse> update(
        @PathVariable Long homeworkId,
        @Valid @RequestBody HomeworkUpdateRequest request) {
        return ApiResponse.ok(homeworkService.update(homeworkId, request));
    }

    @DeleteMapping("/homeworks/{homeworkId}")
    public ApiResponse<Void> delete(@PathVariable Long homeworkId) {
        homeworkService.delete(homeworkId);
        return ApiResponse.ok(null);
    }

    /** T-7. 반 단위라 페이징이 없다. */
    @GetMapping("/homeworks/{homeworkId}/submissions")
    public ApiResponse<HomeworkSubmissionsResponse> submissions(@PathVariable Long homeworkId) {
        return ApiResponse.ok(submissionService.submissionsOf(homeworkId));
    }
}
