package com.njwenglish.controller.parent;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.member.ParentMeResponse;
import com.njwenglish.dto.member.ParentPhoneUpdateRequest;
import com.njwenglish.service.ParentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parent/me")
@RequiredArgsConstructor
public class ParentMeController {

    private final ParentService parentService;

    @GetMapping
    public ApiResponse<ParentMeResponse> me() {
        return ApiResponse.ok(parentService.me());
    }

    @PatchMapping
    public ApiResponse<ParentMeResponse> changePhone(
        @Valid @RequestBody ParentPhoneUpdateRequest request) {
        return ApiResponse.ok(parentService.changePhone(request));
    }
}
