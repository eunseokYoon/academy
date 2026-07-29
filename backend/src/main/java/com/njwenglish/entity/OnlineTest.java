package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ScoreType;
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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시험은 종이로 본다. 학생은 종이 시험지를 풀고 답만 웹에 입력한다.
 * 문제지 파일은 저장하지 않는다. 서버가 가진 건 정답 배열과 해설지뿐이다.
 *
 * <p><b>correctChoices와 answerS3Key를 응시 화면 응답에 넣지 마라.</b>
 * null로 비우지도 마라. 응시용 DTO에서 필드 자체를 빼라.
 * 응답에 들어가는 순간 개발자 도구에서 정답이 그대로 보인다.
 *
 * <p>scoreType이 채워져 있으면 채점 결과가 scores로 자동 반영된다.
 * 그래서 subject가 필수다 (scores.subject가 NOT NULL).
 */
@Entity
@Table(name = "online_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnlineTest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "question_count", nullable = false)
    private Short questionCount;

    @Column(name = "choice_count", nullable = false)
    private Short choiceCount;

    /** 길이가 questionCount와 같아야 한다 (ck_online_tests_keylen). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "correct_choices", nullable = false)
    private Short[] correctChoices;

    /** null이면 균등 배점. 값이 있으면 길이가 questionCount와 같아야 한다. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "points")
    private Short[] points;

    /** 해설·정답지. 채점 후에만 내려준다. */
    @Column(name = "answer_s3_key", length = 500)
    private String answerS3Key;

    /** null이면 연습용이라 성적에 남지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", length = 20)
    private ScoreType scoreType;

    /** scoreType이 있으면 필수. 코드에서 지어내지 말고 T-14 입력값을 그대로 복사한다. */
    @Column(length = 50)
    private String subject;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "week", nullable = false)
    private Short week;

    @Column(name = "opens_at")
    private OffsetDateTime opensAt;

    @Column(name = "closes_at")
    private OffsetDateTime closesAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
