package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.MaterialKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.notice.DownloadUrlResponse;
import com.njwenglish.dto.notice.MaterialUploadUrlRequest;
import com.njwenglish.dto.notice.MaterialUploadUrlResponse;
import com.njwenglish.dto.notice.NoticeAttachmentRequest;
import com.njwenglish.dto.notice.NoticeAttachmentResponse;
import com.njwenglish.dto.notice.NoticeCreateRequest;
import com.njwenglish.dto.notice.NoticeDetailResponse;
import com.njwenglish.dto.notice.NoticeResponse;
import com.njwenglish.dto.notice.NoticeSummaryResponse;
import com.njwenglish.dto.notice.NoticeUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Notice;
import com.njwenglish.entity.NoticeAttachment;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.NoticeScope;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.NoticeAttachmentRepository;
import com.njwenglish.repository.NoticeRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
 *
 * <p>첨부는 자료실이 쓰던 {@link MaterialKeys}를 그대로 재사용한다 — materials/ 프리픽스도
 * 같다. 공지 첨부가 자료실을 대체하는 것이라 새 서명 체계를 따로 둘 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    /** P-1 홈 배너에 접히지 않고 들어가는 건수. */
    private static final int HOME_RECENT_SIZE = 3;

    /** 활성 배정이 없는 학생용 더미. 빈 컬렉션을 IN에 넘기면 SQL 오류다. */
    private static final List<Long> NO_CLASS_ROOM = List.of(-1L);

    /** 첨부 다운로드 URL 유효기간. 응답에 담겨 나가는 값이라 짧게 잡는다. */
    private static final Duration DOWNLOAD_EXPIRY = Duration.ofMinutes(5);

    /** 공지당 첨부 상한. 50MB × 5 = 250MB가 공지 한 건의 최대 저장 비용이다. */
    private static final int MAX_ATTACHMENTS = 5;

    private final NoticeRepository noticeRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final NoticeAttachmentRepository attachmentRepository;
    private final PresignedUrlProvider presignedUrlProvider;
    private final MaterialKeys materialKeys;

    // ---------- 학생 · 학부모 ----------

    /**
     * studentId가 있으면 학부모가 자녀를 지정한 호출이다. 없으면 학생 본인이다.
     * <b>어느 쪽이든 첫 줄이 권한 검증이다.</b>
     */
    @Transactional(readOnly = true)
    public PageResponse<NoticeSummaryResponse> list(Long studentId, Pageable pageable) {
        Student student = resolveStudent(studentId);
        Page<Notice> notices = noticeRepository
            .findForStudent(student.getId(), classRoomIdsOf(student.getId()), parentView(),
                pageable);
        Set<Long> withAttachment = attachmentNoticeIds(notices.getContent());
        return PageResponse.from(
            notices.map(n -> NoticeSummaryResponse.from(n, withAttachment.contains(n.getId()))));
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse detail(Long noticeId, Long studentId) {
        Student student = resolveStudent(studentId);
        // 목록과 같은 조건으로 조회한다. 대상이 아니거나 초안이면 아예 나오지 않는다
        Notice notice = noticeRepository
            .findForStudent(noticeId, student.getId(), classRoomIdsOf(student.getId()),
                parentView())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return NoticeDetailResponse.from(notice, attachmentsOf(notice.getId()));
    }

    /** 홈 화면용. 목록과 같은 조건이라 개수와 목록이 어긋나지 않는다. */
    @Transactional(readOnly = true)
    public long countFor(Long studentId) {
        return noticeRepository.countForStudent(studentId, classRoomIdsOf(studentId),
            parentView());
    }

    /** P-1 홈 배너. 상단 몇 건만 필요하다. */
    @Transactional(readOnly = true)
    public List<NoticeSummaryResponse> recentFor(Long studentId) {
        List<Notice> notices = noticeRepository
            .findRecentForStudent(studentId, classRoomIdsOf(studentId), parentView(),
                PageRequest.of(0, HOME_RECENT_SIZE));
        Set<Long> withAttachment = attachmentNoticeIds(notices);
        return notices.stream()
            .map(n -> NoticeSummaryResponse.from(n, withAttachment.contains(n.getId())))
            .toList();
    }

    /**
     * <b>발급 전에 대상 여부를 다시 확인한다.</b> 목록에서 걸러졌다고 안심하면 안 된다 —
     * noticeId·attachmentId를 직접 넣어 호출하면 목록을 거치지 않는다.
     * 조회 조건이 목록과 같아서 학생만 공지는 학부모에게 404다.
     */
    @Transactional(readOnly = true)
    public DownloadUrlResponse attachmentDownloadUrl(Long noticeId, Long attachmentId,
                                                      Long studentId) {
        Student student = resolveStudent(studentId);
        noticeRepository
            .findForStudent(noticeId, student.getId(), classRoomIdsOf(student.getId()),
                parentView())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        NoticeAttachment attachment = attachmentRepository.findById(attachmentId)
            .filter(each -> each.getNotice().getId().equals(noticeId))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new DownloadUrlResponse(
            presignedUrlProvider.attachmentUrl(
                attachment.getS3Key(), attachment.getFileName(), DOWNLOAD_EXPIRY),
            attachment.getFileName(),
            DOWNLOAD_EXPIRY.toSeconds());
    }

    /**
     * 개인 공지를 즉시 발행한다. <b>수업일 변경 승인이 유일한 호출부다.</b>
     *
     * <p>초안을 거치지 않는다 — 승인 순간 알리는 것이 목적이라 선생님이 발행을 한 번 더
     * 눌러야 한다면 안 누른 알림은 아무에게도 안 간다.
     *
     * <p>엔티티를 그대로 돌려주는 이유는 호출부가 notice_id를 요청에 걸어야 하기 때문이다.
     * 같은 트랜잭션 안이라 영속 상태 그대로 넘어간다.
     */
    @Transactional
    public Notice publishForStudent(String title, String content, Student student,
                                    Teacher teacher) {
        return noticeRepository.save(
            Notice.publishedForStudent(title, content, student, teacher, OffsetDateTime.now()));
    }

    // ---------- 선생님 (T-10) ----------

    @Transactional(readOnly = true)
    public PageResponse<NoticeResponse> listForTeacher(Pageable pageable) {
        return PageResponse.from(noticeRepository.findAllForTeacher(pageable)
            .map(notice -> NoticeResponse.from(notice, attachmentsOf(notice.getId()))));
    }

    @Transactional(readOnly = true)
    public NoticeResponse detailForTeacher(Long noticeId) {
        Notice notice = findNotice(noticeId);
        return NoticeResponse.from(notice, attachmentsOf(notice.getId()));
    }

    /**
     * 첨부 업로드 URL 발급. 자료실 시절과 같은 순서다 —
     * 확장자 → 용량 → 발급. 등록 단계에서만 검사하면 이미 올라간 파일이 고아로 남는다.
     */
    @Transactional(readOnly = true)
    public MaterialUploadUrlResponse issueAttachmentUploadUrl(MaterialUploadUrlRequest request) {
        Teacher teacher = currentTeacher();

        String extension = materialKeys.extensionOf(request.fileName());
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.bytes() > MaterialKeys.MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String s3Key = materialKeys.issue(teacher.getId(), extension, LocalDate.now());
        String contentType = materialKeys.contentTypeOf(extension);
        return new MaterialUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, contentType), s3Key, contentType);
    }

    /** 초안으로 만든다. publish를 호출해야 학생·학부모에게 보인다. */
    @Transactional
    public NoticeResponse create(NoticeCreateRequest request) {
        Teacher teacher = currentTeacher();
        ClassRoom classRoom = resolveTarget(request.scope(), request.classRoomId());

        Notice notice = noticeRepository.save(Notice.draft(
            request.title(), request.content(), request.scope(),
            classRoom, request.pinned(), request.audience(), teacher));
        replaceAttachments(notice, request.attachments(), teacher);
        return NoticeResponse.from(notice, attachmentsOf(notice.getId()));
    }

    @Transactional
    public NoticeResponse update(Long noticeId, NoticeUpdateRequest request) {
        Notice notice = findNotice(noticeId);
        Teacher teacher = currentTeacher();

        notice.edit(
            request.title() == null ? notice.getTitle() : request.title(),
            request.content() == null ? notice.getContent() : request.content(),
            request.pinned() == null ? notice.isPinned() : request.pinned(),
            request.audience() == null ? notice.getAudience() : request.audience());

        // scope를 보냈을 때만 대상을 건드린다. 따로 바꾸면 ck_notices_target에 걸린다
        if (request.scope() != null) {
            notice.retarget(request.scope(),
                resolveTarget(request.scope(), request.classRoomId()));
        }
        replaceAttachments(notice, request.attachments(), teacher);
        return NoticeResponse.from(notice, attachmentsOf(notice.getId()));
    }

    /**
     * 발행. 이 순간부터 학생·학부모 목록에 나온다.
     * 이미 발행된 공지를 다시 눌러도 최초 발행 시각은 유지된다 (목록 정렬 기준이다).
     */
    @Transactional
    public NoticeResponse publish(Long noticeId) {
        Notice notice = findNotice(noticeId);
        notice.publish(OffsetDateTime.now());
        return NoticeResponse.from(notice, attachmentsOf(notice.getId()));
    }

    /**
     * 삭제. 첨부가 있으면 마지막 참조인 S3 객체를 지운다.
     * 발행된 공지를 지우면 학생 화면에서도 사라진다 — 그게 의도다.
     */
    @Transactional
    public void delete(Long noticeId) {
        Notice notice = findNotice(noticeId);
        deleteAttachments(notice.getId());
        noticeRepository.delete(notice);
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

    /**
     * 학부모 조회인지. <b>studentId의 유무로 판정하지 마라.</b> 학생도 자기 studentId를
     * 붙여 부를 수 있고 requireAccessible이 통과시킨다 — 그러면 학생만 공지가
     * 정작 학생에게 안 보인다. 선생님(T-10)은 이 경로를 쓰지 않는다.
     */
    private boolean parentView() {
        return CurrentUser.get().role() == UserRole.PARENT;
    }

    private List<Long> classRoomIdsOf(Long studentId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        return ids.isEmpty() ? NO_CLASS_ROOM : ids;
    }

    /**
     * ALL이면 반이 없어야 하고, CLASS면 있어야 한다. 어긋나면 400이다.
     *
     * <p><b>STUDENT는 여기서 막는다.</b> 개인 공지는 수업일 변경 승인만 만든다 —
     * 선생님이 임의로 개인에게 공지를 쓰는 화면은 없고, 열어 주면 대상 학생을 고르는
     * UI부터 권한 검증까지 따라붙는다. 개인에게 갈 말은 숙제 피드백에 쓴다.
     */
    private ClassRoom resolveTarget(NoticeScope scope, Long classRoomId) {
        if (scope == NoticeScope.STUDENT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
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

    private List<NoticeAttachmentResponse> attachmentsOf(Long noticeId) {
        return attachmentRepository.findByNoticeIdOrderBySortOrder(noticeId).stream()
            .map(NoticeAttachmentResponse::from)
            .toList();
    }

    /**
     * 목록용 hasAttachment 배치 조회. 20건 × 쿼리를 피하려고 noticeId 묶음으로 한 번에 센다.
     */
    private Set<Long> attachmentNoticeIds(List<Notice> notices) {
        List<Long> noticeIds = notices.stream().map(Notice::getId).toList();
        if (noticeIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(attachmentRepository.findNoticeIdsWithAttachment(noticeIds));
    }

    /**
     * 첨부를 통째로 교체한다. null이면 건드리지 않는다.
     *
     * <p><b>s3Key 대조를 빼지 마라.</b> 클라이언트가 보낸 키를 그대로 믿으면
     * 버킷 내 임의 경로를 첨부로 등록할 수 있다.
     */
    private void replaceAttachments(Notice notice, List<NoticeAttachmentRequest> requests,
                                    Teacher teacher) {
        if (requests == null) {
            return;
        }
        if (requests.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
        }
        for (NoticeAttachmentRequest each : requests) {
            if (!materialKeys.matches(each.s3Key(), teacher.getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
        deleteAttachments(notice.getId());

        short order = 0;
        for (NoticeAttachmentRequest each : requests) {
            attachmentRepository.save(NoticeAttachment.of(
                notice, each.s3Key(), each.fileName(), each.bytes(), order++));
        }
    }

    /** 행을 지우고, <b>마지막 참조인 S3 객체만</b> 지운다. */
    private void deleteAttachments(Long noticeId) {
        List<NoticeAttachment> existing =
            attachmentRepository.findByNoticeIdOrderBySortOrder(noticeId);
        if (existing.isEmpty()) {
            return;
        }
        attachmentRepository.deleteByNoticeId(noticeId);
        attachmentRepository.flush();

        for (NoticeAttachment each : existing) {
            if (attachmentRepository.countByS3Key(each.getS3Key()) == 0) {
                presignedUrlProvider.deleteQuietly(each.getS3Key());
            }
        }
    }
}
