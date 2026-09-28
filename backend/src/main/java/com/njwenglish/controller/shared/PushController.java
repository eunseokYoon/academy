package com.njwenglish.controller.shared;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.push.DeviceTokenDeleteRequest;
import com.njwenglish.dto.push.DeviceTokenRequest;
import com.njwenglish.dto.push.PushSettingRequest;
import com.njwenglish.dto.push.PushSettingResponse;
import com.njwenglish.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생·학부모 앱 전용. 역할 검사는 SecurityConfig 와 서비스 두 곳에 있다. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final DeviceTokenService deviceTokenService;

    @PutMapping("/devices")
    public ApiResponse<Void> register(@Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/devices")
    public ApiResponse<Void> unregister(@Valid @RequestBody DeviceTokenDeleteRequest request) {
        deviceTokenService.unregister(request.token());
        return ApiResponse.ok();
    }

    @GetMapping("/settings")
    public ApiResponse<PushSettingResponse> setting() {
        return ApiResponse.ok(deviceTokenService.setting());
    }

    @PatchMapping("/settings")
    public ApiResponse<PushSettingResponse> changeSetting(
        @Valid @RequestBody PushSettingRequest request) {
        return ApiResponse.ok(deviceTokenService.changeSetting(request.enabled()));
    }
}
