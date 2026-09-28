package com.njwenglish.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.njwenglish.repository.DeviceTokenRepository;
import com.njwenglish.repository.PushDailySendRepository;
import com.njwenglish.repository.PushRecipientRepository.RecipientRow;
import com.njwenglish.repository.PushRecipientRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 누가 무엇을 받는가. 설계 2부의 수신자 표와 「본문은 제목과 대상까지만」이 여기서 지켜진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushPlannerTest {

    @Mock
    private PushRecipientRepository recipientRepository;
    @Mock
    private DeviceTokenRepository tokenRepository;
    @Mock
    private PushDailySendRepository dailySendRepository;
    @InjectMocks
    private PushPlanner planner;

    /** 학생 88 김하늘(계정 1000), 학부모 계정 2000. */
    private static RecipientRow row(Long studentId, String name, Long studentUserId,
                                    Long parentUserId) {
        return new RecipientRow() {
            public Long getStudentId() { return studentId; }
            public String getStudentName() { return name; }
            public Long getStudentUserId() { return studentUserId; }
            public Long getParentUserId() { return parentUserId; }
        };
    }

    private static DeviceTokenRepository.TokenRow token(Long userId, String token) {
        return new DeviceTokenRepository.TokenRow() {
            public Long getUserId() { return userId; }
            public String getToken() { return token; }
        };
    }

    private static final RecipientRow HANUL = row(88L, "김하늘", 1000L, 2000L);

    @Test
    @DisplayName("학생 문구에는 이름이 없고, 학부모 문구에는 자녀 이름과 studentId 가 붙는다")
    void 학생과_학부모의_문구와_딥링크() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.HOMEWORK_GRADED, 88L, null), List.of(HANUL));

        assertThat(drafts.get(1000L).title()).isEqualTo("숙제가 채점됐어요");
        assertThat(drafts.get(1000L).data()).isEqualTo(Map.of("screen", "homework"));
        assertThat(drafts.get(2000L).title()).isEqualTo("김하늘 · 숙제가 채점됐어요");
        assertThat(drafts.get(2000L).data())
            .isEqualTo(Map.of("screen", "homework", "studentId", "88"));
    }

    @Test
    @DisplayName("주차 라벨이 문구 앞에 붙는다")
    void 주차_라벨() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.labeled(PushTopic.WEEKLY_SCORE, List.of(88L), "8월 2주"), List.of(HANUL));

        assertThat(drafts.get(1000L).title()).isEqualTo("8월 2주 성적이 올라왔어요");
        assertThat(drafts.get(2000L).title()).isEqualTo("김하늘 · 8월 2주 성적이 올라왔어요");
    }

    @Test
    @DisplayName("질의응답 답글은 학부모에게 가지 않는다 — 게시판에 학부모 경로가 없다")
    void 답글은_학부모_금지() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.QNA_REPLY, 88L, 12L), List.of(HANUL));

        assertThat(drafts).containsOnlyKeys(1000L);
        assertThat(drafts.get(1000L).data()).isEqualTo(Map.of("screen", "qna", "id", "12"));
    }

    @Test
    @DisplayName("재제출·온라인 테스트 제출은 본인 행동이라 학부모만 받는다")
    void 본인_행동은_학부모만() {
        assertThat(PushPlanner.draftsOf(
            PushEvent.of(PushTopic.HOMEWORK_SUBMITTED, 88L, null), List.of(HANUL)))
            .containsOnlyKeys(2000L);
        var online = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.ONLINE_TEST_SUBMITTED, 88L, null), List.of(HANUL));
        assertThat(online).containsOnlyKeys(2000L);
        // 학부모에게 온라인 테스트 화면이 없어 성적으로 간다
        assertThat(online.get(2000L).data().get("screen")).isEqualTo("scores");
    }

    @Test
    @DisplayName("공지 audience 가 한쪽이면 다른 쪽은 받지 않는다")
    void 공지_audience() {
        assertThat(PushPlanner.draftsOf(
            PushEvent.notice(15L, List.of(88L), true, false), List.of(HANUL)))
            .containsOnlyKeys(1000L);
        assertThat(PushPlanner.draftsOf(
            PushEvent.notice(15L, List.of(88L), false, true), List.of(HANUL)))
            .containsOnlyKeys(2000L);
    }

    @Test
    @DisplayName("미가입 학생·학부모 없는 학생은 있는 쪽만 받는다")
    void 계정이_없는_쪽은_건너뛴다() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.HOMEWORK_GRADED, List.of(88L, 89L), null),
            List.of(row(88L, "김하늘", null, 2000L), row(89L, "이서준", 1001L, null)));

        assertThat(drafts).containsOnlyKeys(2000L, 1001L);
    }

    @Test
    @DisplayName("형제가 같은 반이면 학부모는 반 공지를 한 번만 받는다 — 먼저 나온 자녀로")
    void 형제는_한번만() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.notice(15L, List.of(88L, 90L), true, true),
            List.of(HANUL, row(90L, "김바다", 1002L, 2000L)));

        assertThat(drafts).containsOnlyKeys(1000L, 2000L, 1002L);
        assertThat(drafts.get(2000L).data()).containsEntry("studentId", "88");
    }

    @Test
    @DisplayName("학생에게 가는 딥링크는 역할마다 다르다 — 출석 확정은 학생 출석, 학부모 스케줄")
    void 역할별_딥링크() {
        var drafts = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.ATTENDANCE_LESSON, 88L, null), List.of(HANUL));
        assertThat(drafts.get(1000L).data().get("screen")).isEqualTo("attendance");
        assertThat(drafts.get(2000L).data().get("screen")).isEqualTo("schedule");

        var lesson = PushPlanner.draftsOf(
            PushEvent.of(PushTopic.LESSON_PUBLISHED, 88L, 501L), List.of(HANUL));
        assertThat(lesson.get(1000L).data()).isEqualTo(Map.of("screen", "lesson", "id", "501"));
        assertThat(lesson.get(2000L).data().get("screen")).isEqualTo("report");
    }

    @Test
    @DisplayName("묶는 종류는 그날 자리를 차지한 사람에게만 보낸다 — 0 이면 건너뛴다")
    void 하루_1건() {
        given(recipientRepository.findRecipients(any())).willReturn(List.of(HANUL));
        given(tokenRepository.findDeliverable(any()))
            .willReturn(List.of(token(1000L, "tok-student"), token(2000L, "tok-parent")));
        given(dailySendRepository.claim(eq(1000L), eq("HOMEWORK_GRADED"), any())).willReturn(1);
        given(dailySendRepository.claim(eq(2000L), eq("HOMEWORK_GRADED"), any())).willReturn(0);

        var messages = planner.plan(PushEvent.of(PushTopic.HOMEWORK_GRADED, 88L, null));

        assertThat(messages).extracting(PushMessage::token).containsExactly("tok-student");
    }

    @Test
    @DisplayName("묶지 않는 종류는 자리를 차지하지 않는다")
    void 단건은_묶지_않는다() {
        given(recipientRepository.findRecipients(any())).willReturn(List.of(HANUL));
        given(tokenRepository.findDeliverable(any()))
            .willReturn(List.of(token(2000L, "a"), token(2000L, "b")));

        var messages = planner.plan(PushEvent.of(PushTopic.HOMEWORK_SUBMITTED, 88L, null));

        // 학부모의 기기 두 대 모두
        assertThat(messages).extracting(PushMessage::token).containsExactly("a", "b");
        verify(dailySendRepository, never()).claim(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("토큰이 없는 사람은 하루 자리를 차지하지 않는다")
    void 토큰이_없으면_표식도_없다() {
        given(recipientRepository.findRecipients(any())).willReturn(List.of(HANUL));
        given(tokenRepository.findDeliverable(any())).willReturn(List.of());

        assertThat(planner.plan(PushEvent.of(PushTopic.WEEKLY_SCORE, 88L, null))).isEmpty();
        verifyNoInteractions(dailySendRepository);
    }

    @Test
    @DisplayName("대상 학생이 없으면 조회하지 않는다")
    void 빈_이벤트() {
        assertThat(planner.plan(PushEvent.of(PushTopic.NOTICE, List.of(), 1L))).isEmpty();
        verifyNoInteractions(recipientRepository, tokenRepository);
    }
}
