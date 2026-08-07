package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-6b 그리드 한 장. 반 × 수업일이 단위다.
 *
 * <p>페이징하지 않는다. 반 단위(최대 30명)라 한 화면에 다 보여주는 것이 맞다.
 * students는 <b>그 수업일 기준 재원생</b>이고 정렬은 students.name이다 —
 * users.name으로 정렬하면 미가입 학생이 명단에서 통째로 사라진다.
 */
public record HomeworkGridResponse(LessonInfo lesson,
                                   List<StudentInfo> students,
                                   List<ColumnInfo> columns) {

    public record LessonInfo(Long id,
                             LocalDate lessonDate,
                             Long classRoomId,
                             String classRoomName) {
    }

    public record StudentInfo(Long studentId, String name) {
    }

    /**
     * 열 하나. resubmitDueAt이 null이면 재제출을 아직 안 연 것이다 —
     * 그 상태에서는 어떤 학생도 온라인으로 낼 수 없다.
     */
    public record ColumnInfo(Long homeworkId,
                             String title,
                             Short sortOrder,
                             OffsetDateTime resubmitDueAt,
                             int resubmitTargetCount,
                             int awaitingCheckCount,
                             List<CellInfo> cells) {
    }

    /**
     * 칸 하나. result가 null이면 회색 "미채점"이다.
     * status는 온라인 제출 축이라 ⭕를 받은 학생은 NOT_SUBMITTED로 남는다 — 정상이다.
     */
    public record CellInfo(Long studentId,
                           HomeworkResult result,
                           Short completionRate,
                           boolean resolvedByResubmission,
                           SubmissionStatus status,
                           int photoCount,
                           boolean hasVideo) {
    }
}
