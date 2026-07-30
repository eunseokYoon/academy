package com.njwenglish.common.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 허용 목록이 뚫리면 자료실이 임의 파일 저장소가 된다. HTML은 특히 위험하다.
 */
class MaterialKeysTest {

    private final MaterialKeys keys =
        new MaterialKeys("test-secret-value-for-hmac-signing-0123456789");
    private final LocalDate today = LocalDate.of(2026, 5, 21);

    @Test
    @DisplayName("발급한 키는 같은 선생님에서 통과한다")
    void 발급한_키는_통과한다() {
        String s3Key = keys.issue(1L, "pdf", today);

        assertThat(s3Key).startsWith("materials/2026/05/").endsWith(".pdf");
        assertThat(keys.matches(s3Key, 1L)).isTrue();
    }

    @Test
    @DisplayName("같은 s3Key를 여러 번 대조할 수 있다 — 한 파일을 여러 반에 등록하는 경로다")
    void 같은_키를_여러_번_등록할_수_있다() {
        String s3Key = keys.issue(1L, "pdf", today);

        // 자료 id가 아니라 선생님에게 묶여 있어서 반 수만큼 등록해도 대조가 깨지지 않는다
        assertThat(keys.matches(s3Key, 1L)).isTrue();
        assertThat(keys.matches(s3Key, 1L)).isTrue();
    }

    @Test
    @DisplayName("서명이 다르거나 변조된 키는 거부된다")
    void 변조된_키는_거부된다() {
        String s3Key = keys.issue(1L, "pdf", today);

        assertThat(keys.matches(s3Key, 2L)).isFalse();
        assertThat(keys.matches(s3Key.replace(".pdf", "x.pdf"), 1L)).isFalse();
        assertThat(keys.matches("materials/2026/05/whatever.pdf", 1L)).isFalse();
        assertThat(keys.matches("submissions/2026/05/other-students-photo.jpg", 1L)).isFalse();
        assertThat(keys.matches("../../etc/passwd", 1L)).isFalse();
        assertThat(keys.matches(null, 1L)).isFalse();
    }

    @Test
    @DisplayName("문서·압축·이미지 확장자만 허용한다")
    void 허용_확장자만_통과한다() {
        assertThat(keys.extensionOf("0524_lesson.pdf")).isEqualTo("pdf");
        assertThat(keys.extensionOf("기출.hwp")).isEqualTo("hwp");
        assertThat(keys.extensionOf("기출.hwpx")).isEqualTo("hwpx");
        assertThat(keys.extensionOf("단어장.docx")).isEqualTo("docx");
        assertThat(keys.extensionOf("성적.xlsx")).isEqualTo("xlsx");
        assertThat(keys.extensionOf("수업.pptx")).isEqualTo("pptx");
        assertThat(keys.extensionOf("묶음.zip")).isEqualTo("zip");
        assertThat(keys.extensionOf("칠판.png")).isEqualTo("png");
    }

    @Test
    @DisplayName("실행 가능 확장자는 거부된다 — html은 서빙 시 XSS 경로다")
    void 실행_가능_확장자는_거부된다() {
        assertThat(keys.extensionOf("virus.exe")).isNull();
        assertThat(keys.extensionOf("run.sh")).isNull();
        assertThat(keys.extensionOf("run.bat")).isNull();
        assertThat(keys.extensionOf("payload.js")).isNull();
        assertThat(keys.extensionOf("page.html")).isNull();
        assertThat(keys.extensionOf("확장자없음")).isNull();
        assertThat(keys.extensionOf("점으로끝.")).isNull();
        assertThat(keys.extensionOf(null)).isNull();
    }

    @Test
    @DisplayName("jpeg는 jpg로 접히고 대문자 확장자도 통과한다")
    void jpeg와_대문자를_받아들인다() {
        // 맥·아이폰에서 고른 사진이 .jpeg·.JPG로 오는 일이 흔하다. 여기서 막으면 원인을 알 수 없다
        assertThat(keys.extensionOf("사진.jpeg")).isEqualTo("jpg");
        assertThat(keys.extensionOf("사진.JPG")).isEqualTo("jpg");
        assertThat(keys.extensionOf("자료.PDF")).isEqualTo("pdf");
    }

    @Test
    @DisplayName("확장자마다 presign에 쓸 Content-Type이 정해져 있다")
    void 확장자별_content_type이_있다() {
        assertThat(keys.contentTypeOf("pdf")).isEqualTo("application/pdf");
        assertThat(keys.contentTypeOf("zip")).isEqualTo("application/zip");
        assertThat(keys.contentTypeOf("jpg")).isEqualTo("image/jpeg");
        assertThat(keys.contentTypeOf("exe")).isNull();
    }
}
