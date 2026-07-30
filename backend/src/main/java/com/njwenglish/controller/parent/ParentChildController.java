package com.njwenglish.controller.parent;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.dto.clinic.ParentClinicResponse;
import com.njwenglish.dto.home.ParentHomeResponse;
import com.njwenglish.dto.homework.ParentHomeworkResponse;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.dto.score.ScoreChartResponse;
import com.njwenglish.dto.score.StudentExamScheduleResponse;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.service.AttendanceService;
import com.njwenglish.service.ClinicService;
import com.njwenglish.service.ExamScheduleService;
import com.njwenglish.service.HomeService;
import com.njwenglish.service.ParentService;
import com.njwenglish.service.ScoreService;
import com.njwenglish.service.SubmissionService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final ScoreService scoreService;
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

    /** P-2 클리닉. 일정과 출석만 내려간다. */
    @GetMapping("/{studentId}/clinics")
    public ApiResponse<List<ParentClinicResponse>> clinics(
        @PathVariable Long studentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(clinicService.listForChild(studentId, from, to));
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
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(submissionService.childHomeworks(studentId, status, pageable));
    }

    /**
     * P-4. 주차별 시계열이다. 표가 아니라 그래프가 학부모 화면의 핵심이다.
     *
     * <p>등수·백분위·반 평균은 어디에도 넣지 않는다.
     */
    @GetMapping("/{studentId}/scores")
    public ApiResponse<ScoreChartResponse> scores(@PathVariable Long studentId,
                                                  @RequestParam(required = false)
                                                  ScoreType scoreType) {
        return ApiResponse.ok(scoreService.childScores(studentId, scoreType));
    }

    /** 자녀가 바뀌면 반이 바뀌므로 일정도 함께 바뀐다. */
    @GetMapping("/{studentId}/exam-schedules")
    public ApiResponse<List<StudentExamScheduleResponse>> examSchedules(
        @PathVariable Long studentId) {
        return ApiResponse.ok(examScheduleService.childSchedules(studentId));
    }
}
