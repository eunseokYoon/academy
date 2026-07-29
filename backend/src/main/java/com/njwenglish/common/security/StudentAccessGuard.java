package com.njwenglish.common.security;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * studentId를 파라미터로 받는 모든 서비스 메서드의 첫 줄은 requireAccessible이다. 예외는 없다.
 *
 * <p>컨트롤러에서 소유권을 직접 검사하지 마라. 컨트롤러마다 구현하면 반드시 한두 곳을
 * 빠뜨리고, 그게 학부모가 URL의 숫자만 바꿔 남의 아이 성적을 보는 사고가 된다.
 */
@Component
@RequiredArgsConstructor
public class StudentAccessGuard {

    private final StudentRepository studentRepository;

    /**
     * 현재 로그인 사용자가 studentId에 접근 가능한지 검증하고 Student를 반환한다.
     * 불가하면 STUDENT_NOT_ACCESSIBLE을 던진다.
     */
    public Student requireAccessible(Long studentId) {
        AuthUser me = CurrentUser.get();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // student.getUser()·getParent()는 가입 전이면 null이다. 반드시 검사할 것
        boolean allowed = switch (me.role()) {
            case TEACHER -> true;
            case STUDENT -> student.getUser() != null
                && student.getUser().getId().equals(me.userId());
            case PARENT -> student.getParent() != null
                && student.getParent().getUser().getId().equals(me.userId());
        };

        if (!allowed) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE);
        }
        return student;
    }

    /** 학생 본인의 Student 엔티티를 반환한다 (STUDENT 역할 전용 API에서 사용). */
    public Student requireSelf() {
        AuthUser me = CurrentUser.get();
        return studentRepository.findByUserId(me.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));
    }
}
