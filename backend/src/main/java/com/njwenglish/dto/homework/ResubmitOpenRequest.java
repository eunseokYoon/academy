package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 재제출 요청. <b>dueAt은 필수다(2026-08-24 확정).</b>
 *
 * <p>예전에는 생략하면 서버가 「그 반의 다음 수업일 21:00」으로 잡았다. 그 기본값을 없앤 이유는
 * 화면에서만 필수로 만들면 API를 직접 치는 경로에 기본값이 그대로 남아 규칙이 두 곳으로
 * 갈라지기 때문이다. 마감은 선생님이 정한다.
 */
public record ResubmitOpenRequest(@NotNull OffsetDateTime dueAt) {
}
