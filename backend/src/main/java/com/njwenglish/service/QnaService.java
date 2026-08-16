package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.QnaMediaKeys;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.qna.QnaAnswerResponse;
import com.njwenglish.dto.qna.QnaDetailResponse;
import com.njwenglish.dto.qna.QnaPhotoResponse;
import com.njwenglish.dto.qna.QnaSummaryResponse;
import com.njwenglish.entity.QnaPhoto;
import com.njwenglish.entity.QnaPost;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.QnaPhotoRepository;
import com.njwenglish.repository.QnaPostRepository;
import com.njwenglish.repository.TeacherRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 질의응답 게시판. 학생 경로와 선생님 경로가 한 서비스에 있다.
 *
 * <p><b>학생의 열람 판정은 readableRoot 한 곳을 지난다.</b> 목록에서 안 보이는 postId를
 * 직접 호출하는 경로가 항상 남기 때문이다 — 화면에서 카드를 안 그리는 건 안내일 뿐이다.
 *
 * <p>상태(답변 완료·미답변)를 만들지 마라. 확정 사항이다. 강사가 1명이라 200명분 상태를
 * 닫는 절차가 그대로 병목이 된다.
 */
@Service
@RequiredArgsConstructor
public class QnaService {

    /** 내가 속한 반이 하나도 없을 때 IN ()으로 문법 오류가 나는 걸 막는다. */
    private static final List<Long> NO_CLASS_ROOM = List.of(-1L);

    private static final int MAX_PHOTOS = 5;

    private final QnaPostRepository qnaPostRepository;
    private final QnaPhotoRepository qnaPhotoRepository;
    private final ClassRoomRepository classRoomRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final PresignedUrlProvider presignedUrlProvider;
    private final QnaMediaKeys qnaMediaKeys;

    // ---------- 학생 ----------

    /** classRoomId가 null이면 내가 속한 반 전체다. 학생은 반을 여러 개 가질 수 있다. */
    @Transactional(readOnly = true)
    public PageResponse<QnaSummaryResponse> myQuestions(Long classRoomId, Pageable pageable) {
        Student me = studentAccessGuard.requireSelf();
        List<Long> classRoomIds = myClassRoomIds(me.getId(), classRoomId);

        Page<QnaPost> page =
            qnaPostRepository.findRootsForStudent(classRoomIds, me.getId(), pageable);
        return PageResponse.from(toSummaries(page, me.getId()));
    }

    @Transactional(readOnly = true)
    public QnaDetailResponse myQuestion(Long postId) {
        Student me = studentAccessGuard.requireSelf();
        QnaPost root = readableRoot(postId, me);
        return toDetail(root, me.getId(), null);
    }

    // ---------- 선생님 ----------

    /** classRoomId가 null이면 전체 반이다. 비공개글도 전부 나온다. */
    @Transactional(readOnly = true)
    public PageResponse<QnaSummaryResponse> questions(Long classRoomId, Pageable pageable) {
        List<Long> classRoomIds = classRoomId != null
            ? List.of(classRoomId)
            : classRoomRepository.findAll().stream().map(c -> c.getId()).toList();
        if (classRoomIds.isEmpty()) {
            classRoomIds = NO_CLASS_ROOM;
        }

        Page<QnaPost> page = qnaPostRepository.findRootsForTeacher(classRoomIds, pageable);
        return PageResponse.from(toSummaries(page, null));
    }

    @Transactional(readOnly = true)
    public QnaDetailResponse question(Long postId) {
        QnaPost root = findRoot(postId);
        return toDetail(root, null, currentTeacherId());
    }

    // ---------- 내부 ----------

    /**
     * 학생의 열람 판정 정본. 내 반의 질문이고, 공개글이거나 내가 쓴 글이어야 한다.
     * <b>이 검사를 컨트롤러나 다른 메서드로 흩뿌리지 마라.</b>
     */
    private QnaPost readableRoot(Long postId, Student me) {
        QnaPost root = findRoot(postId);
        boolean inMyClass = enrollmentRepository.findActiveClassRoomIds(me.getId())
            .contains(root.getClassRoom().getId());
        boolean visible = Boolean.TRUE.equals(root.getIsPublic())
            || root.isWrittenByStudent(me.getId());

        if (!inMyClass || !visible) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        return root;
    }

