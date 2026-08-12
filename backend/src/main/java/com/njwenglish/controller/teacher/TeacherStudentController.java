package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.member.PasswordResetRequest;
import com.njwenglish.dto.member.PasswordResetResponse;
import com.njwenglish.dto.member.SignupCodeIssueRequest;
import com.njwenglish.dto.member.SignupCodeIssueResponse;
import com.njwenglish.dto.member.StudentCreateRequest;
import com.njwenglish.dto.member.StudentCreateResponse;
import com.njwenglish.dto.member.StudentDeleteResponse;
import com.njwenglish.dto.member.StudentDetailResponse;
import com.njwenglish.dto.member.StudentListItemResponse;
import com.njwenglish.dto.member.StudentRestoreResponse;
import com.njwenglish.dto.member.StudentUpdateRequest;
import com.njwenglish.dto.member.StudentWithdrawRequest;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/students")
@RequiredArgsConstructor
public class TeacherStudentController {

    private final StudentService studentService;

    /** sort=recent가 반 코드 제3자 탐지 경로다. 등록 기간에는 매일 상단만 훑는다. */
    @GetMapping
    public ApiResponse<PageResponse<StudentListItemResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @RequestParam(required = false) StudentStatus status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
            studentService.list(classRoomId, status, keyword, sort, page, size));
    }

    @PostMapping
    public ApiResponse<StudentCreateResponse> create(
        @Valid @RequestBody StudentCreateRequest request) {
        return ApiResponse.ok(studentService.create(request));
    }

    @GetMapping("/{studentId}")
    public ApiResponse<StudentDetailResponse> detail(@PathVariable Long studentId) {
        return ApiResponse.ok(studentService.detail(studentId));
    }

    @PatchMapping("/{studentId}")
    public ApiResponse<StudentDetailResponse> update(@PathVariable Long studentId,
                                                     @RequestBody StudentUpdateRequest request) {
        return ApiResponse.ok(studentService.update(studentId, request));
    }

    @PostMapping("/{studentId}/withdraw")
    public ApiResponse<StudentDetailResponse> withdraw(
        @PathVariable Long studentId,
        @RequestBody(required = false) StudentWithdrawRequest request) {
        return ApiResponse.ok(studentService.withdraw(studentId, request));
    }

    @PostMapping("/{studentId}/restore")
    public ApiResponse<StudentRestoreResponse> restore(@PathVariable Long studentId) {
        return ApiResponse.ok(studentService.restore(studentId));
    }

    /** 반 코드로 들어온 제3자 전용이다. 실제로 다닌 학생은 withdraw다. */
    @DeleteMapping("/{studentId}")
    public ApiResponse<StudentDeleteResponse> delete(@PathVariable Long studentId) {
        return ApiResponse.ok(studentService.delete(studentId));
    }

    @PostMapping("/{studentId}/signup-code")
    public ApiResponse<SignupCodeIssueResponse> issueSignupCode(
        @PathVariable Long studentId, @Valid @RequestBody SignupCodeIssueRequest request) {
        return ApiResponse.ok(studentService.issueSignupCode(studentId, request));
    }

    @PostMapping("/{studentId}/reset-password")
    public ApiResponse<PasswordResetResponse> resetPassword(
        @PathVariable Long studentId,
        @RequestBody(required = false) PasswordResetRequest request) {
        return ApiResponse.ok(studentService.resetPassword(studentId,
            request == null ? new PasswordResetRequest(null, null) : request));
    }
}
