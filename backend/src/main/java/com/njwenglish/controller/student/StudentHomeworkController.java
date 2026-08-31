package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.homework.PhotoRegisterRequest;
import com.njwenglish.dto.homework.PhotoRegisterResponse;
import com.njwenglish.dto.homework.PhotoUploadUrlRequest;
import com.njwenglish.dto.homework.MediaUploadUrlResponse;
import com.njwenglish.dto.homework.StudentHomeworkDetailResponse;
import com.njwenglish.dto.homework.StudentHomeworkListItemResponse;
import com.njwenglish.dto.homework.SubmissionVideoResponse;
import com.njwenglish.dto.homework.SubmitResponse;
import com.njwenglish.dto.homework.VideoRegisterRequest;
import com.njwenglish.dto.homework.VideoUploadUrlRequest;
import com.njwenglish.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-2 · S-3 · S-4.
 *
 * <p>사진은 서버를 거치지 않는다. upload-url로 받은 주소에 클라이언트가 직접 PUT하고,
 * 끝나면 photos로 등록한다. 서버가 바이트를 받는 경로는 없다.
 */
@RestController
@RequestMapping("/api/student/homeworks")
@RequiredArgsConstructor
public class StudentHomeworkController {

    private final SubmissionService submissionService;

    /**
     * year·month는 화면의 달 필터다. 둘 다 있어야 걸린다 — 안 보내면 전부 내려간다.
     *
     * <p>필터를 프론트에서 하지 마라. 한 페이지(20건) 안에서만 걸러져서
     * 지난 달 숙제가 조용히 사라진다.
     */
    @GetMapping
    public ApiResponse<PageResponse<StudentHomeworkListItemResponse>> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(submissionService.myHomeworks(status, year, month, pageable));
    }

    @GetMapping("/{homeworkId}")
    public ApiResponse<StudentHomeworkDetailResponse> detail(@PathVariable Long homeworkId) {
        return ApiResponse.ok(submissionService.myHomework(homeworkId));
    }

    @PostMapping("/{homeworkId}/photos/upload-url")
    public ApiResponse<MediaUploadUrlResponse> uploadUrl(
        @PathVariable Long homeworkId,
        @Valid @RequestBody PhotoUploadUrlRequest request) {
        return ApiResponse.ok(submissionService.issueUploadUrl(homeworkId, request));
    }

    @PostMapping("/{homeworkId}/photos")
    public ApiResponse<PhotoRegisterResponse> registerPhoto(
        @PathVariable Long homeworkId,
        @Valid @RequestBody PhotoRegisterRequest request) {
        return ApiResponse.ok(submissionService.registerPhoto(homeworkId, request));
    }

    @DeleteMapping("/{homeworkId}/photos/{photoId}")
    public ApiResponse<Void> deletePhoto(@PathVariable Long homeworkId,
                                         @PathVariable Long photoId) {
        submissionService.deletePhoto(homeworkId, photoId);
        return ApiResponse.ok(null);
    }

    /**
     * 영상은 제출물당 1개다. 사진과 달리 브라우저 압축이 안 돼 원본이 그대로 올라오므로
     * 100MB 상한이 걸려 있다.
     */
    @PostMapping("/{homeworkId}/video/upload-url")
    public ApiResponse<MediaUploadUrlResponse> videoUploadUrl(
        @PathVariable Long homeworkId,
        @Valid @RequestBody VideoUploadUrlRequest request) {
        return ApiResponse.ok(submissionService.issueVideoUploadUrl(homeworkId, request));
    }

    /** 이미 영상이 있으면 덮어쓴다. 다시 찍어 올리는 건 정상 흐름이다. */
    @PostMapping("/{homeworkId}/video")
    public ApiResponse<SubmissionVideoResponse> registerVideo(
        @PathVariable Long homeworkId,
        @Valid @RequestBody VideoRegisterRequest request) {
        return ApiResponse.ok(submissionService.registerVideo(homeworkId, request));
    }

    @DeleteMapping("/{homeworkId}/video")
    public ApiResponse<Void> deleteVideo(@PathVariable Long homeworkId) {
        submissionService.deleteVideo(homeworkId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{homeworkId}/submit")
    public ApiResponse<SubmitResponse> submit(@PathVariable Long homeworkId) {
        return ApiResponse.ok(submissionService.submit(homeworkId));
    }
}
