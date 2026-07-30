package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.lesson.LessonViewRequest;
import com.njwenglish.dto.lesson.StudentLessonDetailResponse;
import com.njwenglish.dto.lesson.StudentLessonListItemResponse;
import com.njwenglish.service.LessonViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-5. 수업 영상과 레포트는 <b>학생만</b> 본다.
 * 같은 내용을 학부모에게 열어 주는 경로를 만들지 마라 (확정 사항).
 */
@RestController
@RequestMapping("/api/student/lessons")
@RequiredArgsConstructor
public class StudentLessonController {

    private final LessonViewService lessonViewService;

    @GetMapping
    public ApiResponse<PageResponse<StudentLessonListItemResponse>> list(
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(lessonViewService.myLessons(year, month, week, pageable));
    }

    @GetMapping("/{lessonId}")
    public ApiResponse<StudentLessonDetailResponse> detail(@PathVariable Long lessonId) {
        return ApiResponse.ok(lessonViewService.myLesson(lessonId));
    }

    /** 재생 시작(0) · 30초마다 · 이탈 시에만 호출한다. 매초 호출하지 마라. */
    @PostMapping("/{lessonId}/view")
    public ApiResponse<Void> view(@PathVariable Long lessonId,
                                  @Valid @RequestBody LessonViewRequest request) {
        lessonViewService.recordView(lessonId, request);
        return ApiResponse.ok();
    }
}
