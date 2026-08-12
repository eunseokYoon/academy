package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.lesson.LessonReportListItemResponse;
import com.njwenglish.dto.lesson.LessonReportResponse;
import com.njwenglish.service.LessonReportService;
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
 * S-5. <b>수업 영상은 학생만</b> 본다.
 *
 * <p>레포트(내용·중점·다음 예고·숙제)는 학부모도 본다 —
 * /api/parent/children/{studentId}/lessons가 그 경로다. 영상만 빠진다.
 * <b>시청 기록은 없다</b>(2026-08-11 확정). 재생 시간을 서버에 보고하지 마라.
 */
@RestController
@RequestMapping("/api/student/lessons")
@RequiredArgsConstructor
public class StudentLessonController {

    private final LessonReportService lessonReportService;

    @GetMapping
    public ApiResponse<PageResponse<LessonReportListItemResponse>> list(
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(lessonReportService.myLessons(year, month, week, pageable));
    }

    @GetMapping("/{lessonId}")
    public ApiResponse<LessonReportResponse> detail(@PathVariable Long lessonId) {
        return ApiResponse.ok(lessonReportService.myLesson(lessonId));
    }

    /** 재생 시작(0) · 30초마다 · 이탈 시에만 호출한다. 매초 호출하지 마라. */
}
