package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 재제출 요청. <b>dueAt은 필수다(2026-08-24 확정).</b>
 *
 * <p>예전에는 생략하면 서버가 「그 반의 다음 수업일 21:00」으로 잡았다. 그 기본값을 없앤 이유는
 * 화면에서만 필수로 만들면 API를 직접 치는 경로에 기본값이 그대로 남아 규칙이 두 곳으로
 * 갈라지기 때문이다. 마감은 선생님이 정한다.
 *
 * <p>title·description은 2026-09-10에 붙었다. 제목 한 줄로는 "무엇을 다시 해와야
 * 하는지"가 안 담긴다. <b>description은 길이 제한이 없다</b> — homeworks.description이
 * TEXT이고, 학생이 목록에서 눌러 모달로 전문을 본다.
 *
 * <p><b>description을 학부모 응답에 넣지 마라.</b> ParentHomeworkResponse가 지키는
 * 규칙이고, 학부모가 사진을 보게 된 뒤에도 그건 그대로다.
 */
public record ResubmitOpenRequest(
    @NotNull OffsetDateTime dueAt,
    @NotBlank @Size(max = 200) String title,
    String description
) {
}
