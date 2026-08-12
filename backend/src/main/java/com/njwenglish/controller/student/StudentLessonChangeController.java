package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.lessonchange.LessonChangeCreateRequest;
import com.njwenglish.dto.lessonchange.LessonChangeRequestResponse;
import com.njwenglish.dto.lessonchange.LessonSlotResponse;
import com.njwenglish.service.LessonChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-9 수업일 변경. 클리닉 신청과 같은 화면에 있지만 별개다 —
 * 클리닉은 자리를 예약하고, 이쪽은 <b>알림만</b> 만든다.
 *
 * <p>승인해도 배정도 수업도 바뀌지 않는다. 결과물은 개인 공지 한 건이다.
 */
@RestController
@RequestMapping("/api/student/lesson-changes")
@RequiredArgsConstructor
public class StudentLessonChangeController {

    private final LessonChangeRequestService lessonChangeRequestService;

    /** 못 가는 회차로 고를 수 있는 내 수업. */
    @GetMapping("/my-lessons")
    public ApiResponse<List<LessonSlotResponse>> myLessons() {
        return ApiResponse.ok(lessonChangeRequestService.myLessons());
    }

    /** 대신 갈 수업 후보. fromLessonId가 있는 그 주(월~일)의 다른 반 수업만 나온다. */
    @GetMapping("/candidates")
    public ApiResponse<List<LessonSlotResponse>> candidates(@RequestParam Long fromLessonId) {
        return ApiResponse.ok(lessonChangeRequestService.candidates(fromLessonId));
    }

    @GetMapping
    public ApiResponse<List<LessonChangeRequestResponse>> listMine() {
        return ApiResponse.ok(lessonChangeRequestService.listMine());
    }

    @PostMapping
    public ApiResponse<LessonChangeRequestResponse> create(
        @Valid @RequestBody LessonChangeCreateRequest request) {
        return ApiResponse.ok(lessonChangeRequestService.create(request));
    }
}
