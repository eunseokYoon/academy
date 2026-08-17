package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.qna.QnaAnswerRequest;
import com.njwenglish.dto.qna.QnaDetailResponse;
import com.njwenglish.dto.qna.QnaQuestionUpdateRequest;
import com.njwenglish.dto.qna.QnaSummaryResponse;
import com.njwenglish.dto.qna.QnaUploadUrlRequest;
import com.njwenglish.dto.qna.QnaUploadUrlResponse;
import com.njwenglish.service.QnaService;
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
 * T-15 질의응답. 비공개 질문도 전부 본다.
 *
 * <p><b>질문 작성 엔드포인트가 없다.</b> 반 전체에 알릴 일은 공지(notices)가 한다 —
 * 게시판에도 만들면 학생이 공지를 두 군데서 찾는다(확정).
 *
 * <p>수정은 본인 답글만, 삭제는 전부다. 부적절한 글을 내릴 사람이 선생님뿐이다.
 */
@RestController
@RequestMapping("/api/teacher/qna")
@RequiredArgsConstructor
public class TeacherQnaController {

    private final QnaService qnaService;

    /** classRoomId가 없으면 전체 반이다. */
    @GetMapping
    public ApiResponse<PageResponse<QnaSummaryResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(qnaService.questions(classRoomId, pageable));
    }

    @GetMapping("/{postId}")
    public ApiResponse<QnaDetailResponse> detail(@PathVariable Long postId) {
        return ApiResponse.ok(qnaService.question(postId));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<Long> answer(@PathVariable Long postId,
                                    @Valid @RequestBody QnaAnswerRequest request) {
        return ApiResponse.ok(qnaService.answerAsTeacher(postId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody QnaQuestionUpdateRequest request) {
        qnaService.updateAsTeacher(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        qnaService.deleteAsTeacher(id);
        return ApiResponse.ok();
    }

    @PostMapping("/photos/upload-url")
    public ApiResponse<QnaUploadUrlResponse> uploadUrl(
        @Valid @RequestBody QnaUploadUrlRequest request) {
        return ApiResponse.ok(qnaService.uploadUrl(request));
    }
}
