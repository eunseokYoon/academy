package com.njwenglish.dto.home;

import com.njwenglish.dto.score.NextExamResponse;
import java.util.List;

/**
 * S-1 학생 홈. 여러 도메인을 조합하므로 <b>단일 API로 묶어 내려준다.</b>
 * 프론트에서 5~6개를 병렬 호출하면 카드가 하나씩 튀어나와 로딩이 지저분해진다.
 *
 * <p>nextLesson·nextExam·lastLesson은 null일 수 있다. 프론트는 해당 카드를 숨긴다.
 *
 * <p>lastLesson은 <b>가장 최근에 뭔가 적힌 지난 수업</b>이다. 제목·영상·내용·다음 예고를
 * 담는다 — 홈에서 "언제, 어느 반"까지만 보여주던 규칙이 2026-08-11에 여기까지 넓어졌다.
 *
 * <p>공지는 학부모 홈과 <b>같은 블록</b>을 쓴다. 예전에는 학생만 건수(long)를 받아서
 * 배너를 그릴 수 없었다 — 두 화면이 같은 것을 보여줘야 문의가 안 생긴다.
 * 읽음 표시는 범위 밖이라 totalCount는 전체 건수다.
 */
public record StudentHomeResponse(
    StudentRef student,
    NextLessonResponse nextLesson,
    NextExamResponse nextExam,
    /**
     * 배정받은 다음 클리닉. 없으면 null이다(2026-09-10 추가).
     *
     * <p><b>시각은 시간대 시작이 아니라 이 학생의 도착 시각이다.</b> 클리닉은
     * 17:00~22:00처럼 다섯 시간짜리 시간대이고 학생은 그 안에서 한 시간에 배정된다.
     * 학부모 홈과 같은 DTO·같은 조회를 쓴다 — 두 화면이 다른 시각을 보이면 문의가 생긴다.
     */
    NextClinicResponse nextClinic,
    List<HomeHomeworkResponse> currentHomeworks,
    HomeLessonResponse lastLesson,
    HomeNoticesResponse notices
) {
    /** 이름은 students.name이다. 미가입 학생은 users 행이 없다. */
    public record StudentRef(String name) {
    }
}
