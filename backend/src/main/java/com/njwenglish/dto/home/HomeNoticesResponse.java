package com.njwenglish.dto.home;

import com.njwenglish.dto.notice.NoticeSummaryResponse;
import java.util.List;

/**
 * S-1 · P-1 홈의 공지 배너. 학생과 학부모가 <b>같은 모양</b>을 본다 —
 * 두 화면이 어긋나면 "엄마 폰에는 다르게 나온다"는 문의가 된다.
 *
 * <p>totalCount는 그 사람이 볼 수 있는 공지 전체 건수이고, recent는 배너에 펼치는 상단 몇 건이다.
 * 읽음 표시는 범위 밖이라 "안 읽은 수"가 아니라 전체 수다.
 */
public record HomeNoticesResponse(long totalCount, List<NoticeSummaryResponse> recent) {
}
