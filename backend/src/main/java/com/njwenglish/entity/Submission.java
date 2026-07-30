package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.SubmissionStatus;
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

/**
 * 출제 시점에 대상 전원이 NOT_SUBMITTED로 미리 생성된다.
 * 그래서 "행이 있으면 제출함"이 아니다. 학생 삭제 판정 시
 * status &lt;&gt; 'NOT_SUBMITTED'로 봐야 한다. 아니면 아무도 지울 수 없다.
 */
@Entity
@Table(name = "submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "homework_id", nullable = false)
    private Homework homework;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "is_late", nullable = false)
    private boolean isLate;

    /**
     * 영상은 제출물당 최대 1개라 자식 테이블 없이 컬럼으로 둔다.
     * 사진과 달리 브라우저에서 압축할 수 없어 원본이 그대로 올라온다.
     */
    @Column(name = "video_s3_key", length = 500)
    private String videoS3Key;

    @Column(name = "video_bytes")
    private Integer videoBytes;

    /**
     * 학생이 피드백을 마지막으로 열어 본 시각. S-1 홈의 미확인 수를 세는 유일한 근거다.
     * 피드백이 없으면 계속 null이다.
     */
    @Column(name = "feedback_read_at")
    private OffsetDateTime feedbackReadAt;

    /** 출제 시 대상 전원에게 미리 깔린다. 사진은 이 행에 붙어야 하므로 제출 전에도 존재한다. */
    public static Submission notSubmitted(Homework homework, Student student) {
        Submission submission = new Submission();
        submission.homework = homework;
        submission.student = student;
        submission.status = SubmissionStatus.NOT_SUBMITTED;
        submission.isLate = false;
        return submission;
    }

    /**
     * 마감 후 제출도 허용한다(확정 정책). 늦음은 상태가 아니라 is_late 플래그다 —
     * 상태에 LATE를 두면 "늦게 냈지만 확인 완료"를 표현할 수 없다.
     *
     * <p>재제출도 같은 경로다. 마감 전이면 사진을 고쳐 다시 낼 수 있고 상태는 SUBMITTED로 유지된다.
     */
    public void submit(OffsetDateTime now, boolean late) {
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = now;
        this.isLate = late;
    }

    public void check() {
        this.status = SubmissionStatus.CHECKED;
    }

    /** 다시 올리면 이전 것을 덮는다. 호출부가 이전 s3Key를 받아 S3에서 지운다. */
    public String attachVideo(String s3Key, Integer bytes) {
        String previous = this.videoS3Key;
        this.videoS3Key = s3Key;
        this.videoBytes = bytes;
        return previous;
    }

    public String detachVideo() {
        String previous = this.videoS3Key;
        this.videoS3Key = null;
        this.videoBytes = null;
        return previous;
    }

    public boolean hasVideo() {
        return videoS3Key != null;
    }

    /** 선생님이 이미 본 제출물은 학생이 바꿀 수 없다. 본 내용이 뒤바뀌면 안 된다. */
    public boolean isChecked() {
        return status == SubmissionStatus.CHECKED;
    }

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }

    /**
     * 학생이 S-4 상세를 열었다. 피드백이 있을 때만 호출한다 —
     * 피드백이 없는데 찍어 두면 나중에 달린 피드백이 처음부터 확인 처리된다.
     */
    public void markFeedbackRead(OffsetDateTime now) {
        this.feedbackReadAt = now;
    }
}
