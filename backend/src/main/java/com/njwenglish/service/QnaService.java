package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.s3.QnaMediaKeys;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.qna.QnaAnswerRequest;
import com.njwenglish.dto.qna.QnaAnswerResponse;
import com.njwenglish.dto.qna.QnaDetailResponse;
import com.njwenglish.dto.qna.QnaPhotoResponse;
import com.njwenglish.dto.qna.QnaQuestionCreateRequest;
import com.njwenglish.dto.qna.QnaQuestionUpdateRequest;
import com.njwenglish.dto.qna.QnaSeenResponse;
import com.njwenglish.dto.qna.QnaSummaryResponse;
import com.njwenglish.dto.qna.QnaUploadUrlRequest;
import com.njwenglish.dto.qna.QnaUploadUrlResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.QnaPhoto;
import com.njwenglish.entity.QnaPost;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.QnaPhotoRepository;
import com.njwenglish.repository.QnaPostRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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

    // ---------- 사진 업로드 ----------

    /**
     * 업로드 URL 발급. <b>서명이 발급받은 사용자에 묶인다</b> — 글이 아직 없어서다.
     * 학생·선생님이 같은 메서드를 쓴다.
     */
    @Transactional(readOnly = true)
    public QnaUploadUrlResponse uploadUrl(QnaUploadUrlRequest request) {
        if (!qnaMediaKeys.isSupportedPhotoType(request.contentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        Long userId = CurrentUser.get().userId();
        String s3Key = qnaMediaKeys.issuePhoto(userId, request.contentType(), LocalDate.now());
        return new QnaUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, request.contentType()), s3Key);
    }

    // ---------- 학생 쓰기 ----------

    @Transactional
    public Long createQuestion(QnaQuestionCreateRequest request) {
        Student me = studentAccessGuard.requireSelf();
        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!enrollmentRepository.findActiveClassRoomIds(me.getId()).contains(classRoom.getId())) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }

        QnaPost saved = qnaPostRepository.save(QnaPost.question(
            classRoom, me, request.title(), request.content(), request.isPublic()));
        attachPhotos(saved, request.s3KeysOrEmpty());
        return saved.getId();
    }

    @Transactional
    public Long answerAsStudent(Long postId, QnaAnswerRequest request) {
        Student me = studentAccessGuard.requireSelf();
        QnaPost root = readableRoot(postId, me);

        QnaPost saved = qnaPostRepository.save(
            QnaPost.answerByStudent(root, me, request.content()));
        attachPhotos(saved, request.s3KeysOrEmpty());
        return saved.getId();
    }

    /** 질문이든 답글이든 같은 메서드다. 한 테이블이라 얻는 이득이다. */
    @Transactional
    public void updateAsStudent(Long id, QnaQuestionUpdateRequest request) {
        Student me = studentAccessGuard.requireSelf();
        QnaPost post = qnaPostRepository.findWithAuthorById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!post.isWrittenByStudent(me.getId())) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        applyUpdate(post, request);
    }

    @Transactional
    public void deleteAsStudent(Long id) {
        Student me = studentAccessGuard.requireSelf();
        QnaPost post = qnaPostRepository.findWithAuthorById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!post.isWrittenByStudent(me.getId())) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        qnaPostRepository.delete(post);
    }

    // ---------- 선생님 쓰기 ----------

    @Transactional
    public Long answerAsTeacher(Long postId, QnaAnswerRequest request) {
        Teacher teacher = teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        QnaPost root = findRoot(postId);

        QnaPost saved = qnaPostRepository.save(
            QnaPost.answerByTeacher(root, teacher, request.content()));
        attachPhotos(saved, request.s3KeysOrEmpty());
        // 푸시 #6 — 질문한 학생에게만. 학부모에게 보내지 마라(게시판에 학부모 경로가 없다).
        // 학생 답글(answerAsStudent)에는 없다 — 선생님은 알림을 받지 않는다
        if (root.getStudent() != null) {
            eventPublisher.publishEvent(
                PushEvent.of(PushTopic.QNA_REPLY, root.getStudent().getId(), root.getId()));
        }
        return saved.getId();
    }

    /** 선생님은 <b>본인 답글만</b> 고친다. 학생 글을 대신 고치면 학생이 안 쓴 말이 남는다. */
    @Transactional
    public void updateAsTeacher(Long id, QnaQuestionUpdateRequest request) {
        Long teacherId = currentTeacherId();
        QnaPost post = qnaPostRepository.findWithAuthorById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!post.isWrittenByTeacher(teacherId)) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        applyUpdate(post, request);
    }

    /** 삭제는 전부 가능하다. 부적절한 글을 내릴 사람이 선생님뿐이다. */
    @Transactional
    public void deleteAsTeacher(Long id) {
        QnaPost post = qnaPostRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        qnaPostRepository.delete(post);
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

    /**
     * 선생님이 게시판을 열었다(T-1 「새 질문」을 0으로 만든다). 앞서 연 시각을 돌려준다 —
     * 화면이 그 뒤에 올라온 질문에 「새 글」을 붙인다. 글마다 읽음 상태를 두지 않는다.
     */
    @Transactional
    public QnaSeenResponse markSeenByTeacher() {
        Teacher teacher = teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new QnaSeenResponse(teacher.markQnaSeen(OffsetDateTime.now()));
    }

    private Long currentTeacherId() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
            .getId();
    }

    // ---------- 내부 (쓰기) ----------

    /**
     * 답글에 제목·공개여부가 오면 400이다. 통과시켜도 ck_qna_posts_shape가 막지만,
     * 여기서 걸러야 500이 아니라 400이 나간다.
     */
    private void applyUpdate(QnaPost post, QnaQuestionUpdateRequest request) {
        if (post.isRoot()) {
            if (request.title() == null || request.isPublic() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            post.editQuestion(request.title(), request.content(), request.isPublic());
            return;
        }
        if (request.title() != null || request.isPublic() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        post.editAnswer(request.content());
    }

    /**
     * s3Key는 클라이언트가 보내는 값이다. <b>서명을 반드시 대조한다</b> —
     * 안 하면 남이 올린 파일이나 버킷 내 임의 경로를 자기 글에 붙일 수 있다.
     */
    private void attachPhotos(QnaPost post, List<String> s3Keys) {
        if (s3Keys.isEmpty()) {
            return;
        }
        if (s3Keys.size() > MAX_PHOTOS) {
            throw new BusinessException(ErrorCode.QNA_PHOTO_LIMIT_EXCEEDED);
        }
        Long userId = CurrentUser.get().userId();
        short order = 0;
        for (String s3Key : s3Keys) {
            if (!qnaMediaKeys.matchesPhoto(s3Key, userId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            qnaPhotoRepository.save(QnaPhoto.of(post, s3Key, order++, null));
        }
    }
}
