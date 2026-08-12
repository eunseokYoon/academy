package com.njwenglish.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/**
 * 필터와 Security 핸들러는 GlobalExceptionHandler를 타지 않는다.
 * 그대로 두면 인증 실패만 HTML이나 빈 본문으로 나가서 프론트 처리가 갈린다.
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
        throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }
}
