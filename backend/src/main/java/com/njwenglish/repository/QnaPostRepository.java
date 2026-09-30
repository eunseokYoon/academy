package com.njwenglish.repository;

import com.njwenglish.entity.QnaPost;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <b>parent IS NULL은 이 파일 밖으로 나가지 않는다.</b> 질문과 답글이 한 테이블에 살기 때문에
 * 그 조건이 여기저기 흩어지면 어느 한 곳에서 답글이 목록에 섞여 나온다. 판정이 필요하면
 * QnaPost.isRoot()를 쓰고, 조회는 여기 있는 메서드 이름으로 한다.
 */
public interface QnaPostRepository extends JpaRepository<QnaPost, Long> {

    /**
     * 학생 목록. 내가 속한 반의 질문 중 <b>공개글이거나 내가 쓴 글</b>만 나온다.
     *
     * <p>q.student.id는 암묵적 INNER JOIN이지만 질문 행의 student_id는 NOT NULL이라
     * (ck_qna_posts_shape) 사라지는 행이 없다. 답글에는 쓰지 마라.
     */
    @EntityGraph(attributePaths = {"classRoom", "student"})
    @Query("""
        SELECT q FROM QnaPost q
        WHERE q.parent IS NULL
          AND q.classRoom.id IN :classRoomIds
          AND (q.isPublic = true OR q.student.id = :studentId)
        ORDER BY q.createdAt DESC
        """)
    Page<QnaPost> findRootsForStudent(@Param("classRoomIds") Collection<Long> classRoomIds,
                                      @Param("studentId") Long studentId,
                                      Pageable pageable);

    /** 선생님 목록. 비공개글도 전부 나온다. classRoomIds가 전체 반이면 필터가 없는 것과 같다. */
    @EntityGraph(attributePaths = {"classRoom", "student"})
    @Query("""
        SELECT q FROM QnaPost q
        WHERE q.parent IS NULL
          AND q.classRoom.id IN :classRoomIds
        ORDER BY q.createdAt DESC
        """)
    Page<QnaPost> findRootsForTeacher(@Param("classRoomIds") Collection<Long> classRoomIds,
                                      Pageable pageable);

    /** T-1 「새 질문」. 선생님이 게시판을 마지막으로 연 뒤에 올라온 질문 수다(답글은 세지 않는다). */
    @Query("""
        SELECT COUNT(q) FROM QnaPost q
        WHERE q.parent IS NULL
          AND q.createdAt > :since
        """)
    long countRootsCreatedAfter(@Param("since") OffsetDateTime since);

    /** 상세의 답글. 대화 순서라 오름차순이다. */
    @EntityGraph(attributePaths = {"student", "teacher"})
    @Query("SELECT q FROM QnaPost q WHERE q.parent.id = :postId ORDER BY q.createdAt ASC")
    List<QnaPost> findAnswersByPostId(@Param("postId") Long postId);

    /** 목록 카드의 답글 수. 20행마다 쿼리를 날리지 않으려고 한 번에 가져온다. */
    @Query("""
        SELECT q.parent.id AS postId, COUNT(q) AS answerCount
        FROM QnaPost q
        WHERE q.parent.id IN :postIds
        GROUP BY q.parent.id
        """)
    List<AnswerCountRow> countAnswersByPostIds(@Param("postIds") Collection<Long> postIds);

    @EntityGraph(attributePaths = {"classRoom", "student", "parent"})
    Optional<QnaPost> findWithAuthorById(Long id);

    interface AnswerCountRow {
        Long getPostId();

        long getAnswerCount();
    }
}
