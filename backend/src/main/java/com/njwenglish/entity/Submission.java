package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.HomeworkResult;
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
     * 오프라인 채점 결과. null이 "아직 채점 안 함"이고 화면에 회색 "미채점"으로 뜬다.
     * status(온라인 제출 축)와 독립된 축이다 — GRID에서 ⭕를 받은 학생은
     * status가 영원히 NOT_SUBMITTED로 남는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HomeworkResult result;

    @Column(name = "completion_rate")
    private Short completionRate;

    @Column(name = "resolved_by_resubmission", nullable = false)
    private boolean resolvedByResubmission;

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
     * 상태에 LATE를 두면 "늦게 냈지만 마감을 넘겼다"를 한 값으로 뭉개게 된다.
     *
     * <p>GRID 재제출은 호출부가 곧바로 {@link #resolveByResubmission()}을 이어 부른다.
     * 그러면 result가 DONE이 되어 이 학생은 재제출 대상에서 빠지고, 그 순간부터
     * SubmissionService.findEditableSubmission이 다시 손대는 것을 막는다.
     */
    public void submit(OffsetDateTime now, boolean late) {
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = now;
        this.isLate = late;
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

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
    }

    /**
     * 채점. DONE이 아닌 값으로 바꾸면 재제출 표시를 자동으로 내린다 —
     * 안 그러면 ck_submissions_resolved에 걸려 그리드 저장이 통째로 실패한다.
     * 그리고 그 학생은 다시 재제출 대상으로 돌아온다.
     *
     * <p>퍼센트는 PARTIAL에만 붙는다. 다른 결과와 함께 들어온 값은 버린다.
     */
    public void grade(HomeworkResult result, Short completionRate) {
        this.result = result;
        this.completionRate = result == HomeworkResult.PARTIAL ? completionRate : null;
        if (result != HomeworkResult.DONE) {
            this.resolvedByResubmission = false;
        }
    }

    /**
     * 재제출이 들어왔다. 채점 결과가 ⭕로 올라가고 "재제출" 표시가 붙는다.
     * 선생님 확인 단계는 없다 — 학생이 제출하는 순간 {@code submit} 뒤에 이어 불린다.
     */
    public void resolveByResubmission() {
        this.result = HomeworkResult.DONE;
        this.completionRate = null;
        this.resolvedByResubmission = true;
    }

    /**
     * 이 학생이 온라인으로 다시 내야 하는가. <b>제출 경로를 여는 유일한 근거다.</b>
     * 목록에서 버튼을 안 그리는 것만으로는 부족하다 — URL을 직접 치면 뚫린다.
     */
    public boolean isResubmitTarget() {
        return homework.isResubmitOpen()
            && (result == HomeworkResult.PARTIAL || result == HomeworkResult.NOT_DONE);
    }

}
