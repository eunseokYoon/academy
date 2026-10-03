package com.njwenglish.common.error;

import com.njwenglish.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.DateTimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
            .body(ApiResponse.fail(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED));
    }

    /**
     * 요청 자체가 잘못됐다 — 깨진 JSON·없는 enum 값, {@code ?classRoomId=abc}, 빠진 필수 파라미터,
     * 파라미터 검증 실패, 13월·2월 30일 같은 날짜. 예전에는 전부 아래 handleUnknown이 받아
     * 500과 error 로그가 됐다(2026-09-30 리뷰). 클라이언트 잘못이라 경고로만 남긴다.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        DateTimeException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED));
    }

    /**
     * 경로는 있는데 메서드가 없다. 7-4의 사고(클래스 매핑을 안 보고 쓴 경로)가 이 모양으로
     * 나온다 — 500으로 덮이면 원인을 잘못 짚는다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
            .body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * DB 제약 위반 중 <b>유니크 위반(23505)만</b> 409다 — 더블클릭·동시 요청으로 같은 행이 두 번
     * 들어가려던 경우다(제출 행 생성, 수업일 변경 PENDING 중복, 클리닉 슬롯 등).
     *
     * <p><b>FK 위반(23503)과 CHECK 위반(23514)은 그대로 500이다.</b> 둘은 서버가 미리 검사했어야
     * 하는 것을 놓쳤다는 뜻이다 — FK는 삭제 판정 누락(CLAUDE.md 10-4 「FK 위반을 예외로 잡아
     * 변환하지 마라」), CHECK는 입력 검증 누락이다. 409로 바꾸면 그 누락이 조용히 묻힌다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            log.warn("Unique violation: {}", cause.getMessage());
            return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.getStatus())
                .body(ApiResponse.fail(ErrorCode.DUPLICATE_RESOURCE));
        }
        return handleUnknown(e);
    }

    /**
     * 존재하지 않는 경로. 이 핸들러가 없으면 아래 handleUnknown이 잡아 500으로 응답한다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
            .body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
            .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
