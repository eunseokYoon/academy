package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.OnlineTestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 임시 저장은 서버에 한다(출석과 반대). 25문항에 20~30분 걸려서
 * 브라우저가 닫히면 다 날아가기 때문이다. 그래서 IN_PROGRESS 상태가 있다.
 *
 * <p>채점은 획득 배점 / 전체 배점 × 100이다.
 * 100/문항수를 문항마다 더하지 마라. 30문항 만점이 99.99가 된다.
 */
@Entity
@Table(name = "online_test_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnlineTestSubmission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_test_id", nullable = false)
    private OnlineTest onlineTest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** 학생 답. 미체크는 null 요소이며 오답 처리한다 (감점 없음). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "chosen_choices", nullable = false)
    private Short[] chosenChoices;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnlineTestStatus status;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    /** 100점 환산 */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "correct_count")
    private Short correctCount;
}
