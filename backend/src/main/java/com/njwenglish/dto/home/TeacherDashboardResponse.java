package com.njwenglish.dto.home;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * T-1 대시보드.
 *
 * <p><b>todo가 이 화면의 존재 이유다.</b> 선생님이 무엇을 안 했는지 한눈에 보여주고,
 * 각 항목을 탭하면 해당 화면으로 이동한다.
 *
 * <p>집계 테이블도 캐시도 두지 않는다. 200명 규모에서 실시간 count로 충분하다.
 */
public record TeacherDashboardResponse(Today today, Todo todo, Stats stats) {

    /** 오늘 수업. 시작 시각은 반에 있는 값이라 반 일정이 비어 있으면 null이다. */
    public record Today(LocalDate date, List<Lesson> lessons) {
    }

    public record Lesson(
        Long lessonId,
        String classRoomName,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        long studentCount,
        LessonAttendanceStatus attendanceStatus,
        boolean contentWritten
    ) {
    }

    /**
     * 앞의 여섯 개는 할 일이고, <b>뒤의 두 개는 점검 항목</b>이다.
     *
     * <p>반 코드에는 전화번호 대조가 없어서 코드를 아는 사람은 누구나 가입한다.
     * 막을 수단이 없으므로 가입 후 발견해서 지운다. recentSignupCount가 그 탐지 입구고,
     * openJoinCodeCount는 등록 기간이 끝났는데 코드가 열려 있는지 알려준다.
     *
     * <p><b>이 두 개는 0이어도 화면에서 숨기지 마라.</b> "확인했다"는 것 자체가 정보다.
     */
    public record Todo(
        long pendingAttendanceCount,
        long awaitingCheckCount,
        long unwrittenLessonCount,
        long unsignedStudentCount,
        long unlinkedParentCount,
        long pendingClinicRequestCount,
        long recentSignupCount,
        long openJoinCodeCount
    ) {
    }

    /** 등수·평균 같은 상대 지표는 어디에도 넣지 않는다. 규모 확인용 숫자 둘뿐이다. */
    public record Stats(long totalStudents, long activeClassRooms) {
    }
}
