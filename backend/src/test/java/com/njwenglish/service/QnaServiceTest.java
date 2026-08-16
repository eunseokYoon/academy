package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.QnaMediaKeys;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.qna.QnaAnswerRequest;
import com.njwenglish.dto.qna.QnaQuestionCreateRequest;
import com.njwenglish.dto.qna.QnaQuestionUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.QnaPost;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.QnaPhotoRepository;
import com.njwenglish.repository.QnaPostRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 게시판에서 조용히 새는 두 지점을 고정한다.
 *
 * <ol>
 *   <li>목록에 없는 postId 직접 호출 — 남의 비공개 질문이 그대로 열린다
 *   <li>내 반이 아닌 반에 글쓰기 — 반 경계가 무의미해진다
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QnaServiceTest {

    @Mock private QnaPostRepository qnaPostRepository;
    @Mock private QnaPhotoRepository qnaPhotoRepository;
    @Mock private ClassRoomRepository classRoomRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private StudentAccessGuard studentAccessGuard;
    @Mock private PresignedUrlProvider presignedUrlProvider;
    @Mock private QnaMediaKeys qnaMediaKeys;

    private QnaService qnaService;

    private ClassRoom myClass;
    private ClassRoom otherClass;
    private Student me;
    private Student classmate;

    @BeforeEach
    void setUp() {
        qnaService = new QnaService(qnaPostRepository, qnaPhotoRepository, classRoomRepository,
            teacherRepository, enrollmentRepository, studentAccessGuard, presignedUrlProvider,
            qnaMediaKeys);

        myClass = Fixtures.openClassRoom(1L, "A고 2학년 목요일반", "HK7F2Q");
        otherClass = Fixtures.openClassRoom(2L, "B고 3학년 월요일반", "QQ11ZZ");
        me = Fixtures.student(10L, "강민준");
        classmate = Fixtures.student(11L, "이서연");

        Fixtures.login(Fixtures.studentUser(100L));
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(enrollmentRepository.findActiveClassRoomIds(10L)).willReturn(List.of(1L));
        given(qnaPhotoRepository.findByPostIds(anyList())).willReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("학생 목록은 내 반의 공개글과 내가 쓴 비공개글만 나온다")
    void listOnlyMyClassAndVisible() {
        QnaPost open = question(1L, myClass, classmate, "관계대명사 질문", true);
        given(qnaPostRepository.findRootsForStudent(any(), any(), any()))
            .willReturn(new PageImpl<>(List.of(open)));
        given(qnaPostRepository.countAnswersByPostIds(anyList())).willReturn(List.of());

        var page = qnaService.myQuestions(null, PageRequest.of(0, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).authorName()).isEqualTo("이서연");
        assertThat(page.items().get(0).mine()).isFalse();
    }

    @Test
    @DisplayName("남의 비공개 질문은 postId를 직접 넣어도 403이다")
    void privateQuestionOfOthersRejected() {
        QnaPost secret = question(2L, myClass, classmate, "성적 상담", false);
        given(qnaPostRepository.findWithAuthorById(2L)).willReturn(Optional.of(secret));

        assertThatThrownBy(() -> qnaService.myQuestion(2L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("내가 쓴 비공개 질문은 열린다")
    void myPrivateQuestionVisible() {
        QnaPost mine = question(3L, myClass, me, "결석 관련 질문", false);
        given(qnaPostRepository.findWithAuthorById(3L)).willReturn(Optional.of(mine));
        given(qnaPostRepository.findAnswersByPostId(3L)).willReturn(List.of());

        var detail = qnaService.myQuestion(3L);

        assertThat(detail.title()).isEqualTo("결석 관련 질문");
        assertThat(detail.isPublic()).isFalse();
        assertThat(detail.editable()).isTrue();
    }

    @Test
    @DisplayName("내 반이 아닌 반의 공개 질문도 403이다")
    void otherClassQuestionRejected() {
        QnaPost outside = question(4L, otherClass, classmate, "다른 반 질문", true);
        given(qnaPostRepository.findWithAuthorById(4L)).willReturn(Optional.of(outside));

        assertThatThrownBy(() -> qnaService.myQuestion(4L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("선생님은 비공개 질문도 본다")
    void teacherSeesPrivate() {
        Fixtures.login(Fixtures.teacher(1L));
        // question()의 currentTeacherId()가 답글 editable 판정을 위해 이 조회를 반드시 거친다.
        // 스텁이 없으면 Mockito가 기본값 Optional.empty()를 돌려줘 orElseThrow가 터진다.
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        QnaPost secret = question(5L, myClass, classmate, "성적 상담", false);
        given(qnaPostRepository.findWithAuthorById(5L)).willReturn(Optional.of(secret));
        given(qnaPostRepository.findAnswersByPostId(5L)).willReturn(List.of());

        assertThat(qnaService.question(5L).title()).isEqualTo("성적 상담");
    }

    @Test
    @DisplayName("내 반이 아닌 classRoomId를 넘겨도 그 반의 글은 조회되지 않는다")
    void classRoomIdNotMineIsExcluded() {
        given(qnaPostRepository.findRootsForStudent(any(), any(), any()))
            .willReturn(Page.empty());

        qnaService.myQuestions(2L, PageRequest.of(0, 20));

        // 반환값이 비었다는 것만 보면 mock이 mock을 확인하는 꼴이다.
        // 실제로 쿼리에 넘어간 classRoomIds를 캡처해서 2L이 없는지, 그리고 빈 목록 대신
        // NO_CLASS_ROOM 센티넬(-1L)이 들어갔는지를 본다.
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(qnaPostRepository).findRootsForStudent(captor.capture(), any(), any());
        assertThat(captor.getValue()).doesNotContain(2L);
        assertThat(captor.getValue()).containsExactly(-1L);
    }

    @Test
    @DisplayName("활성 반이 하나도 없어도 조회 쿼리에 빈 목록 대신 더미가 들어간다")
    void noActiveClassRoomUsesSentinel() {
        given(enrollmentRepository.findActiveClassRoomIds(10L)).willReturn(List.of());
        given(qnaPostRepository.findRootsForStudent(any(), any(), any()))
            .willReturn(Page.empty());

        qnaService.myQuestions(null, PageRequest.of(0, 20));

        // 빈 리스트를 그대로 IN에 넘기면 쿼리 자체가 깨진다. NO_CLASS_ROOM이 그걸 막는다.
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(qnaPostRepository).findRootsForStudent(captor.capture(), any(), any());
        assertThat(captor.getValue()).containsExactly(-1L);
    }

    @Test
    @DisplayName("내 반이 아닌 반에는 질문을 쓸 수 없다")
    void cannotWriteToOtherClass() {
        given(classRoomRepository.findById(2L)).willReturn(Optional.of(otherClass));

        var request = new QnaQuestionCreateRequest(2L, "제목", "본문", false, List.of());

        assertThatThrownBy(() -> qnaService.createQuestion(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("서명이 맞지 않는 s3Key는 붙일 수 없다")
    void forgedS3KeyRejected() {
        given(classRoomRepository.findById(1L)).willReturn(Optional.of(myClass));
        given(qnaMediaKeys.matchesPhoto(any(), any())).willReturn(false);

        var request = new QnaQuestionCreateRequest(
            1L, "제목", "본문", false, List.of("submissions/2026/08/남의파일.webp"));

        assertThatThrownBy(() -> qnaService.createQuestion(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("사진은 5장까지다")
    void photoLimit() {
        given(classRoomRepository.findById(1L)).willReturn(Optional.of(myClass));
        given(qnaMediaKeys.matchesPhoto(any(), any())).willReturn(true);

        var request = new QnaQuestionCreateRequest(
            1L, "제목", "본문", false, List.of("a", "b", "c", "d", "e", "f"));

        assertThatThrownBy(() -> qnaService.createQuestion(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.QNA_PHOTO_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("남의 글은 수정할 수 없다")
    void cannotEditOthers() {
        QnaPost others = question(6L, myClass, classmate, "남의 글", true);
        given(qnaPostRepository.findWithAuthorById(6L)).willReturn(Optional.of(others));

        var request = new QnaQuestionUpdateRequest("바꾼 제목", "바꾼 본문", true);

        assertThatThrownBy(() -> qnaService.updateAsStudent(6L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("답글에 제목·공개여부를 보내면 400이다")
    void cannotSetTitleOnAnswer() {
        QnaPost root = question(7L, myClass, me, "질문", true);
        QnaPost answer = QnaPost.answerByStudent(root, me, "답글");
        ReflectionTestUtils.setField(answer, "id", 8L);
        given(qnaPostRepository.findWithAuthorById(8L)).willReturn(Optional.of(answer));

        var request = new QnaQuestionUpdateRequest("제목", "본문", true);

        assertThatThrownBy(() -> qnaService.updateAsStudent(8L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("답글의 답글은 만들 수 없다")
    void noNestedAnswer() {
        QnaPost root = question(9L, myClass, me, "질문", true);
        QnaPost answer = QnaPost.answerByStudent(root, me, "답글");
        ReflectionTestUtils.setField(answer, "id", 10L);
        given(qnaPostRepository.findWithAuthorById(10L)).willReturn(Optional.of(answer));

        var request = new QnaAnswerRequest("대댓글", List.of());

        assertThatThrownBy(() -> qnaService.answerAsStudent(10L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("학생은 남의 비공개 질문에 답글을 달 수 없다")
    void cannotAnswerOthersPrivateQuestion() {
        QnaPost secret = question(11L, myClass, classmate, "성적 상담", false);
        given(qnaPostRepository.findWithAuthorById(11L)).willReturn(Optional.of(secret));

        var request = new QnaAnswerRequest("답글", List.of());

        assertThatThrownBy(() -> qnaService.answerAsStudent(11L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("학생은 남의 글을 삭제할 수 없다")
    void cannotDeleteOthers() {
        QnaPost others = question(12L, myClass, classmate, "남의 글", true);
        given(qnaPostRepository.findWithAuthorById(12L)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> qnaService.deleteAsStudent(12L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);

        verify(qnaPostRepository, never()).delete(any());
    }

    @Test
    @DisplayName("선생님은 본인 답글만 수정할 수 있다")
    void teacherCanOnlyEditOwnAnswer() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        QnaPost studentQuestion = question(13L, myClass, me, "질문", true);
        given(qnaPostRepository.findWithAuthorById(13L)).willReturn(Optional.of(studentQuestion));

        var request = new QnaQuestionUpdateRequest("바뀐 제목", "바뀐 본문", true);

        assertThatThrownBy(() -> qnaService.updateAsTeacher(13L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
    }

    private QnaPost question(Long id, ClassRoom classRoom, Student author, String title,
                             boolean isPublic) {
        QnaPost post = QnaPost.question(classRoom, author, title, "본문", isPublic);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
