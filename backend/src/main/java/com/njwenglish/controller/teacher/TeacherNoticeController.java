package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.material.MaterialUploadUrlRequest;
import com.njwenglish.dto.material.MaterialUploadUrlResponse;
import com.njwenglish.dto.notice.NoticeCreateRequest;
import com.njwenglish.dto.notice.NoticeResponse;
import com.njwenglish.dto.notice.NoticeUpdateRequest;
import com.njwenglish.service.NoticeService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * T-10 공지 관리. 작성 → (수정) → 발행 순서다.
 * 발행 전에는 초안이라 학생·학부모에게 보이지 않는다.
 *
 * <p>여러 반에 같은 공지를 내리려면 반을 다중 선택해 반마다 생성한다.
 */
@RestController
@RequestMapping("/api/teacher/notices")
@RequiredArgsConstructor
public class TeacherNoticeController {

    private final NoticeService noticeService;

    /** 초안까지 전부 내려준다. 선생님이 작성 중인 글을 찾을 곳이 여기뿐이다. */
    @GetMapping
    public ApiResponse<PageResponse<NoticeResponse>> list(
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(noticeService.listForTeacher(pageable));
    }

    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> detail(@PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.detailForTeacher(noticeId));
    }

    @PostMapping
    public ApiResponse<NoticeResponse> create(@Valid @RequestBody NoticeCreateRequest request) {
        return ApiResponse.ok(noticeService.create(request));
    }

    /** 업로드 URL 발급. 파일은 서버를 거치지 않는다 — 클라이언트가 S3로 직접 PUT한다. */
    @PostMapping("/attachments/upload-url")
    public ApiResponse<MaterialUploadUrlResponse> attachmentUploadUrl(
        @Valid @RequestBody MaterialUploadUrlRequest request) {
        return ApiResponse.ok(noticeService.issueAttachmentUploadUrl(request));
    }

    @PatchMapping("/{noticeId}")
    public ApiResponse<NoticeResponse> update(@PathVariable Long noticeId,
                                              @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.ok(noticeService.update(noticeId, request));
    }

    @PostMapping("/{noticeId}/publish")
    public ApiResponse<NoticeResponse> publish(@PathVariable Long noticeId) {
        return ApiResponse.ok(noticeService.publish(noticeId));
    }

    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return ApiResponse.ok();
    }
}
