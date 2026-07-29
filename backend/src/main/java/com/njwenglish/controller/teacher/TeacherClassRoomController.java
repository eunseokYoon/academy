package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.classroom.ClassRoomCreateRequest;
import com.njwenglish.dto.classroom.ClassRoomCreateResponse;
import com.njwenglish.dto.classroom.ClassRoomResponse;
import com.njwenglish.dto.classroom.ClassRoomStudentsResponse;
import com.njwenglish.dto.classroom.ClassRoomUpdateRequest;
import com.njwenglish.dto.classroom.EnrollmentCreateRequest;
import com.njwenglish.dto.classroom.JoinCodeRequest;
import com.njwenglish.dto.classroom.JoinCodeResponse;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.service.ClassRoomService;
import com.njwenglish.service.EnrollmentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/teacher/class-rooms")
@RequiredArgsConstructor
public class TeacherClassRoomController {

    private final ClassRoomService classRoomService;
    private final EnrollmentService enrollmentService;

    @GetMapping
    public ApiResponse<List<ClassRoomResponse>> list(
        @RequestParam(required = false) ClassRoomStatus status) {
        return ApiResponse.ok(classRoomService.list(status));
    }

    @PostMapping
    public ApiResponse<ClassRoomCreateResponse> create(
        @Valid @RequestBody ClassRoomCreateRequest request) {
        return ApiResponse.ok(classRoomService.create(request));
    }

    @GetMapping("/{classRoomId}")
    public ApiResponse<ClassRoomResponse> detail(@PathVariable Long classRoomId) {
        return ApiResponse.ok(classRoomService.detail(classRoomId));
    }

    @PatchMapping("/{classRoomId}")
    public ApiResponse<ClassRoomResponse> update(@PathVariable Long classRoomId,
                                                 @RequestBody ClassRoomUpdateRequest request) {
        return ApiResponse.ok(classRoomService.update(classRoomId, request));
    }

    /** 잘못 만든 반 전용이다. 운영이 시작된 반은 409이고 close를 써야 한다. */
    @DeleteMapping("/{classRoomId}")
    public ApiResponse<Void> delete(@PathVariable Long classRoomId) {
        classRoomService.delete(classRoomId);
        return ApiResponse.ok();
    }

    @PostMapping("/{classRoomId}/close")
    public ApiResponse<ClassRoomResponse> close(@PathVariable Long classRoomId) {
        return ApiResponse.ok(classRoomService.close(classRoomId));
    }

    @PostMapping("/{classRoomId}/join-code")
    public ApiResponse<JoinCodeResponse> changeJoinCode(@PathVariable Long classRoomId,
                                                        @RequestBody JoinCodeRequest request) {
        return ApiResponse.ok(classRoomService.changeJoinCode(classRoomId, request));
    }

    @GetMapping("/{classRoomId}/students")
    public ApiResponse<ClassRoomStudentsResponse> students(
        @PathVariable Long classRoomId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate asOf) {
        return ApiResponse.ok(enrollmentService.students(classRoomId, asOf));
    }

    @PostMapping("/{classRoomId}/students")
    public ApiResponse<ClassRoomStudentsResponse> assign(
        @PathVariable Long classRoomId, @Valid @RequestBody EnrollmentCreateRequest request) {
        return ApiResponse.ok(enrollmentService.assign(classRoomId, request));
    }

    /** 행을 지우지 않고 left_at을 기록한다. */
    @DeleteMapping("/{classRoomId}/students/{studentId}")
    public ApiResponse<Void> unassign(@PathVariable Long classRoomId,
                                      @PathVariable Long studentId) {
        enrollmentService.unassign(classRoomId, studentId);
        return ApiResponse.ok();
    }
}
