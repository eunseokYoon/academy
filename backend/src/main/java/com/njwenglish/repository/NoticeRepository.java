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

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 학생·학부모 목록. 조건이 두 개고 <b>둘 다 빠뜨리기 쉽다.</b>
     *
     * <ol>
     *   <li>publishedAt IS NOT NULL — 작성 중인 초안이 학부모에게 보이면 안 된다
     *   <li>ALL 분기 — 반 조건만 쓰면 전체 공지가 아무에게도 안 보인다 (class_room_id가 NULL)
     * </ol>
     *
     * <p>조인이 LEFT인 이유도 2번이다. n.classRoom.id로 경로를 타면 암묵적 INNER JOIN이
     * 생겨 scope=ALL 공지가 사라진다.
     *
     * <p>정렬은 pinned DESC, publishedAt DESC다. 고정 공지가 항상 위에 온다.
     *
     * <p>classRoomIds는 빈 컬렉션이면 안 된다. 호출부에서 더미 값을 넣는다.
     */
    @Query(value = """
        SELECT n FROM Notice n
        LEFT JOIN n.classRoom c
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds)
        ORDER BY n.pinned DESC, n.publishedAt DESC, n.id DESC
        """,
        countQuery = """
        SELECT COUNT(n) FROM Notice n
        LEFT JOIN n.classRoom c
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds)
        """)
    Page<Notice> findForStudent(@Param("classRoomIds") Collection<Long> classRoomIds,
                                Pageable pageable);

    /** P-1·S-1 홈의 공지 개수. 목록과 같은 조건이어야 숫자와 목록이 어긋나지 않는다. */
    @Query("""
        SELECT COUNT(n) FROM Notice n
        LEFT JOIN n.classRoom c
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds)
        """)
    long countForStudent(@Param("classRoomIds") Collection<Long> classRoomIds);

    /** 상세. 목록과 같은 조건이라 대상이 아닌 공지는 조회되지 않는다(404). */
    @Query("""
        SELECT n FROM Notice n
        LEFT JOIN FETCH n.classRoom c
        WHERE n.id = :noticeId
          AND n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds)
        """)
    Optional<Notice> findForStudent(@Param("noticeId") Long noticeId,
                                    @Param("classRoomIds") Collection<Long> classRoomIds);

    /**
     * T-10 목록. 초안까지 전부 보여준다 — 선생님이 작성 중인 글을 찾을 곳이 여기뿐이다.
     * 정렬은 최신 작성순이다. 발행 시각으로 정렬하면 초안이 어디로 갈지 정해지지 않는다.
     */
    @Query(value = """
        SELECT n FROM Notice n LEFT JOIN FETCH n.classRoom c
        ORDER BY n.pinned DESC, n.createdAt DESC, n.id DESC
        """,
        countQuery = "SELECT COUNT(n) FROM Notice n")
    Page<Notice> findAllForTeacher(Pageable pageable);

    @Query("SELECT n FROM Notice n LEFT JOIN FETCH n.classRoom WHERE n.id = :noticeId")
    Optional<Notice> findWithClassRoom(@Param("noticeId") Long noticeId);

    /** P-1 홈의 최근 공지. 목록 상단 몇 건만 필요해 Pageable로 자른다. */
    @Query("""
        SELECT n FROM Notice n
        LEFT JOIN n.classRoom c
        WHERE n.publishedAt IS NOT NULL
          AND (n.scope = 'ALL' OR c.id IN :classRoomIds)
        ORDER BY n.pinned DESC, n.publishedAt DESC, n.id DESC
        """)
    List<Notice> findRecentForStudent(@Param("classRoomIds") Collection<Long> classRoomIds,
                                      Pageable pageable);
}
