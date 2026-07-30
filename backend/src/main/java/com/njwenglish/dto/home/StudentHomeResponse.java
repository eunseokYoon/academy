package com.njwenglish.dto.home;

import com.njwenglish.dto.score.NextExamResponse;
import java.util.List;

/**
 * S-1 학생 홈. 여러 도메인을 조합하므로 <b>단일 API로 묶어 내려준다.</b>
 * 프론트에서 5~6개를 병렬 호출하면 카드가 하나씩 튀어나와 로딩이 지저분해진다.
 *
 * <p>nextLesson·nextExam은 null일 수 있다. 프론트는 해당 카드를 숨긴다.
 *
 * <p>unreadFeedbackCount는 학생이 S-4 상세를 열면 줄어든다
 * (submissions.feedback_read_at). 공지 읽음 표시는 범위 밖이라 noticeCount는 전체 건수다.
 */
public record StudentHomeResponse(
    StudentRef student,
    NextLessonResponse nextLesson,
    NextExamResponse nextExam,
    List<HomeHomeworkResponse> currentHomeworks,
    long unreadFeedbackCount,
    long noticeCount
) {
    /** 이름은 students.name이다. 미가입 학생은 users 행이 없다. */
    public record StudentRef(String name) {
    }
}
