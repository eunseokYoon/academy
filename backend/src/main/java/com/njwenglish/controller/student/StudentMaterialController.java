package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.material.DownloadUrlResponse;
import com.njwenglish.dto.material.StudentMaterialResponse;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-8 수업 자료실. <b>경로가 /api/student/** 라서 학부모 토큰으로 호출하면 403이다.</b>
 * 자료실을 학부모에게 여는 경로를 만들지 마라 (확정 사항).
 *
 * <p>studentId 파라미터가 없다. 본인 것만 보므로 서비스가 토큰에서 학생을 찾는다.
 */
@RestController
@RequestMapping("/api/student/materials")
@RequiredArgsConstructor
public class StudentMaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ApiResponse<PageResponse<StudentMaterialResponse>> list(
        @RequestParam(required = false) MaterialCategory category,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(materialService.myMaterials(category, pageable));
    }

    /** 목록에 나오지 않는 materialId로 호출하면 403이다. 발급 전에 권한을 다시 본다. */
    @GetMapping("/{materialId}/download-url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@PathVariable Long materialId) {
        return ApiResponse.ok(materialService.myDownloadUrl(materialId));
    }
}
