package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.dto.homework.FeedbackResponse;
import com.njwenglish.entity.Feedback;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.Teacher;
import com.njwenglish.repository.FeedbackRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-7 피드백. 작성하면 제출물이 CHECKED가 된다.
 *
 * <p>피드백은 <b>학생 화면(S-4)에만</b> 나온다. 학부모는 제출 여부까지만 본다.
 * 별도 발송 과정은 없다 — 저장하면 학생이 다음에 열 때 보인다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final SubmissionRepository submissionRepository;
    private final TeacherRepository teacherRepository;

    @Transactional
    public FeedbackResponse create(Long submissionId, String content) {
        Submission submission = findCheckable(submissionId);
        if (feedbackRepository.findBySubmissionId(submissionId).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Feedback feedback = feedbackRepository.save(
            Feedback.create(submission, currentTeacher(), content));
        submission.check();
        return FeedbackResponse.from(feedback);
    }

    @Transactional
    public FeedbackResponse update(Long submissionId, String content) {
        Feedback feedback = feedbackRepository.findBySubmissionId(submissionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        feedback.edit(content);
        return FeedbackResponse.from(feedback);
    }

    /**
     * 피드백 없이 확인만. 200명 전원에게 글을 쓰는 건 현실적이지 않아서 이 경로가 필요하다.
     * 없으면 선생님이 "잘했어요"를 200번 입력하거나, 아예 확인 처리를 안 하게 된다.
     */
    @Transactional
    public void check(Long submissionId) {
        findCheckable(submissionId).check();
    }

    /**
     * 미제출은 확인할 것이 없다. CHECKED로 바꾸면 미제출 집계에서 사라져
     * 선생님이 독려 대상을 놓친다.
     */
    private Submission findCheckable(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!submission.isSubmitted() && !submission.isChecked()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return submission;
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
