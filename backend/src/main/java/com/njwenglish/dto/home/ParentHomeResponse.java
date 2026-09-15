package com.njwenglish.dto.home;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import com.njwenglish.dto.notice.NoticeSummaryResponse;
import com.njwenglish.dto.score.NextExamResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * P-1 학부모 포털 홈. 여러 도메인을 조합하므로 <b>단일 API로 묶어 내려준다.</b>
 *
 * <p><b>홈은 요약이다.</b> 수업 제목·내용, 숙제 사진·피드백, 자료실을 이 응답에 넣지 마라.
 * latestLesson 같은 필드를 추가하지 마라 — 홈이 화면 전체를 대신하기 시작한다.
 *
 * <p>수업 레포트 자체는 학부모도 본다(P-6, /parent/children/{id}/lessons). 영상만 빠진다.
 * 숙제 사진·선생님 피드백과 자료실은 여전히 학생 전용이다.
 *
 * <p>다음 수업이 날짜·시각뿐인 이유가 그것이다. 홈에서는 "언제인지"까지가 전부다.
 * nextLessonTime은 <b>lessons가 아니라 반의 요일 슬롯</b>에서 온다. 그 요일 슬롯이 없으면
 * null이고, 프론트는 시각 없이 날짜만 그린다 — 시각을 지어내 채우지 마라.
 *
 * <p>각 값은 null일 수 있다. 시험 일정이 등록 안 됐으면 nextExam은 null이고
 * 프론트가 D-day 카드를 숨긴다. <b>0이나 임의 값을 채우지 마라.</b>
 */
public record ParentHomeResponse(
    ChildRef student,
    NextExamResponse nextExam,
    LocalDate nextLessonDate,
    @JsonFormat(pattern = "HH:mm") LocalTime nextLessonTime,
    /**
     * 다음 수업까지 며칠. 없으면 null, 오늘이면 0이다(2026-09-10 추가).
     *
     * <p><b>서버가 센다.</b> 프론트가 nextLessonDate로 계산하면 기기 시계에 따라
     * 학생 화면과 하루 어긋난다. NextClinicResponse.dDay와 같은 기준(KST 오늘)이다.
     */
    Integer nextLessonDDay,
    HomeNoticesResponse notices,
    long pendingHomeworkCount,
    NextClinicResponse nextClinic,
    AttendanceSummaryResponse thisMonthAttendance
) {
    /**
     * 이름은 students.name이다. phone은 <b>원본</b>이다 — 보호자가 자기 자녀 번호를
     * 보는 것이라 가리지 않는다. 미가입 자녀는 users 행이 없어 phone이 null이다.
     */
    public record ChildRef(Long id, String name, String phone, List<String> classRooms) {
    }
}
