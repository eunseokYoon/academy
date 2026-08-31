package com.njwenglish.controller.parent;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.dto.clinic.ParentClinicResponse;
import com.njwenglish.dto.home.ParentHomeResponse;
import com.njwenglish.dto.homework.ParentHomeworkResponse;
import com.njwenglish.dto.lesson.LessonReportListItemResponse;
import com.njwenglish.dto.lesson.LessonReportResponse;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.dto.score.StudentExamScheduleResponse;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.service.AttendanceService;
import com.njwenglish.service.ClinicService;
import com.njwenglish.service.ExamScheduleService;
import com.njwenglish.service.HomeService;
import com.njwenglish.service.LessonReportService;
import com.njwenglish.service.ParentService;
import com.njwenglish.service.StudentScoreQueryService;
import com.njwenglish.service.SubmissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학부모는 <b>조회만</b> 한다. 클리닉 신청·취소·변경 경로를 여기에 만들지 마라.
 *
 * <p>studentId 소유권은 서비스의 StudentAccessGuard가 검증한다.
 * 컨트롤러에서 따로 검사하지 않는다 — 두 곳에 두면 한 곳을 빠뜨린다.
 */
@RestController
@RequestMapping("/api/parent/children")
@RequiredArgsConstructor
public class ParentChildController {

    private final ParentService parentService;
    private final HomeService homeService;
    private final AttendanceService attendanceService;
    private final ClinicService clinicService;
    private final SubmissionService submissionService;
    private final LessonReportService lessonReportService;
    private final StudentScoreQueryService studentScoreQueryService;
    private final ExamScheduleService examScheduleService;

    @GetMapping
    public ApiResponse<List<ChildResponse>> children() {
        return ApiResponse.ok(parentService.children());
    }

    /**
     * P-1 포털 홈. 여러 도메인을 <b>한 번의 호출</b>로 묶어 내려준다.
     *
     * <p>수업 내용·영상, 숙제 사진·피드백, 자료실 관련 필드를 여기에 추가하지 마라.
     * 학부모는 "자녀가 했는지 여부"만 본다.
     */
    @GetMapping("/{studentId}/home")
    public ApiResponse<ParentHomeResponse> home(@PathVariable Long studentId) {
        return ApiResponse.ok(homeService.parentHome(studentId));
    }

    /** P-2 캘린더. 학생 응답(S-6)과 형식이 같다. */
    @GetMapping("/{studentId}/attendances")
    public ApiResponse<AttendanceCalendarResponse> attendances(@PathVariable Long studentId,
                                                               @RequestParam int year,
                                                               @RequestParam int month) {
        return ApiResponse.ok(attendanceService.calendar(studentId, year, month));
    }

    /**
     * 클리닉. 일정·도착 시각·출석만 내려간다. 신청·변경 경로는 학부모에게 열지 않는다.
     *
     * <p>week를 빼면 그 달 전체(P-2 캘린더), 넣으면 그 주만(P-6 주간 레포트)이다.
     * 출석 캘린더(/attendances)와 같은 year·month 모양이라 화면이 날짜 문자열을
     * 만들 일이 없다.
     */
    @GetMapping("/{studentId}/clinics")
    public ApiResponse<List<ParentClinicResponse>> clinics(
        @PathVariable Long studentId,
        @RequestParam int year,
        @RequestParam int month,
        @RequestParam(required = false) Integer week) {
        return ApiResponse.ok(clinicService.listForChild(studentId, year, month, week));
    }

    /**
     * P-3. <b>제출 여부만</b> 내려간다. 숙제 내용·사진·피드백은 학생 화면(S-4)에만 있다.
     *
     * <p>상세 엔드포인트(.../homeworks/{homeworkId})를 만들지 마라.
     * 목록의 제출 여부가 학부모가 보는 전부다.
     */
    @GetMapping("/{studentId}/homeworks")
    public ApiResponse<PageResponse<ParentHomeworkResponse>> homeworks(
        @PathVariable Long studentId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(
            submissionService.childHomeworks(studentId, status, year, month, pageable));
    }

    /**
     * P-6 주간 레포트의 재료. <b>videoId·embedUrl은 null로 나간다</b> —
     * LessonReportResponse.forParent가 그 자리를 고정한다.
     * 시청 기록(POST /view)도 학부모 경로에는 없다. 보기만 한다.
     */
    @GetMapping("/{studentId}/lessons")
    public ApiResponse<PageResponse<LessonReportListItemResponse>> lessons(
        @PathVariable Long studentId,
        @RequestParam(required = false) Short year,
        @RequestParam(required = false) Short month,
        @RequestParam(required = false) Short week,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(
            lessonReportService.childLessons(studentId, year, month, week, pageable));
    }

    @GetMapping("/{studentId}/lessons/{lessonId}")
    public ApiResponse<LessonReportResponse> lesson(@PathVariable Long studentId,
                                                    @PathVariable Long lessonId) {
        return ApiResponse.ok(lessonReportService.childLesson(studentId, lessonId));
    }

    /**
     * P-4. 종류별 섹션이다. 학생 화면(S-7)과 <b>완전히 같은 응답</b>을 쓴다.
     *
     * <p>등수·백분위·반 평균은 어디에도 넣지 않는다.
     * 정기고사도 여기 없다 — 선생님만 보기로 확정된 데이터다.
     */
    @GetMapping("/{studentId}/scores")
    public ApiResponse<StudentScoreResponse> scores(@PathVariable Long studentId) {
        return ApiResponse.ok(studentScoreQueryService.forStudent(studentId));
    }

    /** 자녀가 바뀌면 반이 바뀌므로 일정도 함께 바뀐다. */
    @GetMapping("/{studentId}/exam-schedules")
    public ApiResponse<List<StudentExamScheduleResponse>> examSchedules(
        @PathVariable Long studentId) {
        return ApiResponse.ok(examScheduleService.childSchedules(studentId));
    }
}
