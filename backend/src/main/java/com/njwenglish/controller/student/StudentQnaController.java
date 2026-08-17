package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.qna.QnaAnswerRequest;
import com.njwenglish.dto.qna.QnaClassRoomResponse;
import com.njwenglish.dto.qna.QnaDetailResponse;
import com.njwenglish.dto.qna.QnaQuestionCreateRequest;
import com.njwenglish.dto.qna.QnaQuestionUpdateRequest;
import com.njwenglish.dto.qna.QnaSummaryResponse;
import com.njwenglish.dto.qna.QnaUploadUrlRequest;
import com.njwenglish.dto.qna.QnaUploadUrlResponse;
import com.njwenglish.service.QnaService;
import jakarta.validation.Valid;
import java.util.List;
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
 * S-9 질의응답 게시판. <b>경로가 /api/student/** 라서 학부모 토큰으로 호출하면 403이다.</b>
 * 학부모용 경로를 만들지 마라 — 학부모가 읽는 걸 알면 학생이 묻지 못한다(확정).
 *
 * <p>studentId 파라미터가 없다. 본인 것만 보므로 서비스가 토큰에서 학생을 찾는다.
 *
 * <p>PATCH·DELETE가 질문과 답글에 공통이다. 질문과 답글이 한 테이블이라 가능하다.
 */
@RestController
@RequestMapping("/api/student/qna")
@RequiredArgsConstructor
public class StudentQnaController {

    private final QnaService qnaService;

    /** classRoomId가 없으면 내가 속한 반 전체다. */
    @GetMapping
    public ApiResponse<PageResponse<QnaSummaryResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(qnaService.myQuestions(classRoomId, pageable));
    }

    /**
     * 글쓰기에서 반을 고르기 위한 목록. 반이 하나면 화면이 자동 선택한다.
     *
     * <p><b>{@code /{postId}}보다 위에 둔다.</b> "/class-rooms"는 정적 세그먼트라 원래
     * 우선 매칭되지만, 순서에 기대지 않는다 — 아래 있었다면 숫자 변환 실패로 400이 났을 것이다.
     */
    @GetMapping("/class-rooms")
    public ApiResponse<List<QnaClassRoomResponse>> myClassRooms() {
        return ApiResponse.ok(qnaService.myClassRooms());
    }

    /** 목록에 나오지 않는 postId로 호출하면 403이다. 서비스가 권한을 다시 본다. */
    @GetMapping("/{postId}")
    public ApiResponse<QnaDetailResponse> detail(@PathVariable Long postId) {
        return ApiResponse.ok(qnaService.myQuestion(postId));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody QnaQuestionCreateRequest request) {
        return ApiResponse.ok(qnaService.createQuestion(request));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<Long> answer(@PathVariable Long postId,
                                    @Valid @RequestBody QnaAnswerRequest request) {
        return ApiResponse.ok(qnaService.answerAsStudent(postId, request));
    }

    /** 질문이면 title·isPublic이 필수고, 답글이면 둘 다 있으면 400이다. */
    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody QnaQuestionUpdateRequest request) {
        qnaService.updateAsStudent(id, request);
        return ApiResponse.ok();
    }

    /** 질문을 지우면 답글과 사진이 함께 사라진다(ON DELETE CASCADE). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        qnaService.deleteAsStudent(id);
        return ApiResponse.ok();
    }

    /** 사진은 서버를 거치지 않는다. 여기서 받은 주소에 클라이언트가 직접 PUT한다. */
    @PostMapping("/photos/upload-url")
    public ApiResponse<QnaUploadUrlResponse> uploadUrl(
        @Valid @RequestBody QnaUploadUrlRequest request) {
        return ApiResponse.ok(qnaService.uploadUrl(request));
    }
}
