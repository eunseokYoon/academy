package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 질의응답 게시판의 한 행. <b>질문과 답글이 같은 엔티티다</b> — parent가 null이면 질문,
 * 아니면 그 질문에 달린 답글이다.
 *
 * <p>이 구조의 위험은 답글 행에 제목·공개여부가 섞여 들어가는 것이다. 그래서
 * <b>setter를 만들지 않고 팩토리를 셋으로 나눴다</b> — 답글을 만드는 경로에는 title·isPublic을
 * 넣을 자리가 아예 없다. DB의 ck_qna_posts_shape가 같은 규칙을 한 번 더 막는다.
 *
 * <p>답글의 작성자는 학생이거나 선생님 중 <b>정확히 하나</b>다. 팩토리를 둘로 나눈 이유가
 * 그것이다 — nullable 둘을 받는 팩토리 하나면 둘 다 null인 호출을 막을 방법이 없다.
 *
 * <p>대댓글은 없다. 답글의 parent는 항상 질문이어야 하고, 이 검사는 CHECK로 표현할 수 없어
 * QnaService가 한다.
 *
 * <p>content는 사용자 입력 텍스트다. 프론트에서 HTML로 렌더링하지 마라.
 */
@Entity
@Table(name = "qna_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null이면 질문이다. 판정은 isRoot()를 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private QnaPost parent;

    /** 질문에만 값이 있다. 답글의 반은 parent를 따라간다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 질문에만 값이 있다. false면 작성자와 선생님만 본다. */
    @Column(name = "is_public")
    private Boolean isPublic;

    public static QnaPost question(ClassRoom classRoom, Student student, String title,
                                   String content, boolean isPublic) {
        QnaPost post = new QnaPost();
        post.classRoom = classRoom;
        post.student = student;
        post.title = title;
        post.content = content;
        post.isPublic = isPublic;
        return post;
    }

    public static QnaPost answerByStudent(QnaPost parent, Student student, String content) {
        QnaPost post = new QnaPost();
        post.parent = parent;
        post.student = student;
        post.content = content;
        return post;
    }

    public static QnaPost answerByTeacher(QnaPost parent, Teacher teacher, String content) {
        QnaPost post = new QnaPost();
        post.parent = parent;
        post.teacher = teacher;
        post.content = content;
        return post;
    }

    /** 질문인지. 이 판정의 정본이다 — 호출부에서 parent == null을 직접 쓰지 마라. */
    public boolean isRoot() {
        return parent == null;
    }

    public void editQuestion(String title, String content, boolean isPublic) {
        this.title = title;
        this.content = content;
        this.isPublic = isPublic;
    }

    public void editAnswer(String content) {
        this.content = content;
    }

    /** student는 답글에서 null일 수 있다(선생님 답글). null 검사를 빼지 마라. */
    public boolean isWrittenByStudent(Long studentId) {
        return student != null && student.getId().equals(studentId);
    }

    public boolean isWrittenByTeacher(Long teacherId) {
        return teacher != null && teacher.getId().equals(teacherId);
    }

    /** 질문이면 자기 반, 답글이면 부모의 반. */
    public ClassRoom classRoomOfThread() {
        return isRoot() ? classRoom : parent.getClassRoom();
    }

    /** 질문이면 자기 자신, 답글이면 부모. 권한 판정은 항상 질문 기준이다. */
    public QnaPost root() {
        return isRoot() ? this : parent;
    }
}
