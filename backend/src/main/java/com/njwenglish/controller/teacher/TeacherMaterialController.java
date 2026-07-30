package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.material.MaterialCreateRequest;
import com.njwenglish.dto.material.MaterialResponse;
import com.njwenglish.dto.material.MaterialUpdateRequest;
import com.njwenglish.dto.material.MaterialUploadUrlRequest;
import com.njwenglish.dto.material.MaterialUploadUrlResponse;
import com.njwenglish.entity.enums.MaterialCategory;
import com.njwenglish.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-9 주차별 자료실 관리.
 *
 * <p>업로드는 3단계다: upload-url 발급 → 클라이언트가 S3로 직접 PUT → 등록.
 * <b>같은 파일을 여러 반에 주려면 S3에 한 번만 올리고 등록만 반 수만큼 호출한다.</b>
 */
@RestController
@RequestMapping("/api/teacher/materials")
@RequiredArgsConstructor
public class TeacherMaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ApiResponse<PageResponse<MaterialResponse>> list(
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week,
        @RequestParam(required = false) MaterialCategory category,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(materialService.list(year, month, week, category, pageable));
    }

    /** 확장자 허용 목록과 50MB 상한을 여기서 검사한다. exe·html은 거부된다. */
    @PostMapping("/upload-url")
    public ApiResponse<MaterialUploadUrlResponse> uploadUrl(
        @Valid @RequestBody MaterialUploadUrlRequest request) {
        return ApiResponse.ok(materialService.issueUploadUrl(request));
    }

    @PostMapping
    public ApiResponse<MaterialResponse> create(
        @Valid @RequestBody MaterialCreateRequest request) {
        return ApiResponse.ok(materialService.create(request));
    }

    @PatchMapping("/{materialId}")
    public ApiResponse<MaterialResponse> update(@PathVariable Long materialId,
                                                @Valid @RequestBody MaterialUpdateRequest request) {
        return ApiResponse.ok(materialService.update(materialId, request));
    }

    @DeleteMapping("/{materialId}")
    public ApiResponse<Void> delete(@PathVariable Long materialId) {
        materialService.delete(materialId);
        return ApiResponse.ok();
    }
}
