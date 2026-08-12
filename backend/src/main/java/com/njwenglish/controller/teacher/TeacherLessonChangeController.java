package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.lessonchange.LessonChangeDecideRequest;
import com.njwenglish.dto.lessonchange.LessonChangeRequestResponse;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.service.LessonChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-13 수업일 변경 승인. 승인하면 그 순간 학생·학부모에게 개인 공지가 발행된다.
 *
 * <p><b>배정도 수업도 출석도 바뀌지 않는다.</b> 원래 반 출석부에 그 날이 그대로 남으니
 * 출석 확정할 때 선생님이 손으로 처리한다.
 */
@RestController
@RequestMapping("/api/teacher/lesson-change-requests")
@RequiredArgsConstructor
public class TeacherLessonChangeController {

    private final LessonChangeRequestService lessonChangeRequestService;

    /** status를 생략하면 전부다. 화면은 PENDING만 보낸다. */
    @GetMapping
    public ApiResponse<List<LessonChangeRequestResponse>> list(
        @RequestParam(required = false) ChangeRequestStatus status) {
        return ApiResponse.ok(lessonChangeRequestService.listForTeacher(status));
    }

    @PostMapping("/{requestId}/decide")
    public ApiResponse<LessonChangeRequestResponse> decide(
        @PathVariable Long requestId,
        @Valid @RequestBody LessonChangeDecideRequest request) {
        return ApiResponse.ok(lessonChangeRequestService.decide(requestId, request));
    }
}
