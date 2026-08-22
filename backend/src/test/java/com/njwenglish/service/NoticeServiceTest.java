package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.MaterialKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.notice.MaterialUploadUrlRequest;
import com.njwenglish.dto.notice.NoticeAttachmentRequest;
import com.njwenglish.dto.notice.NoticeCreateRequest;
import com.njwenglish.dto.notice.NoticeUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Notice;
import com.njwenglish.entity.NoticeAttachment;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.NoticeScope;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.NoticeAttachmentRepository;
import com.njwenglish.repository.NoticeRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * /api/notices는 역할별 접두사가 아니라 공통 경로다. SecurityConfig가 역할을 보지 않으므로
 * <b>권한 검증이 전부 서비스에 있다.</b> 여기가 뚫리면 학부모가 남의 자녀 studentId로
 * 그 학생의 반 공지를 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private NoticeAttachmentRepository attachmentRepository;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;
    @Mock
    private MaterialKeys materialKeys;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Student child = Fixtures.student(88L, "서동환");

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository, classRoomRepository,
            enrollmentRepository, teacherRepository, studentAccessGuard, attachmentRepository,
            presignedUrlProvider, materialKeys);
        given(teacherRepository.findByUserId(any()))
            .willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Notice notice(Long id, NoticeScope scope, ClassRoom target) {
        return notice(id, scope, target, false);
    }

    private Notice notice(Long id, NoticeScope scope, ClassRoom target, boolean studentsOnly) {
        Notice notice = Notice.draft("[SUMMER] 선행 안내", "본문", scope, target, false,
            studentsOnly, Fixtures.teacherEntity(1L));
        ReflectionTestUtils.setField(notice, "id", id);
        return notice;
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("studentId를 받으면 첫 줄이 requireAccessible이다")
    void studentId를_받으면_접근_권한을_검증한다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(any(), any(), anyBoolean(), any(Pageable.class)))
            .willReturn(Page.empty());

        noticeService.list(88L, PageRequest.of(0, 20));

        verify(studentAccessGuard).requireAccessible(88L);
        verify(studentAccessGuard, never()).requireSelf();
    }

    @Test
    @DisplayName("studentId가 없으면 학생 본인 조회다")
    void studentId가_없으면_본인_조회다() {
        given(studentAccessGuard.requireSelf()).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(any(), any(), anyBoolean(), any(Pageable.class)))
            .willReturn(Page.empty());

        noticeService.list(null, PageRequest.of(0, 20));

        verify(studentAccessGuard).requireSelf();
        verify(studentAccessGuard, never()).requireAccessible(any());
    }

    @Test
    @DisplayName("자녀가 아니면 403이 그대로 전파된다")
    void 남의_자녀_공지는_403이다() {
        given(studentAccessGuard.requireAccessible(99L))
            .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));

        assertThatThrownBy(() -> noticeService.list(99L, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDENT_NOT_ACCESSIBLE);

        verify(noticeRepository, never())
            .findForStudent(any(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    @DisplayName("배정이 없는 학생도 목록 조회가 깨지지 않는다 — 빈 IN 대신 더미가 들어간다")
    void 배정_전_학생도_공지를_조회할_수_있다() {
        given(studentAccessGuard.requireSelf()).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of());
        given(noticeRepository.findForStudent(any(), any(), anyBoolean(), any(Pageable.class)))
            .willReturn(Page.empty());

        noticeService.list(null, PageRequest.of(0, 20));

        verify(noticeRepository)
            .findForStudent(eq(88L), eq(List.of(-1L)), anyBoolean(), any(Pageable.class));
    }

    @Test
    @DisplayName("대상이 아닌 공지나 초안 상세는 404다")
    void 대상이_아닌_공지_상세는_404다() {
        given(studentAccessGuard.requireSelf()).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(eq(15L), eq(88L), eq(List.of(3L)), anyBoolean()))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.detail(15L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ---------- 작성 ----------

    @Test
    @DisplayName("CLASS인데 classRoomId가 없으면 400이다")
    void CLASS는_반이_필수다() {
        assertThatThrownBy(() -> noticeService.create(new NoticeCreateRequest(
            "제목", "본문", NoticeScope.CLASS, null, false, false, List.of())))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(noticeRepository, never()).save(any());
    }

    @Test
    @DisplayName("ALL인데 classRoomId가 있으면 400이다 — ck_notices_target과 같은 규칙이다")
    void ALL은_반을_받지_않는다() {
        assertThatThrownBy(() -> noticeService.create(new NoticeCreateRequest(
            "제목", "본문", NoticeScope.ALL, 3L, false, false, List.of())))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("새 공지는 초안이다 — 발행 전에는 학생·학부모에게 보이지 않는다")
    void 새_공지는_초안이다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(noticeRepository.save(any())).willAnswer(call -> call.getArgument(0));

        var response = noticeService.create(new NoticeCreateRequest(
            "[SUMMER] 선행 안내", "본문", NoticeScope.CLASS, 3L, false, false, List.of()));

        assertThat(response.publishedAt()).isNull();
        assertThat(response.classRoomName()).isEqualTo("고2 심화반");
    }

    @Test
    @DisplayName("제목만 고쳐도 고정이 풀리지 않는다")
    void 부분_수정은_나머지를_건드리지_않는다() {
        Notice pinned = notice(15L, NoticeScope.ALL, null);
        ReflectionTestUtils.setField(pinned, "pinned", true);
        given(noticeRepository.findWithClassRoom(15L)).willReturn(Optional.of(pinned));

        var response = noticeService.update(15L,
            new NoticeUpdateRequest("바뀐 제목", null, null, null, null, null, null));

        assertThat(response.title()).isEqualTo("바뀐 제목");
        assertThat(response.content()).isEqualTo("본문");
        assertThat(response.pinned()).isTrue();
    }

    // ---------- 발행 ----------

    @Test
    @DisplayName("발행하면 publishedAt이 채워지고, 다시 눌러도 최초 시각을 유지한다")
    void 재발행은_최초_시각을_유지한다() {
        given(noticeRepository.findWithClassRoom(15L))
            .willReturn(Optional.of(notice(15L, NoticeScope.ALL, null)));

        var first = noticeService.publish(15L);
        var second = noticeService.publish(15L);

        assertThat(first.publishedAt()).isNotNull();
        // 목록 정렬 기준이라 흔들리면 공지 순서가 바뀐다
        assertThat(second.publishedAt()).isEqualTo(first.publishedAt());
    }

    // ---------- 학생만 보기 ----------

    @Test
    @DisplayName("학부모가 목록을 보면 parentView=true로 조회한다")
    void 학부모_목록은_parentView가_true다() {
        Fixtures.login(Fixtures.parentUser(9L));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(any(), any(), eq(true), any(Pageable.class)))
            .willReturn(Page.empty());

        noticeService.list(88L, PageRequest.of(0, 20));

        verify(noticeRepository).findForStudent(eq(88L), any(), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("학생이 자기 studentId를 붙여 불러도 parentView=false다")
    void 학생이_자기_id를_붙여도_parentView는_false다() {
        // studentId != null을 학부모 판정으로 쓰면 여기서 학생만 공지가 학생에게 사라진다
        Fixtures.login(Fixtures.studentUser(7L));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(any(), any(), eq(false), any(Pageable.class)))
            .willReturn(Page.empty());

        noticeService.list(88L, PageRequest.of(0, 20));

        verify(noticeRepository).findForStudent(eq(88L), any(), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("홈 배너 개수에도 학부모 필터가 걸린다")
    void 홈_배너_개수에도_필터가_걸린다() {
        // HomeService가 학부모 홈에서 이 메서드를 그대로 쓴다.
        // 여기를 빠뜨리면 목록에서 가려진 공지가 배너에서 샌다
        Fixtures.login(Fixtures.parentUser(9L));
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.countForStudent(any(), any(), eq(true))).willReturn(2L);

        assertThat(noticeService.countFor(88L)).isEqualTo(2L);

        verify(noticeRepository).countForStudent(eq(88L), any(), eq(true));
    }

    @Test
    @DisplayName("홈 배너 목록에도 학부모 필터가 걸린다")
    void 홈_배너_목록에도_필터가_걸린다() {
        Fixtures.login(Fixtures.parentUser(9L));
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findRecentForStudent(any(), any(), eq(true), any(Pageable.class)))
            .willReturn(List.of());

        noticeService.recentFor(88L);

        verify(noticeRepository)
            .findRecentForStudent(eq(88L), any(), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("상세에도 학부모 필터가 걸린다")
    void 상세에도_필터가_걸린다() {
        Fixtures.login(Fixtures.parentUser(9L));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(eq(5L), any(), any(), eq(true)))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.detail(5L, 88L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(noticeRepository).findForStudent(eq(5L), eq(88L), any(), eq(true));
    }

    @Test
    @DisplayName("수업일 변경이 만든 개인 공지는 학부모에게 그대로 간다")
    void 개인_공지는_학부모에게_간다() {
        // publishedForStudent가 studentsOnly를 건드리지 않아야 한다.
        // 학부모가 못 받으면 변경 알림 자체가 무의미하다
        Notice personal = Notice.publishedForStudent(
            "수업일이 바뀌었습니다", "8/25 → 8/26", child,
            Fixtures.teacherEntity(1L), java.time.OffsetDateTime.now());

        assertThat(personal.isStudentsOnly()).isFalse();
    }

    @Test
    @DisplayName("선생님이 학생만 보기로 공지를 만든다")
    void 학생만_보기로_공지를_만든다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(noticeRepository.save(any(Notice.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        var response = noticeService.create(new NoticeCreateRequest(
            "이번 주 교재", "첨부 확인하세요", NoticeScope.CLASS, 3L, false, true, List.of()));

        assertThat(response.studentsOnly()).isTrue();
    }

    @Test
    @DisplayName("수정에서 studentsOnly가 null이면 그대로 둔다")
    void studentsOnly가_null이면_유지된다() {
        Notice existing = notice(5L, NoticeScope.CLASS, classRoom, true);
        given(noticeRepository.findWithClassRoom(5L)).willReturn(Optional.of(existing));

        var response = noticeService.update(5L,
            new NoticeUpdateRequest("제목만 수정", null, null, null, null, null, null));

        assertThat(response.studentsOnly()).isTrue();
    }

    // ---------- 첨부 ----------

    @Test
    @DisplayName("허용하지 않는 확장자면 400이다")
    void 허용하지_않는_확장자는_400이다() {
        given(materialKeys.extensionOf("악성.exe")).willReturn(null);

        assertThatThrownBy(() -> noticeService.issueAttachmentUploadUrl(
            new MaterialUploadUrlRequest("악성.exe", 1024L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("50MB를 넘으면 413이다")
    void 용량_초과는_413이다() {
        given(materialKeys.extensionOf("교재.pdf")).willReturn("pdf");

        assertThatThrownBy(() -> noticeService.issueAttachmentUploadUrl(
            new MaterialUploadUrlRequest("교재.pdf", MaterialKeys.MAX_BYTES + 1)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("남이 발급받은 s3Key는 400이다")
    void 서명이_안_맞는_s3Key는_400이다() {
        // 대조를 빼면 버킷 내 임의 경로를 첨부로 등록할 수 있다
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(materialKeys.matches("materials/2026/08/남의키.pdf", 1L)).willReturn(false);

        assertThatThrownBy(() -> noticeService.create(new NoticeCreateRequest(
            "제목", "본문", NoticeScope.CLASS, 3L, false, true,
            List.of(new NoticeAttachmentRequest(
                "materials/2026/08/남의키.pdf", "교재.pdf", 1024L)))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("첨부가 6개면 409다")
    void 첨부_6개는_409다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(materialKeys.matches(any(), eq(1L))).willReturn(true);

        List<NoticeAttachmentRequest> six = java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> new NoticeAttachmentRequest(
                "materials/2026/08/key" + i + ".pdf", "교재" + i + ".pdf", 1024L))
            .toList();

        assertThatThrownBy(() -> noticeService.create(new NoticeCreateRequest(
            "제목", "본문", NoticeScope.CLASS, 3L, false, true, six)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("공지를 지우면 마지막 참조인 S3 객체를 지운다")
    void 공지를_지우면_S3_객체도_지운다() {
        // ON DELETE CASCADE는 행만 지운다. 세지 않고 지우면 남은 참조가 깨진다
        Notice existing = notice(5L, NoticeScope.CLASS, classRoom, true);
        given(noticeRepository.findWithClassRoom(5L)).willReturn(Optional.of(existing));
        given(attachmentRepository.findByNoticeIdOrderBySortOrder(5L)).willReturn(List.of(
            attachment("materials/2026/08/a.pdf"), attachment("materials/2026/08/b.pdf")));
        given(attachmentRepository.countByS3Key("materials/2026/08/a.pdf")).willReturn(0L);
        given(attachmentRepository.countByS3Key("materials/2026/08/b.pdf")).willReturn(1L);

        noticeService.delete(5L);

        verify(presignedUrlProvider).deleteQuietly("materials/2026/08/a.pdf");
        verify(presignedUrlProvider, never()).deleteQuietly("materials/2026/08/b.pdf");
    }

    @Test
    @DisplayName("학부모는 학생만 공지의 첨부 다운로드 URL을 못 받는다")
    void 학부모는_학생만_공지의_첨부를_못_받는다() {
        // 목록에서 가려졌다고 안심하면 안 된다. URL을 직접 치면 목록을 거치지 않는다
        Fixtures.login(Fixtures.parentUser(9L));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(child);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(noticeRepository.findForStudent(eq(5L), any(), any(), eq(true)))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.attachmentDownloadUrl(5L, 11L, 88L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    private NoticeAttachment attachment(String s3Key) {
        return NoticeAttachment.of(notice(5L, NoticeScope.CLASS, classRoom),
            s3Key, "교재.pdf", 1024L, (short) 0);
    }
}
