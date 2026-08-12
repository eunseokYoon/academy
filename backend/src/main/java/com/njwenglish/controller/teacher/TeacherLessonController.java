package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.lesson.LessonBulkCreateRequest;
import com.njwenglish.dto.lesson.LessonBulkCreateResponse;
import com.njwenglish.dto.lesson.LessonCreateRequest;
import com.njwenglish.dto.lesson.LessonDetailResponse;
import com.njwenglish.dto.lesson.LessonListItemResponse;
import com.njwenglish.dto.lesson.LessonUpdateRequest;
import com.njwenglish.service.LessonService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/teacher/lessons")
@RequiredArgsConstructor
public class TeacherLessonController {

    private final LessonService lessonService;

    @GetMapping
    public ApiResponse<List<LessonListItemResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week) {
        return ApiResponse.ok(lessonService.list(classRoomId, from, to, year, month, week));
    }

    @PostMapping
    public ApiResponse<LessonDetailResponse> create(
        @Valid @RequestBody LessonCreateRequest request) {
        return ApiResponse.ok(lessonService.create(request));
    }

    /** 한 학기 수업일을 요일 기준으로 미리 만든다. 이미 있는 날짜는 건너뛴다. */
    @PostMapping("/bulk")
    public ApiResponse<LessonBulkCreateResponse> bulkCreate(
        @Valid @RequestBody LessonBulkCreateRequest request) {
        return ApiResponse.ok(lessonService.bulkCreate(request));
    }

    @GetMapping("/{lessonId}")
    public ApiResponse<LessonDetailResponse> detail(@PathVariable Long lessonId) {
        return ApiResponse.ok(lessonService.detail(lessonId));
    }

    @PatchMapping("/{lessonId}")
    public ApiResponse<LessonDetailResponse> update(@PathVariable Long lessonId,
                                                    @RequestBody LessonUpdateRequest request) {
        return ApiResponse.ok(lessonService.update(lessonId, request));
    }

    @PostMapping("/{lessonId}/publish")
    public ApiResponse<LessonDetailResponse> publish(@PathVariable Long lessonId) {
        return ApiResponse.ok(lessonService.publish(lessonId));
    }

    @DeleteMapping("/{lessonId}")
    public ApiResponse<Void> delete(@PathVariable Long lessonId) {
        lessonService.delete(lessonId);
        return ApiResponse.ok();
    }
}
