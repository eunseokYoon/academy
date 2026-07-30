package com.njwenglish.repository;

import com.njwenglish.entity.Material;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    /**
     * S-8 목록. <b>PUBLIC 분기와 반 분기를 OR로 묶는다.</b> 반 조건만 쓰면 전체 공개 자료가
     * 아무에게도 안 보인다 — PUBLIC은 class_room_id가 NULL이라 IN에 걸리지 않는다.
     *
     * <p>조인을 LEFT로 명시한 이유가 그것이다. m.classRoom.id로 경로를 타면 암묵적
     * INNER JOIN이 생겨 class_room_id가 NULL인 PUBLIC 자료가 통째로 사라진다. 에러는 안 난다.
     *
     * <p>classRoomIds는 <b>절대 빈 컬렉션이면 안 된다.</b> 빈 IN ()은 SQL 오류다.
     * 배정 전 학생은 호출부에서 더미 값을 넣어 넘긴다.
     *
     * <p>category는 문자열로 비교한다. enum 파라미터에 IS NULL을 걸면 타입 추론이 갈린다.
     */
    @Query(value = """
        SELECT m FROM Material m
        LEFT JOIN m.classRoom c
        WHERE (m.visibility = 'PUBLIC' OR c.id IN :classRoomIds)
          AND (:category IS NULL OR CAST(m.category AS string) = :category)
        ORDER BY m.createdAt DESC, m.id DESC
        """,
        countQuery = """
        SELECT COUNT(m) FROM Material m
        LEFT JOIN m.classRoom c
        WHERE (m.visibility = 'PUBLIC' OR c.id IN :classRoomIds)
          AND (:category IS NULL OR CAST(m.category AS string) = :category)
        """)
    Page<Material> findForStudent(@Param("classRoomIds") Collection<Long> classRoomIds,
                                  @Param("category") String category,
                                  Pageable pageable);

    /** T-9 목록. 주차·분류가 전부 선택이라 null이면 조건을 통과시킨다. */
    @Query(value = """
        SELECT m FROM Material m
        LEFT JOIN FETCH m.classRoom c
        WHERE (:year IS NULL OR m.year = :year)
          AND (:month IS NULL OR m.month = :month)
          AND (:week IS NULL OR m.week = :week)
          AND (:category IS NULL OR CAST(m.category AS string) = :category)
        ORDER BY m.createdAt DESC, m.id DESC
        """,
        countQuery = """
        SELECT COUNT(m) FROM Material m
        WHERE (:year IS NULL OR m.year = :year)
          AND (:month IS NULL OR m.month = :month)
          AND (:week IS NULL OR m.week = :week)
          AND (:category IS NULL OR CAST(m.category AS string) = :category)
        """)
    Page<Material> search(@Param("year") Short year,
                          @Param("month") Short month,
                          @Param("week") Short week,
                          @Param("category") String category,
                          Pageable pageable);

    @Query("SELECT m FROM Material m LEFT JOIN FETCH m.classRoom WHERE m.id = :materialId")
    Optional<Material> findWithClassRoom(@Param("materialId") Long materialId);

    /**
     * 같은 파일을 여러 반에 준 경우 s3Key가 여러 행에 공유된다.
     * 마지막 행을 지울 때만 S3 객체를 지워야 한다 — 안 세면 다른 반의 자료가 깨진다.
     */
    long countByS3Key(String s3Key);
}
