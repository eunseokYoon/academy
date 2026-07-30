package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.home.StudentHomeResponse;
import com.njwenglish.dto.member.StudentMeResponse;
import com.njwenglish.service.HomeService;
import com.njwenglish.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-1 홈과 S-7 상단 카드.
 *
 * <p>홈은 여러 도메인을 조합하지만 <b>호출은 하나</b>다. 프론트에서 나눠 부르지 마라.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentHomeController {

    private final StudentService studentService;
    private final HomeService homeService;

    /** S-1. currentHomeworks가 화면에서 가장 커야 한다 — 학생이 여는 이유가 그것이다. */
    @GetMapping("/home")
    public ApiResponse<StudentHomeResponse> home() {
        return ApiResponse.ok(homeService.studentHome());
    }

    @GetMapping("/me")
    public ApiResponse<StudentMeResponse> me() {
        return ApiResponse.ok(studentService.me());
    }
}
