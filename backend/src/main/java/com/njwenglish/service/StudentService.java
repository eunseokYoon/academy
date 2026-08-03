package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.common.util.InviteCodes;
import com.njwenglish.common.util.PhoneNumbers;
import com.njwenglish.dto.member.PasswordResetRequest;
import com.njwenglish.dto.member.PasswordResetResponse;
import com.njwenglish.dto.member.SignupCodeIssueRequest;
import com.njwenglish.dto.member.SignupCodeIssueResponse;
import com.njwenglish.dto.member.SignupCodeResponse;
import com.njwenglish.dto.member.StudentCreateRequest;
import com.njwenglish.dto.member.StudentCreateResponse;
import com.njwenglish.dto.member.StudentDeleteResponse;
import com.njwenglish.dto.member.StudentDetailResponse;
import com.njwenglish.dto.member.StudentListItemResponse;
import com.njwenglish.dto.member.StudentMeResponse;
import com.njwenglish.dto.member.StudentRestoreResponse;
import com.njwenglish.dto.member.StudentUpdateRequest;
import com.njwenglish.dto.member.StudentWithdrawRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.SignupCodeRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-2 학생 관리. 여기서 users 행을 만들지 않는다 — 계정은 당사자가 회원가입할 때 생긴다.
 * 그래서 students.user_id·parent_id가 nullable이고 getUser()는 null일 수 있다.
 *
 * <p>퇴원(withdraw)과 삭제(delete)는 쓰임이 다르다. 실제로 다닌 학생은 언제나 퇴원이고,
 * 삭제는 반 코드로 들어온 제3자 전용이다. 삭제는 되돌릴 수 없다.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    private static final String SORT_RECENT = "recent";

    private final StudentAccessGuard studentAccessGuard;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final SignupCodeRepository signupCodeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final InviteCodeIssuer inviteCodeIssuer;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final ParentLinkService parentLinkService;
    private final ParentRepository parentRepository;

    /**
     * S-7 상단 카드. 이름은 students.name이고 전화번호는 마스킹해 내려간다.
     *
     * <p>학생 본인 호출이라 getUser()가 null일 수 없지만, 반 목록은 재원 중인 것만이다 —
     * 퇴원한 반이 "내 반"으로 남으면 학생이 혼란스럽다.
     */
    @Transactional(readOnly = true)
    public StudentMeResponse me() {
        Student student = studentAccessGuard.requireSelf();
        List<StudentMeResponse.ClassRoomRef> classRooms =
            enrollmentRepository.findByStudentIdAndLeftAtIsNull(student.getId()).stream()
                .map(Enrollment::getClassRoom)
                .sorted(Comparator.comparing(ClassRoom::getName))
                .map(room -> new StudentMeResponse.ClassRoomRef(room.getId(), room.getName()))
                .toList();

        return new StudentMeResponse(
            student.getId(),
            student.getName(),
            classRooms,
            student.getUser() == null ? null : PhoneNumbers.mask(student.getUser().getPhone()),
            student.getParent() != null);
    }

    /**
     * sort=recent가 반 코드 제3자 탐지 경로다. 등록 기간에는 매일 상단 20명만 훑는다.
     * 200명을 이름순으로 놓고 낯선 이름 하나를 찾는 것은 실제로 불가능하다.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentListItemResponse> list(Long classRoomId, StudentStatus status,
                                                      String keyword, String sort,
                                                      int page, int size) {
        Page<Student> students = studentRepository.search(
            classRoomId,
            status == null ? null : status.name(),
            namePattern(keyword),
            phonePattern(keyword),
            PageRequest.of(page, size, sortOf(sort)));

        List<Long> studentIds = students.getContent().stream().map(Student::getId).toList();
        Map<Long, List<String>> classRoomNames = classRoomNamesOf(studentIds);
        Map<Long, Map<UserRole, String>> pendingPhones = pendingCodePhonesOf(studentIds);

        return PageResponse.from(students.map(student -> new StudentListItemResponse(
            student.getId(),
            student.getName(),
            classRoomNames.getOrDefault(student.getId(), List.of()),
            studentPhoneOf(student, pendingPhones),
            student.getUser() != null,
            parentPhoneOf(student, pendingPhones),
            student.getParent() != null,
            student.getStatus(),
            student.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public StudentDetailResponse detail(Long studentId) {
        return toDetail(studentAccessGuard.requireAccessible(studentId));
    }

    /**
     * 한 트랜잭션: students → enrollments → signup_codes 2행. users는 만들지 않는다.
     *
     * <p>두 번호가 같으면 login_id UNIQUE 때문에 둘 중 하나는 끝내 가입하지 못한다.
     * 등록 단계에서 막아야 원인을 찾을 수 있다.
     */
    @Transactional
    public StudentCreateResponse create(StudentCreateRequest request) {
        String studentPhone = requirePhone(request.studentPhone());
        String parentPhone = requirePhone(request.parentPhone());
        if (studentPhone.equals(parentPhone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        requireAvailablePhone(studentPhone);
        requireAvailablePhone(parentPhone);

        Student student = studentRepository.save(Student.create(requireName(request.name())));
        student.changeMemo(request.memo());
        enrollAll(student, request.classRoomIds());

        SignupCode studentCode = issueCode(student, UserRole.STUDENT, studentPhone);
        // 학부모는 코드 없이 계정이 바로 생긴다. 반 코드 가입 경로와 같은 방식이다.
        // 선생님이 넣은 번호라 학생이 적는 경우보다 신뢰도가 높다.
        parentLinkService.linkParent(student, parentPhone, student.getName() + " 학부모");

        return new StudentCreateResponse(student.getId(), SignupCodeResponse.from(studentCode));
    }

    @Transactional
    public StudentDetailResponse update(Long studentId, StudentUpdateRequest request) {
        Student student = studentAccessGuard.requireAccessible(studentId);

        if (request.name() != null) {
            String name = requireName(request.name());
            student.rename(name);
            // 계정이 있으면 표시 이름도 맞춰 둔다. 이름의 원본은 언제나 students.name이다
            if (student.getUser() != null) {
                student.getUser().rename(name);
            }
        }
        if (request.memo() != null) {
            student.changeMemo(request.memo().isBlank() ? null : request.memo());
        }
        if (request.studentPhone() != null) {
            changeStudentPhone(student, requirePhone(request.studentPhone()));
        }
        if (request.parentPhone() != null) {
            changeParentPhone(student, requirePhone(request.parentPhone()));
        }
        return toDetail(student);
    }

    /**
     * 데이터는 지우지 않는다. 과거 출석·성적·숙제는 그대로 두고 상태만 바꾼다.
     * 미가입 학생(user_id IS NULL)도 오류 없이 처리돼야 한다.
     */
    @Transactional
    public StudentDetailResponse withdraw(Long studentId, StudentWithdrawRequest request) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        LocalDate withdrawnAt = request == null || request.withdrawnAt() == null
            ? LocalDate.now() : request.withdrawnAt();

        student.withdraw(withdrawnAt);
        blockLogin(student.getUser());
        enrollmentRepository.findByStudentIdAndLeftAtIsNull(studentId)
            .forEach(enrollment -> enrollment.leave(withdrawnAt));

        // 퇴원생 코드로 가입되면 안 된다
        signupCodeRepository.deleteAll(signupCodeRepository.findByStudentIdAndUsedAtIsNull(studentId));

        Parent parent = student.getParent();
        if (parent != null
            && studentRepository.countByParentIdAndStatus(parent.getId(), StudentStatus.ENROLLED) == 0) {
            blockLogin(parent.getUser());
        }
        return toDetail(student);
    }

    /** 퇴원의 역연산이지만 반 배정과 코드는 되살리지 않는다. 선생님이 T-3에서 다시 배정한다. */
    @Transactional
    public StudentRestoreResponse restore(Long studentId) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        if (student.isEnrolled()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        student.restore();
        if (student.getUser() != null) {
            student.getUser().activate();
        }
        if (student.getParent() != null) {
            student.getParent().getUser().activate();
        }
        return new StudentRestoreResponse(studentId, student.getStatus(), 0);
    }

    /**
     * 반 코드로 들어온 제3자를 지운다. 운영 기록이 하나라도 있으면 409이고, 그때는 퇴원이 맞다.
     *
     * <p>submissions는 출제 시 대상 전원의 행이 NOT_SUBMITTED로 미리 깔린다. "행이 있으면 409"로
     * 짜면 아무도 못 지운다. 판정은 hasOperationalRecords가 status로 걸러서 한다.
     *
     * <p><b>학부모가 붙어 있는지는 판정에 쓰지 않는다.</b> 예전에는 학부모가 코드로 직접
     * 가입해야 연결돼서 "진짜 학생"의 신호였지만, 지금은 가입·등록 시점에 전원 자동으로 붙는다.
     * 이걸 조건에 넣으면 모든 학생이 409가 되어 제3자를 아무도 못 지운다.
     */
    @Transactional
    public StudentDeleteResponse delete(Long studentId) {
        Student student = studentAccessGuard.requireAccessible(studentId);

        if (studentRepository.hasOperationalRecords(studentId)) {
            throw new BusinessException(ErrorCode.STUDENT_HAS_RECORDS);
        }

        User user = student.getUser();
        Parent parent = student.getParent();
        long enrollments = enrollmentRepository.countByStudentId(studentId);

        // FK 순서: 자식 → students → users
        studentRepository.deleteSubmissionPhotosOf(studentId);
        studentRepository.deleteSubmissionsOf(studentId);
        enrollmentRepository.deleteByStudentId(studentId);
        signupCodeRepository.deleteByStudentId(studentId);
        studentRepository.delete(student);
        studentRepository.flush();

        if (user != null) {
            // revoke가 아니라 delete다. 행이 남으면 FK 때문에 users를 못 지운다
            tokenService.deleteAllOf(user.getId());
            userRepository.delete(user);
        }
        deleteParentIfChildless(parent);

        return new StudentDeleteResponse(studentId, user != null, enrollments);
    }

    /**
     * 이 학생이 유일한 자녀였으면 학부모 계정도 함께 지운다.
     *
     * <p>학부모 계정이 학생 가입에 딸려 자동으로 생기므로, 잘못 들어온 학생을 지울 때
     * 계정을 남기면 아무 자녀도 없는 유령 계정이 쌓인다. 형제·자매가 남아 있으면 지우지 않는다.
     */
    private void deleteParentIfChildless(Parent parent) {
        if (parent == null || studentRepository.countByParentId(parent.getId()) > 0) {
            return;
        }
        User parentUser = parent.getUser();
        parentRepository.delete(parent);
        if (parentUser != null) {
            tokenService.deleteAllOf(parentUser.getId());
            userRepository.delete(parentUser);
        }
    }

    /**
     * 코드 재발급. 미사용 코드가 남아 있으면 폐기하고 새로 발급한다 —
     * 유효한 코드가 여러 장 떠다니면 어느 것이 살아 있는지 알 수 없다.
     */
    @Transactional
    public SignupCodeIssueResponse issueSignupCode(Long studentId,
                                                   SignupCodeIssueRequest request) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        UserRole target = requireCodeTarget(request.target());

        // 이미 가입했다면 필요한 것은 코드가 아니라 reset-password다
        if (alreadySignedUp(student, target)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<SignupCode> pending =
            signupCodeRepository.findByStudentIdAndTargetRoleAndUsedAtIsNull(studentId, target);
        String phone = request.phone() != null && !request.phone().isBlank()
            ? requirePhone(request.phone())
            : pending.stream().map(SignupCode::getPhone).findFirst().orElse(null);
        if (phone == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        // 자기 코드부터 비워야 아래 선점 검사에 자기 자신이 걸리지 않는다
        signupCodeRepository.deleteAll(pending);
        signupCodeRepository.flush();
        requireAvailablePhone(phone);

        return SignupCodeIssueResponse.from(issueCode(student, target, phone));
    }

    /**
     * 비밀번호 분실 시 유일한 복구 경로다. 이메일·SMS가 범위 밖이라 자동 재설정을 만들 수 없다.
     * 분실이 곧 유출일 수 있으므로 리프레시 토큰을 전부 폐기한다.
     */
    @Transactional
    public PasswordResetResponse resetPassword(Long studentId, PasswordResetRequest request) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        UserRole target = request.target() == null ? UserRole.STUDENT : requireTarget(request.target());

        // 계정이 없으면 초기화할 것도 없다. 그 경우 필요한 것은 signup-code 재발급이다
        User user = target == UserRole.PARENT
            ? (student.getParent() == null ? null : student.getParent().getUser())
            : student.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        String password = request.newPassword() == null || request.newPassword().isBlank()
            ? InviteCodes.generate(InviteCodes.TEMPORARY_PASSWORD_LENGTH)
            : request.newPassword();

        user.resetPassword(passwordEncoder.encode(password));
        tokenService.revokeAllOf(user.getId());

        return new PasswordResetResponse(target, user.getLoginId(), password);
    }

    // ---------- 내부 ----------

    private void enrollAll(Student student, List<Long> classRoomIds) {
        if (classRoomIds == null || classRoomIds.isEmpty()) {
            return;
        }
        List<ClassRoom> classRooms = classRoomRepository.findByIdIn(classRoomIds);
        if (classRooms.size() != classRoomIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        LocalDate today = LocalDate.now();
        classRooms.forEach(classRoom ->
            enrollmentRepository.save(Enrollment.create(student, classRoom, today)));
    }

    private SignupCode issueCode(Student student, UserRole target, String phone) {
        return signupCodeRepository.save(SignupCode.issue(
            student, target, inviteCodeIssuer.issue(), phone, OffsetDateTime.now()));
    }

    private boolean alreadySignedUp(Student student, UserRole target) {
        return target == UserRole.PARENT ? student.getParent() != null : student.getUser() != null;
    }

    private void changeStudentPhone(Student student, String phone) {
        User user = student.getUser();
        if (user != null) {
            if (phone.equals(user.getLoginId())) {
                return;
            }
            requireAvailablePhone(phone);
            user.changePhone(phone);
            return;
        }
        changePendingCodePhone(student, UserRole.STUDENT, phone);
    }

    /**
     * 이미 가입한 학부모의 번호는 여기서 바꾸지 않는다. 형제·자매가 함께 쓰는 계정이라
     * 다른 자녀 쪽에서 보면 이유 없이 로그인 아이디가 바뀐 것이 된다. 본인이 P-5에서 바꾼다.
     */
    private void changeParentPhone(Student student, String phone) {
        if (student.getParent() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        changePendingCodePhone(student, UserRole.PARENT, phone);
    }

    private void changePendingCodePhone(Student student, UserRole target, String phone) {
        List<SignupCode> pending = signupCodeRepository
            .findByStudentIdAndTargetRoleAndUsedAtIsNull(student.getId(), target);
        if (pending.isEmpty()) {
            // 저장할 곳이 없다. 코드를 먼저 발급해야 한다
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (pending.stream().anyMatch(code -> phone.equals(code.getPhone()))) {
            return;
        }
        requireAvailablePhone(phone);
        pending.forEach(code -> code.changePhone(phone));
    }

    /** users.login_id뿐 아니라 아직 안 쓴 코드의 대조 번호와도 겹치면 안 된다. */
    private void requireAvailablePhone(String phone) {
        if (userRepository.existsByLoginId(phone)
            || signupCodeRepository.existsByPhoneAndUsedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private void blockLogin(User user) {
        if (user == null) {
            return;
        }
        user.deactivate();
        tokenService.revokeAllOf(user.getId());
    }

    private StudentDetailResponse toDetail(Student student) {
        Map<UserRole, String> pending = signupCodeRepository
            .findByStudentIdAndUsedAtIsNull(student.getId()).stream()
            .collect(Collectors.toMap(SignupCode::getTargetRole, SignupCode::getPhone,
                (older, newer) -> newer));

        List<StudentDetailResponse.EnrolledClassRoom> classRooms =
            enrollmentRepository.findByStudentIdAndLeftAtIsNull(student.getId()).stream()
                .map(e -> new StudentDetailResponse.EnrolledClassRoom(
                    e.getClassRoom().getId(), e.getClassRoom().getName(), e.getJoinedAt()))
                .sorted(Comparator.comparing(StudentDetailResponse.EnrolledClassRoom::name))
                .toList();

        List<SignupCodeIssueResponse> codes =
            signupCodeRepository.findByStudentIdAndUsedAtIsNull(student.getId()).stream()
                .map(SignupCodeIssueResponse::from)
                .toList();

        Parent parent = student.getParent();
        return new StudentDetailResponse(
            student.getId(),
            student.getName(),
            student.getMemo(),
            student.getStatus(),
            student.getWithdrawnAt(),
            student.getCreatedAt(),
            student.getUser() != null ? student.getUser().getLoginId()
                : pending.get(UserRole.STUDENT),
            student.getUser() != null,
            parent != null ? parent.getUser().getLoginId() : pending.get(UserRole.PARENT),
            parent != null,
            parent != null ? parent.getUser().getName() : null,
            classRooms,
            codes);
    }

    private Map<Long, List<String>> classRoomNamesOf(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return enrollmentRepository.findActiveByStudentIds(studentIds).stream()
            .collect(Collectors.groupingBy(e -> e.getStudent().getId(),
                Collectors.mapping(e -> e.getClassRoom().getName(), Collectors.toList())));
    }

    private Map<Long, Map<UserRole, String>> pendingCodePhonesOf(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return signupCodeRepository.findByStudentIdInAndUsedAtIsNull(studentIds).stream()
            .collect(Collectors.groupingBy(code -> code.getStudent().getId(),
                Collectors.toMap(SignupCode::getTargetRole, SignupCode::getPhone,
                    (older, newer) -> newer)));
    }

    private String studentPhoneOf(Student student, Map<Long, Map<UserRole, String>> pending) {
        return student.getUser() != null
            ? student.getUser().getLoginId()
            : pending.getOrDefault(student.getId(), Map.of()).get(UserRole.STUDENT);
    }

    private String parentPhoneOf(Student student, Map<Long, Map<UserRole, String>> pending) {
        return student.getParent() != null
            ? student.getParent().getUser().getLoginId()
            : pending.getOrDefault(student.getId(), Map.of()).get(UserRole.PARENT);
    }

    private Sort sortOf(String sort) {
        return SORT_RECENT.equalsIgnoreCase(sort)
            ? Sort.by(Sort.Direction.DESC, "createdAt")
            : Sort.by(Sort.Direction.ASC, "name");
    }

    private String namePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /** 번호는 하이픈이 섞여 들어온다. 숫자가 하나도 없으면 이름 검색만 한다. */
    private String phonePattern(String keyword) {
        String digits = PhoneNumbers.normalize(keyword);
        return digits.isBlank() ? null : "%" + digits + "%";
    }

    /**
     * 비밀번호 초기화 대상. 학생·학부모 둘 다 계정이 있으므로 둘 다 받는다.
     *
     * <p><b>코드 발급과 공유하지 마라.</b> 코드는 학생만 나가지만 초기화는 학부모도 필요하다.
     * 하나로 합쳐서 STUDENT만 허용하면 학부모가 비밀번호를 잊었을 때 손쓸 방법이 없어진다 —
     * 이 서비스에는 비밀번호 찾기가 없고 선생님 초기화가 유일한 경로다.
     */
    private UserRole requireTarget(UserRole target) {
        if (target != UserRole.STUDENT && target != UserRole.PARENT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return target;
    }

    /**
     * 코드는 학생용만 발급한다. 학부모 계정은 등록 시점에 바로 만들어져서 전달할 코드가 없다.
     * PARENT를 보내면 400이다 — V7의 ck_signup_codes_role도 같은 것을 DB에서 막는다.
     */
    private UserRole requireCodeTarget(UserRole target) {
        if (target != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return target;
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
