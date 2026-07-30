package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.notice.NoticeCreateRequest;
import com.njwenglish.dto.notice.NoticeDetailResponse;
import com.njwenglish.dto.notice.NoticeResponse;
import com.njwenglish.dto.notice.NoticeSummaryResponse;
import com.njwenglish.dto.notice.NoticeUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Notice;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.NoticeScope;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.NoticeRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 (T-10 작성 · 학생·학부모 조회).
 *
 * <p><b>/api/notices는 공통 경로라 SecurityConfig의 역할 검사가 걸리지 않는다.</b>
 * 경로가 공통이라고 권한 검증을 건너뛰지 마라 — studentId를 받는 API에 예외는 없다.
 * 공지 자체는 개인 데이터가 아니지만, 검증이 없으면 남의 자녀 studentId로 그 학생의
 * 반 범위 공지를 조회할 수 있고, 이 규칙에 한 곳이라도 구멍을 내면 다음 사람이 따라 한다.
 *
 * <p>읽음 표시(notice_reads)는 1차 범위 밖이다. 만들지 마라.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    /** P-1 홈 배너에 접히지 않고 들어가는 건수. */
    private static final int HOME_RECENT_SIZE = 3;

    /** 활성 배정이 없는 학생용 더미. 빈 컬렉션을 IN에 넘기면 SQL 오류다. */
    private static final List<Long> NO_CLASS_ROOM = List.of(-1L);

    private final NoticeRepository noticeRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;

    // ---------- 학생 · 학부모 ----------

    /**
     * studentId가 있으면 학부모가 자녀를 지정한 호출이다. 없으면 학생 본인이다.
     * <b>어느 쪽이든 첫 줄이 권한 검증이다.</b>
     */
    @Transactional(readOnly = true)
    public PageResponse<NoticeSummaryResponse> list(Long studentId, Pageable pageable) {
        Student student = resolveStudent(studentId);
        return PageResponse.from(noticeRepository
            .findForStudent(classRoomIdsOf(student.getId()), pageable)
            .map(NoticeSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse detail(Long noticeId, Long studentId) {
        Student student = resolveStudent(studentId);
        // 목록과 같은 조건으로 조회한다. 대상이 아니거나 초안이면 아예 나오지 않는다
        return noticeRepository.findForStudent(noticeId, classRoomIdsOf(student.getId()))
            .map(NoticeDetailResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 홈 화면용. 목록과 같은 조건이라 개수와 목록이 어긋나지 않는다. */
    @Transactional(readOnly = true)
    public long countFor(Long studentId) {
        return noticeRepository.countForStudent(classRoomIdsOf(studentId));
    }

    /** P-1 홈 배너. 상단 몇 건만 필요하다. */
    @Transactional(readOnly = true)
    public List<NoticeSummaryResponse> recentFor(Long studentId) {
        return noticeRepository
            .findRecentForStudent(classRoomIdsOf(studentId), PageRequest.of(0, HOME_RECENT_SIZE))
            .stream()
            .map(NoticeSummaryResponse::from)
            .toList();
    }

    // ---------- 선생님 (T-10) ----------

    @Transactional(readOnly = true)
    public PageResponse<NoticeResponse> listForTeacher(Pageable pageable) {
        return PageResponse.from(
            noticeRepository.findAllForTeacher(pageable).map(NoticeResponse::from));
    }

    @Transactional(readOnly = true)
    public NoticeResponse detailForTeacher(Long noticeId) {
        return NoticeResponse.from(findNotice(noticeId));
    }

    /** 초안으로 만든다. publish를 호출해야 학생·학부모에게 보인다. */
    @Transactional
    public NoticeResponse create(NoticeCreateRequest request) {
        Teacher teacher = currentTeacher();
        ClassRoom classRoom = resolveTarget(request.scope(), request.classRoomId());

        return NoticeResponse.from(noticeRepository.save(Notice.draft(
            request.title(), request.content(), request.scope(),
            classRoom, request.pinned(), teacher)));
    }

    @Transactional
    public NoticeResponse update(Long noticeId, NoticeUpdateRequest request) {
        Notice notice = findNotice(noticeId);

        notice.edit(
            request.title() == null ? notice.getTitle() : request.title(),
            request.content() == null ? notice.getContent() : request.content(),
            request.pinned() == null ? notice.isPinned() : request.pinned());

        // scope를 보냈을 때만 대상을 건드린다. 따로 바꾸면 ck_notices_target에 걸린다
        if (request.scope() != null) {
            notice.retarget(request.scope(),
                resolveTarget(request.scope(), request.classRoomId()));
        }
        return NoticeResponse.from(notice);
    }

    /**
     * 발행. 이 순간부터 학생·학부모 목록에 나온다.
     * 이미 발행된 공지를 다시 눌러도 최초 발행 시각은 유지된다 (목록 정렬 기준이다).
     */
    @Transactional
    public NoticeResponse publish(Long noticeId) {
        Notice notice = findNotice(noticeId);
        notice.publish(OffsetDateTime.now());
        return NoticeResponse.from(notice);
    }

    /**
     * 삭제. 읽음 기록도 첨부도 없어서 걸리는 것이 없다.
     * 발행된 공지를 지우면 학생 화면에서도 사라진다 — 그게 의도다.
     */
    @Transactional
    public void delete(Long noticeId) {
        noticeRepository.delete(findNotice(noticeId));
    }

    // ---------- 내부 ----------

    /**
     * studentId가 있으면 requireAccessible, 없으면 학생 본인이다.
     * <b>여기가 /api/notices의 유일한 권한 관문이다.</b>
     */
    private Student resolveStudent(Long studentId) {
        return studentId == null
            ? studentAccessGuard.requireSelf()
            : studentAccessGuard.requireAccessible(studentId);
    }

    private List<Long> classRoomIdsOf(Long studentId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        return ids.isEmpty() ? NO_CLASS_ROOM : ids;
    }

    /** ALL이면 반이 없어야 하고, CLASS면 있어야 한다. 어긋나면 400이다. */
    private ClassRoom resolveTarget(NoticeScope scope, Long classRoomId) {
        if (scope == NoticeScope.ALL) {
            if (classRoomId != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            return null;
        }
        if (classRoomId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return classRoomRepository.findById(classRoomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Notice findNotice(Long noticeId) {
        return noticeRepository.findWithClassRoom(noticeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
