package com.njwenglish.repository;

import com.njwenglish.entity.Notice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <b>parentView 조건은 넷 모두에 있어야 한다.</b> 목록·상세만 막으면 HomeService가
 * 학부모 홈 배너에 쓰는 countForStudent·findRecentForStudent로 그대로 샌다.
 *
 * <p>조건을 n에 직접 건다. n.classRoom.id 같은 경로를 새로 만들지 마라 —
 * 암묵적 INNER JOIN이 생겨 다른 scope의 공지가 통째로 사라진다.
 */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 학생·학부모 목록. 조건이 세 개고 <b>전부 빠뜨리기 쉽다.</b>
     *
     * <ol>
     *   <li>publishedAt IS NOT NULL — 작성 중인 초안이 학부모에게 보이면 안 된다
     *   <li>ALL 분기 — 반 조건만 쓰면 전체 공지가 아무에게도 안 보인다 (class_room_id가 NULL)
     *   <li>STUDENT 분기 — 그 학생 본인 공지. 수업일 변경 안내가 여기로 온다
     * </ol>
     *
     * <p>조인이 둘 다 LEFT인 이유도 같다. n.classRoom.id나 n.student.id로 경로를 타면
     * 암묵적 INNER JOIN이 생겨 다른 scope의 공지가 통째로 사라진다.
     *
     * <p><b>studentId 분기는 대상 그 자체다.</b> 호출부가 넘기는 값이 권한 검증을 통과한
     * 학생인지 반드시 확인해라 — 아무 숫자나 들어오면 남의 개인 공지가 보인다.
     *
     * <p>정렬은 pinned DESC, publishedAt DESC다. 고정 공지가 항상 위에 온다.
     *
     * <p>classRoomIds는 빈 컬렉션이면 안 된다. 호출부에서 더미 값을 넣는다.
     */
    @Query(value = """
        SELECT n FROM Notice n
        LEFT JOIN n.classRoom c
        LEFT JOIN n.student s
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds OR s.id = :studentId)
          AND (:parentView = false OR n.studentsOnly = false)
        ORDER BY n.pinned DESC, n.publishedAt DESC, n.id DESC
        """,
        countQuery = """
        SELECT COUNT(n) FROM Notice n
        LEFT JOIN n.classRoom c
        LEFT JOIN n.student s
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds OR s.id = :studentId)
          AND (:parentView = false OR n.studentsOnly = false)
        """)
    Page<Notice> findForStudent(@Param("studentId") Long studentId,
                                @Param("classRoomIds") Collection<Long> classRoomIds,
                                @Param("parentView") boolean parentView,
                                Pageable pageable);

    /** P-1·S-1 홈의 공지 개수. 목록과 같은 조건이어야 숫자와 목록이 어긋나지 않는다. */
    @Query("""
        SELECT COUNT(n) FROM Notice n
        LEFT JOIN n.classRoom c
        LEFT JOIN n.student s
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds OR s.id = :studentId)
          AND (:parentView = false OR n.studentsOnly = false)
        """)
    long countForStudent(@Param("studentId") Long studentId,
                         @Param("classRoomIds") Collection<Long> classRoomIds,
                         @Param("parentView") boolean parentView);

    /** 상세. 목록과 같은 조건이라 대상이 아닌 공지는 조회되지 않는다(404). */
    @Query("""
        SELECT n FROM Notice n
        LEFT JOIN FETCH n.classRoom c
        LEFT JOIN n.student s
        WHERE n.id = :noticeId
          AND n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds OR s.id = :studentId)
          AND (:parentView = false OR n.studentsOnly = false)
        """)
    Optional<Notice> findForStudent(@Param("noticeId") Long noticeId,
                                    @Param("studentId") Long studentId,
                                    @Param("classRoomIds") Collection<Long> classRoomIds,
                                    @Param("parentView") boolean parentView);

    /**
     * T-10 목록. 초안까지 전부 보여준다 — 선생님이 작성 중인 글을 찾을 곳이 여기뿐이다.
     * 정렬은 최신 작성순이다. 발행 시각으로 정렬하면 초안이 어디로 갈지 정해지지 않는다.
     *
     * <p>수업일 변경 승인이 만든 개인 공지도 여기 섞여 나온다. 그게 선생님이 그 알림을
     * 확인하는 경로다.
     */
    @Query(value = """
        SELECT n FROM Notice n LEFT JOIN FETCH n.classRoom c LEFT JOIN FETCH n.student s
        ORDER BY n.pinned DESC, n.createdAt DESC, n.id DESC
        """,
        countQuery = "SELECT COUNT(n) FROM Notice n")
    Page<Notice> findAllForTeacher(Pageable pageable);

    @Query("""
        SELECT n FROM Notice n LEFT JOIN FETCH n.classRoom LEFT JOIN FETCH n.student
        WHERE n.id = :noticeId
        """)
    Optional<Notice> findWithClassRoom(@Param("noticeId") Long noticeId);

    /** P-1 홈의 최근 공지. 목록 상단 몇 건만 필요해 Pageable로 자른다. */
    @Query("""
        SELECT n FROM Notice n
        LEFT JOIN n.classRoom c
        LEFT JOIN n.student s
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds OR s.id = :studentId)
          AND (:parentView = false OR n.studentsOnly = false)
        ORDER BY n.pinned DESC, n.publishedAt DESC, n.id DESC
        """)
    List<Notice> findRecentForStudent(@Param("studentId") Long studentId,
                                      @Param("classRoomIds") Collection<Long> classRoomIds,
                                      @Param("parentView") boolean parentView,
                                      Pageable pageable);
}
