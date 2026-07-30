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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제출 1건당 피드백 1개다 (submission_id UNIQUE). 수정은 UPDATE로 처리한다.
 * 피드백은 학생에게만 보인다. 학부모에게 노출하지 마라.
 */
@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static Feedback create(Submission submission, Teacher teacher, String content) {
        Feedback feedback = new Feedback();
        feedback.submission = submission;
        feedback.teacher = teacher;
        feedback.content = content;
        return feedback;
    }

    public void edit(String content) {
        this.content = content;
    }
}