    /** postId가 답글을 가리키면 400이다. 상세는 질문으로만 연다. */
    private QnaPost findRoot(Long postId) {
        QnaPost post = qnaPostRepository.findWithAuthorById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!post.isRoot()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return post;
    }

    private List<Long> myClassRoomIds(Long studentId, Long classRoomId) {
        List<Long> ids = enrollmentRepository.findActiveClassRoomIds(studentId);
        if (classRoomId != null) {
            // 내 반이 아닌 반을 지정하면 빈 결과다. 403으로 존재 여부를 알려줄 이유가 없다
            ids = ids.stream().filter(classRoomId::equals).toList();
        }
        return ids.isEmpty() ? NO_CLASS_ROOM : ids;
    }

    private Page<QnaSummaryResponse> toSummaries(Page<QnaPost> page, Long myStudentId) {
        List<Long> ids = page.getContent().stream().map(QnaPost::getId).toList();
        Map<Long, Long> answerCounts = ids.isEmpty()
            ? Map.of()
            : qnaPostRepository.countAnswersByPostIds(ids).stream()
                .collect(Collectors.toMap(
                    QnaPostRepository.AnswerCountRow::getPostId,
                    QnaPostRepository.AnswerCountRow::getAnswerCount));
        Map<Long, List<QnaPhoto>> photos = ids.isEmpty()
            ? Map.of()
            : qnaPhotoRepository.findByPostIds(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getPost().getId()));

        return page.map(post -> new QnaSummaryResponse(
            post.getId(),
            post.getTitle(),
            post.getStudent().getName(),
            post.getClassRoom().getId(),
            post.getClassRoom().getName(),
            Boolean.TRUE.equals(post.getIsPublic()),
            myStudentId != null && post.isWrittenByStudent(myStudentId),
            !photos.getOrDefault(post.getId(), List.of()).isEmpty(),
            answerCounts.getOrDefault(post.getId(), 0L),
            post.getCreatedAt()));
    }

    /**
     * myStudentId·myTeacherId 중 하나만 채워 부른다. editable은 화면에서 버튼을 그릴지의
     * 근거일 뿐이고, 실제 차단은 쓰기 경로가 다시 한다.
     */
    private QnaDetailResponse toDetail(QnaPost root, Long myStudentId, Long myTeacherId) {
        List<QnaPost> answers = qnaPostRepository.findAnswersByPostId(root.getId());

        List<Long> ids = new java.util.ArrayList<>();
        ids.add(root.getId());
        answers.forEach(a -> ids.add(a.getId()));
        Map<Long, List<QnaPhoto>> photos = qnaPhotoRepository.findByPostIds(ids).stream()
            .collect(Collectors.groupingBy(p -> p.getPost().getId()));

        return new QnaDetailResponse(
            root.getId(),
            root.getTitle(),
            root.getStudent().getName(),
            root.getClassRoom().getId(),
            root.getClassRoom().getName(),
            Boolean.TRUE.equals(root.getIsPublic()),
            root.getContent(),
            toPhotoResponses(photos.get(root.getId())),
            myStudentId != null && root.isWrittenByStudent(myStudentId),
            root.getCreatedAt(),
            answers.stream()
                .map(a -> new QnaAnswerResponse(
                    a.getId(),
                    a.getTeacher() != null ? "선생님" : a.getStudent().getName(),
                    a.getTeacher() != null,
                    a.getContent(),
                    toPhotoResponses(photos.get(a.getId())),
                    (myStudentId != null && a.isWrittenByStudent(myStudentId))
                        || (myTeacherId != null && a.isWrittenByTeacher(myTeacherId)),
                    a.getCreatedAt()))
                .toList());
    }

    private List<QnaPhotoResponse> toPhotoResponses(List<QnaPhoto> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
            .sorted(Comparator.comparing(QnaPhoto::getSortOrder))
            .map(p -> new QnaPhotoResponse(p.getId(), presignedUrlProvider.readUrl(p.getS3Key())))
            .toList();
    }

    private Long currentTeacherId() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
            .getId();
    }
}
