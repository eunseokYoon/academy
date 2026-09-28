package com.njwenglish.service.push;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 커밋 후에 보낼 알림 한 건. 서비스는 이것을 {@code ApplicationEventPublisher} 로 내기만 하고,
 * 보내는 건 {@link PushDispatcher} 가 <b>커밋 뒤에</b> 한다 — 롤백된 저장에 알림이 가면 안 된다.
 *
 * <p>담는 것은 id 뿐이다. 문구·수신자는 커밋된 데이터로 다시 계산한다.
 *
 * @param studentIds 누구에 관한 알림인가. 수신자는 이 학생과(또는) 그 학부모다
 * @param toStudent  공지의 audience 가 학부모 전용이면 false. 그 밖에는 {@link PushTopic} 이 정한다
 * @param toParent   공지의 audience 가 학생 전용이면 false
 * @param targetId   딥링크의 id(공지·질문·수업). 없으면 null
 * @param label      문구 앞에 붙는 말(주차별 성적의 「8월 2주」). 없으면 null
 */
public record PushEvent(PushTopic topic, Set<Long> studentIds, boolean toStudent,
                        boolean toParent, Long targetId, String label) {

    public PushEvent {
        studentIds = Set.copyOf(studentIds);
    }

    public static PushEvent of(PushTopic topic, Collection<Long> studentIds, Long targetId) {
        return new PushEvent(topic, Set.copyOf(studentIds), true, true, targetId, null);
    }

    public static PushEvent of(PushTopic topic, Long studentId, Long targetId) {
        return of(topic, List.of(studentId), targetId);
    }

    public static PushEvent labeled(PushTopic topic, Collection<Long> studentIds, String label) {
        return new PushEvent(topic, Set.copyOf(studentIds), true, true, null, label);
    }

    public static PushEvent notice(Long noticeId, Collection<Long> studentIds, boolean toStudent,
                                   boolean toParent) {
        return new PushEvent(PushTopic.NOTICE, Set.copyOf(studentIds), toStudent, toParent,
            noticeId, null);
    }

    public boolean isEmpty() {
        return studentIds.isEmpty();
    }
}
