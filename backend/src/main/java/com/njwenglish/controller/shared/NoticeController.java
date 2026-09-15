package com.njwenglish.controller.shared;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.notice.DownloadUrlResponse;
import com.njwenglish.dto.notice.NoticeDetailResponse;
import com.njwenglish.dto.notice.NoticeSummaryResponse;
import com.njwenglish.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생·학부모 공통 공지 조회.
 *
 * <p><b>이 경로는 역할별 접두사가 아니다.</b> SecurityConfig는 로그인만 확인하고
 * 역할을 보지 않는다. 그래서 studentId 검증이 전부 서비스에서 이뤄진다 —
 * 학부모 A가 학부모 B의 자녀 studentId를 넣으면 403이다.
 *
 * <p>studentId는 학부모가 자녀를 지정할 때만 쓴다. 학생 본인은 생략한다.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<PageResponse<NoticeSummaryResponse>> list(
        @RequestParam(required = false) Long studentId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(noticeService.list(studentId, pageable));
    }

    /** 목록과 같은 조건으로 조회한다. 대상이 아닌 공지나 초안은 404다. */
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> detail(
        @PathVariable Long noticeId,
        @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok(noticeService.detail(noticeId, studentId));
    }

    /** 첨부 다운로드. 대상이 아니면 404다 — 목록과 같은 조건으로 다시 확인한다. */
    @GetMapping("/{noticeId}/attachments/{attachmentId}/download-url")
    public ApiResponse<DownloadUrlResponse> attachmentDownloadUrl(
        @PathVariable Long noticeId,
        @PathVariable Long attachmentId,
        @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok(
            noticeService.attachmentDownloadUrl(noticeId, attachmentId, studentId));
    }
}
