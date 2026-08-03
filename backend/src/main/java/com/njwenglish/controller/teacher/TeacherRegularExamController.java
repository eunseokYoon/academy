package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.regularexam.RegularExamGridResponse;
import com.njwenglish.dto.regularexam.RegularExamSaveRequest;
import com.njwenglish.service.RegularExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정기고사. <b>/api/teacher 아래에만 존재한다.</b>
 * 학생·학부모용 대응 엔드포인트를 만들지 마라 — 노출하지 않기로 확정된 데이터다.
 */
@RestController
@RequestMapping("/api/teacher/regular-exams")
@RequiredArgsConstructor
public class TeacherRegularExamController {

    private final RegularExamService regularExamService;

    @GetMapping
    public ApiResponse<RegularExamGridResponse> grid(@RequestParam Long classRoomId,
                                                     @RequestParam short year) {
        return ApiResponse.ok(regularExamService.grid(classRoomId, year));
    }

    @PutMapping
    public ApiResponse<Void> save(@Valid @RequestBody RegularExamSaveRequest request) {
        regularExamService.save(request);
        return ApiResponse.ok();
    }
}
