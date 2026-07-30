package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.home.TeacherDashboardResponse;
import com.njwenglish.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-1 대시보드. 선생님이 로그인하면 처음 보는 화면이다.
 *
 * <p>매번 호출되지만 캐시를 넣지 마라. 어긋난 숫자는 없는 숫자보다 나쁘다.
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ApiResponse<TeacherDashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }
}
