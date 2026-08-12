package com.njwenglish.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.njwenglish.entity.enums.HomeworkKind;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeworkGradingTest {

    private static final OffsetDateTime DUE_AT =
        OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9));

    private Homework gridColumn() {
        ClassRoom classRoom = ClassRoom.create(Fixtures.teacherEntity(1L), "동성고1 수요일반", "HK7F2Q", null);
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        return Homework.gridColumn(classRoom, lesson, Fixtures.teacherEntity(1L), "독해 5-8", (short) 1);
    }

    private Submission cellOf(Homework homework) {
        return Submission.notSubmitted(homework, Fixtures.student(88L, "고연준"));
    }

    @Test
    @DisplayName("GRID 열은 마감 없이 만들어지고 재제출이 닫혀 있다")
    void gridColumnStartsClosed() {
        Homework column = gridColumn();

        assertThat(column.getKind()).isEqualTo(HomeworkKind.GRID);
        assertThat(column.isGrid()).isTrue();
        assertThat(column.getDueAt()).isNull();
        assertThat(column.isResubmitOpen()).isFalse();
        assertThat(column.getSortOrder()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("재제출을 열면 마감이 붙고 isResubmitOpen이 true가 된다")
    void openResubmit() {
        Homework column = gridColumn();

        column.openResubmit(DUE_AT);

        assertThat(column.isResubmitOpen()).isTrue();
        assertThat(column.getDueAt()).isEqualTo(DUE_AT);
    }

    @Test
    @DisplayName("재제출을 닫으면 마감이 null로 돌아간다")
    void closeResubmit() {
        Homework column = gridColumn();
        column.openResubmit(DUE_AT);

        column.closeResubmit();

        assertThat(column.isResubmitOpen()).isFalse();
        assertThat(column.getDueAt()).isNull();
    }

    @Test
    @DisplayName("세모는 퍼센트를 함께 저장한다")
    void gradePartial() {
        Submission cell = cellOf(gridColumn());

        cell.grade(HomeworkResult.PARTIAL, (short) 50);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.PARTIAL);
        assertThat(cell.getCompletionRate()).isEqualTo((short) 50);
    }

    @Test
    @DisplayName("세모가 아니면 퍼센트를 버린다 - 보내와도 null로 저장한다")
    void gradeDropsRateWhenNotPartial() {
        Submission cell = cellOf(gridColumn());

        cell.grade(HomeworkResult.DONE, (short) 50);

        assertThat(cell.getCompletionRate()).isNull();
    }

    @Test
    @DisplayName("세모인데 퍼센트가 없으면 DB 제약에 걸릴 값이 만들어진다 - 호출부가 채워야 한다")
    void gradePartialWithoutRateIsCallerError() {
        Submission cell = cellOf(gridColumn());

        cell.grade(HomeworkResult.PARTIAL, null);

        // 엔티티는 막지 않는다. ck_submissions_rate가 최종 방어선이라는 걸 여기 남긴다
        assertThat(cell.getResult()).isEqualTo(HomeworkResult.PARTIAL);
        assertThat(cell.getCompletionRate()).isNull();
    }

    @Test
    @DisplayName("재제출 확인은 동그라미로 올리고 재제출 표시를 붙인다")
    void resolveByResubmission() {
        Submission cell = cellOf(gridColumn());
        cell.grade(HomeworkResult.PARTIAL, (short) 50);

        cell.resolveByResubmission();

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(cell.getCompletionRate()).isNull();
        assertThat(cell.isResolvedByResubmission()).isTrue();
    }

    @Test
    @DisplayName("확인 후 다시 X로 고치면 재제출 표시가 내려간다")
    void regradeClearsResolvedFlag() {
        Submission cell = cellOf(gridColumn());
        cell.resolveByResubmission();

        cell.grade(HomeworkResult.NOT_DONE, null);

        assertThat(cell.isResolvedByResubmission()).isFalse();
    }

    @Test
    @DisplayName("재제출을 연 열에서 세모·X만 대상이 된다")
    void resubmitTarget() {
        Homework column = gridColumn();
        column.openResubmit(DUE_AT);

        Submission done = cellOf(column);
        done.grade(HomeworkResult.DONE, null);
        Submission partial = cellOf(column);
        partial.grade(HomeworkResult.PARTIAL, (short) 50);
        Submission notDone = cellOf(column);
        notDone.grade(HomeworkResult.NOT_DONE, null);
        Submission ungraded = cellOf(column);

        assertThat(done.isResubmitTarget()).isFalse();
        assertThat(partial.isResubmitTarget()).isTrue();
        assertThat(notDone.isResubmitTarget()).isTrue();
        assertThat(ungraded.isResubmitTarget()).isFalse();
    }

    @Test
    @DisplayName("재제출을 안 연 열은 세모·X여도 대상이 아니다")
    void notTargetWhenResubmitClosed() {
        Submission cell = cellOf(gridColumn());
        cell.grade(HomeworkResult.NOT_DONE, null);

        assertThat(cell.isResubmitTarget()).isFalse();
    }

    @Test
    @DisplayName("ONLINE 숙제는 채점 축을 쓰지 않아 재제출 대상이 되지 않는다")
    void onlineHomeworkIsNeverResubmitTarget() {
        ClassRoom classRoom = ClassRoom.create(Fixtures.teacherEntity(1L), "고2 심화반", "AB12CD", null);
        Homework online = Homework.create(classRoom, null, Fixtures.teacherEntity(1L),
            "주간지 전 범위", null, DUE_AT);
        Submission cell = Submission.notSubmitted(online, Fixtures.student(88L, "고연준"));

        assertThat(online.isGrid()).isFalse();
        assertThat(cell.getResult()).isNull();
        assertThat(cell.isResubmitTarget()).isFalse();
    }
}
