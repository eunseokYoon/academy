package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.weeklytest.WeeklyTestGridResponse;
import com.njwenglish.service.WeeklyTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-8. 반 × 주차 그리드. 종류별로 API를 나누지 마라 — 한 화면에서 4종을 함께 저장한다.
 */
@RestController
@RequestMapping("/api/teacher/weekly-tests")
@RequiredArgsConstructor
public class TeacherWeeklyTestController {

    private final WeeklyTestService weeklyTestService;

    @GetMapping
    public ApiResponse<WeeklyTestGridResponse> grid(@RequestParam Long classRoomId,
                                                    @RequestParam short year,
                                                    @RequestParam short month,
                                                    @RequestParam short week) {
        return ApiResponse.ok(weeklyTestService.grid(classRoomId, year, month, week));
    }
}
