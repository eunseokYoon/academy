package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
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
 * <p><b>성적 자동 반영은 없다.</b> 이 테스트는 클리닉 테스트를 오프라인으로 못 보는
 * 학생을 위한 대체본이고, 선생님이 결과를 보고 성적 기입 탭에 직접 적는다.
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

    /**
     * 해설·정답지 여러 장(V27, 2026-09-29). 채점 후에만 내려준다. 전량 교체라
     * orphanRemoval 이 켜져 있다({@link #replaceAnswerFiles}).
     */
    @OneToMany(mappedBy = "onlineTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<OnlineTestAnswerFile> answerFiles = new ArrayList<>();

    /**
     * <b>앞 N문항이 내부지문</b>, 나머지가 외부지문이다. null이면 집계하지 않는다.
     *
     * <p>클리닉 성적이 내부·외부로 나뉘어 있는데 온라인 테스트 결과는 문항 하나로
     * 이어진 배열이다. 이 값이 있으면 결과 화면이 내부·외부 맞힌 개수를 따로 집계해
     * 보여주고, 선생님은 그 숫자를 성적 기입 탭에 옮겨 적기만 하면 된다.
     */
    @Column(name = "internal_question_count")
    private Short internalQuestionCount;

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

    public static OnlineTest create(ClassRoom classRoom, Teacher teacher, String title,
                                    short questionCount, short choiceCount,
                                    Short[] correctChoices, Short[] points,
                                    List<String> answerS3Keys,
                                    Short internalQuestionCount,
                                    short year, short month, short week,
                                    OffsetDateTime opensAt, OffsetDateTime closesAt) {
        OnlineTest test = new OnlineTest();
        test.classRoom = classRoom;
        test.teacher = teacher;
        test.title = title;
        test.questionCount = questionCount;
        test.choiceCount = choiceCount;
        test.correctChoices = correctChoices;
        test.points = points;
        test.replaceAnswerFiles(answerS3Keys);
        test.internalQuestionCount = internalQuestionCount;
        test.year = year;
        test.month = month;
        test.week = week;
        test.opensAt = opensAt;
        test.closesAt = closesAt;
        return test;
    }

    /**
     * 공개 전에만 정답·문항 수를 바꿀 수 있다. 호출부에서 isPublished()를 먼저 검사해라.
     * 공개 후 정답을 고치면 이미 응시한 학생의 점수가 소급 변경된다.
     */
    public void edit(String title, short questionCount, short choiceCount,
                     Short[] correctChoices, Short[] points, List<String> answerS3Keys,
                     Short internalQuestionCount,
                     short year, short month, short week,
                     OffsetDateTime opensAt, OffsetDateTime closesAt) {
        this.title = title;
        this.questionCount = questionCount;
        this.choiceCount = choiceCount;
        this.correctChoices = correctChoices;
        this.points = points;
        replaceAnswerFiles(answerS3Keys);
        this.internalQuestionCount = internalQuestionCount;
        this.year = year;
        this.month = month;
        this.week = week;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    /** 공개 후에도 바꿀 수 있는 값. 정답과 문항 수는 여기 넣지 마라. */
    public void editSchedule(String title, List<String> answerS3Keys,
                             OffsetDateTime opensAt, OffsetDateTime closesAt) {
        this.title = title;
        replaceAnswerFiles(answerS3Keys);
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    /** 해설지 s3Key 목록. 올린 순서다. */
    public List<String> getAnswerS3Keys() {
        return answerFiles.stream().map(OnlineTestAnswerFile::getS3Key).toList();
    }

    /**
     * 해설지를 통째로 바꾼다. 목록이 지금과 같으면 아무것도 안 한다 — 수정할 때마다
     * 행을 지웠다 다시 넣지 않는다. 개수·키 검증은 서비스가 먼저 한다.
     */
    public void replaceAnswerFiles(List<String> s3Keys) {
        List<String> next = s3Keys == null ? List.of() : s3Keys;
        if (next.equals(getAnswerS3Keys())) {
            return;
        }
        answerFiles.clear();
        for (int i = 0; i < next.size(); i++) {
            answerFiles.add(OnlineTestAnswerFile.of(this, next.get(i), (short) i));
        }
    }

    public void publish(OffsetDateTime now) {
        if (publishedAt == null) {
            this.publishedAt = now;
        }
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /** 응시 가능 시각인지. opensAt이 null이면 공개 즉시 열린다. */
    public boolean isOpenAt(OffsetDateTime now) {
        return opensAt == null || !now.isBefore(opensAt);
    }

    /** closesAt이 null이면 마감이 없다. */
    public boolean isClosedAt(OffsetDateTime now) {
        return closesAt != null && now.isAfter(closesAt);
    }
}
