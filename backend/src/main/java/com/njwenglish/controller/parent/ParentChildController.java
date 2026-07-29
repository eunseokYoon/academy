package com.njwenglish.controller.parent;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.service.ParentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parent/children")
@RequiredArgsConstructor
public class ParentChildController {

    private final ParentService parentService;

    @GetMapping
    public ApiResponse<List<ChildResponse>> children() {
        return ApiResponse.ok(parentService.children());
    }
}
