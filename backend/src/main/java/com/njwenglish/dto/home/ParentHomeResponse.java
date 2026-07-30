package com.njwenglish.dto.home;

import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.notice.NoticeSummaryResponse;
import com.njwenglish.dto.score.NextExamResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * P-1 학부모 포털 홈. 여러 도메인을 조합하므로 <b>단일 API로 묶어 내려준다.</b>
 *
 * <p><b>학부모는 "자녀가 했는지 여부"만 본다.</b> 수업 제목·내용·영상, 숙제 내용·사진·피드백,
 * 자료실은 이 응답에 넣지 마라 (확정 사항). latestLesson 같은 필드를 추가하지 마라.
 *
 * <p>nextLessonDate가 날짜 하나뿐인 이유가 그것이다. "다음 수업이 언제인지"까지가 전부다.
 *
 * <p>각 값은 null일 수 있다. 시험 일정이 등록 안 됐으면 nextExam은 null이고
 * 프론트가 D-day 카드를 숨긴다. <b>0이나 임의 값을 채우지 마라.</b>
 */
public record ParentHomeResponse(
    ChildRef student,
    NextExamResponse nextExam,
    LocalDate nextLessonDate,
    NoticesBlock notices,
    long pendingHomeworkCount,
    NextClinicResponse nextClinic,
    AttendanceSummaryResponse thisMonthAttendance
) {
    /**
     * 이름은 students.name이다. phone은 <b>서버에서 마스킹한 값</b>이다 (010-****-1234).
     * 원본을 내려주고 프론트에서 가리면 개발자 도구에 그대로 남는다.
     * 미가입 자녀는 users 행이 없어 phone이 null이다.
     */
    public record ChildRef(Long id, String name, String phone, List<String> classRooms) {
    }

    /** totalCount는 목록 전체 건수다. recent는 홈 배너에 펼쳐 보여주는 상단 몇 건이다. */
    public record NoticesBlock(long totalCount, List<NoticeSummaryResponse> recent) {
    }
}
