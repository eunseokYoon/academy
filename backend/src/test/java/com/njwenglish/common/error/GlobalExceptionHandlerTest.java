package com.njwenglish.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2026-09-30 리뷰: 요청 오류가 전부 handleUnknown으로 떨어져 500·error 로그가 됐다.
 * 실제 컨트롤러 대신 작은 컨트롤러에 이 advice만 붙여 상태 코드를 본다.
 */
class GlobalExceptionHandlerTest {

    record Body(LocalDate date) {
    }

    @RestController
    static class Probe {
        @GetMapping("/items/{id}")
        String item(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/search")
        String search(@RequestParam Long classRoomId) {
            return "ok";
        }

        @PostMapping("/body")
        String body(@RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/date")
        String date(@RequestParam int month) {
            return LocalDate.of(2026, month, 1).toString();
        }

        @GetMapping("/unique")
        String unique() {
            throw new DataIntegrityViolationException("dup",
                new SQLException("duplicate key", "23505"));
        }

        @GetMapping("/fk")
        String fk() {
            throw new DataIntegrityViolationException("fk",
                new SQLException("violates foreign key", "23503"));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
        .setControllerAdvice(new GlobalExceptionHandler())
        // 웹(axios)·앱(dio)은 JSON을 요청한다. 없으면 클래스패스의 XML 변환기가 고른다
        .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
        .build();

    @Test
    @DisplayName("경로 변수 형식이 틀리면 400이다")
    void typeMismatchIs400() throws Exception {
        mvc.perform(get("/items/abc")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 400이다")
    void missingParamIs400() throws Exception {
        mvc.perform(get("/search")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("깨진 JSON이면 400이다")
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"date\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13월 같은 날짜는 400이다")
    void invalidDateIs400() throws Exception {
        mvc.perform(get("/date").param("month", "13")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("있는 경로에 없는 메서드는 405다 — 500으로 덮이면 7-4 사고의 원인을 잘못 짚는다")
    void wrongMethodIs405() throws Exception {
        mvc.perform(delete("/search")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("유니크 위반(더블클릭)은 409다")
    void uniqueViolationIs409() throws Exception {
        mvc.perform(get("/unique")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("FK 위반은 500으로 남긴다 — 삭제 판정 누락을 409로 묻지 않는다(10-4)")
    void foreignKeyViolationStays500() throws Exception {
        mvc.perform(get("/fk")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
