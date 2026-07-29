package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.util.InviteCodes;
import com.njwenglish.common.util.PhoneNumbers;
import com.njwenglish.dto.auth.SignupRequest;
import com.njwenglish.dto.auth.SignupResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.SignupCodeRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세 가지 가입이 이 서비스 하나를 쓴다. 서버가 코드를 보고 판별한다 —
 * signup_codes.code를 먼저 찾고, 없으면 class_rooms.join_code를 찾는다.
 *
 * <p>실패 사유를 세분화하지 않는다. "전화번호가 다릅니다"로 알려주면 코드만 가진 사람이
 * 번호를 추측할 수 있고, 어느 종류의 코드가 틀렸는지 알려주면 판별기가 된다.
 * 1·3·4번 실패와 반 코드 실패는 전부 같은 INVITE_CODE_INVALID다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final SignupCodeRepository signupCodeRepository;
    private final ClassRoomRepository classRoomRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ParentLinkService parentLinkService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String code = InviteCodes.normalize(request.code());

        return signupCodeRepository.findByCode(code)
            .map(signupCode -> byPersonalCode(signupCode, request))
            .orElseGet(() -> byJoinCode(code, request));
    }

    /**
     * 경로 1 — 반 코드. 학생 자가 가입이며 주 경로다.
     *
     * <p>반 코드에는 전화번호 대조가 없다. 20명이 나눠 쓰는 값이라 특정인에게 묶을 수 없어서다.
     * 즉 코드를 아는 사람은 누구나 가입한다. join_code_active 검사가 유일한 방어선이므로
     * 절대 빼지 마라. 등록 기간이 끝나면 선생님이 T-3에서 닫는다.
     */
    private SignupResponse byJoinCode(String code, SignupRequest request) {
        ClassRoom classRoom = classRoomRepository.findByJoinCode(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));

        if (!classRoom.isJoinCodeActive() || classRoom.getStatus() == ClassRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }

        String name = requireName(request.name());
        String phone = requirePhone(request.phone());
        String parentPhone = requirePhone(request.parentPhone());

        // 같은 번호면 login_id가 겹쳐 둘 중 하나는 로그인하지 못한다
        if (phone.equals(parentPhone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        requireUnusedPhone(phone);

        Student student = studentRepository.save(Student.create(name));
        student.linkUser(createAccount(UserRole.STUDENT, phone, name));
        enrollmentRepository.save(Enrollment.create(student, classRoom, LocalDate.now()));

        // 학부모 연결의 시작점. 선생님이 T-2에서 이 코드를 확인해 학부모에게 전달한다
        parentLinkService.issueParentCode(student, parentPhone);

        return SignupResponse.of(UserRole.STUDENT, phone, student.getName(), classRoom.getName());
    }

    /** 경로 2 — 개인 코드. 코드와 전화번호를 모두 확인해야 무작위 대입으로 계정을 만들 수 없다. */
    private SignupResponse byPersonalCode(SignupCode signupCode, SignupRequest request) {
        if (signupCode.isUsed()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_USED);
        }
        String phone = requirePhone(request.phone());
        if (signupCode.isExpired(OffsetDateTime.now())
            || !signupCode.getPhone().equals(phone)) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }

        Student student = signupCode.getStudent();
        UserRole role = signupCode.getTargetRole();

        if (role == UserRole.PARENT) {
            parentLinkService.linkParent(student, phone, requireName(request.name()));
        } else {
            linkStudentAccount(student, phone);
        }

        signupCode.markUsed(OffsetDateTime.now());
        return SignupResponse.of(role, phone, student.getName(), null);
    }

    /** students 행과 이름은 선생님이 등록할 때 이미 있다. 여기서는 계정만 붙인다. */
    private void linkStudentAccount(Student student, String phone) {
        if (student.getUser() != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        requireUnusedPhone(phone);
        student.linkUser(createAccount(UserRole.STUDENT, phone, student.getName()));
    }

    private User createAccount(UserRole role, String phone, String name) {
        return userRepository.save(User.create(role, phone, name,
            passwordEncoder.encode(User.INITIAL_PASSWORD)));
    }

    private void requireUnusedPhone(String phone) {
        if (userRepository.existsByLoginId(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return raw.trim();
    }

    private String requirePhone(String raw) {
        String phone = PhoneNumbers.normalize(raw);
        if (phone.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return phone;
    }
}
