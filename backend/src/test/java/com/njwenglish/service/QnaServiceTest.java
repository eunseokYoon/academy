package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.QnaMediaKeys;
import com.njwenglish.common.security.StudentAccessGuard;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

    private QnaPost question(Long id, ClassRoom classRoom, Student author, String title,
                             boolean isPublic) {
        QnaPost post = QnaPost.question(classRoom, author, title, "본문", isPublic);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
