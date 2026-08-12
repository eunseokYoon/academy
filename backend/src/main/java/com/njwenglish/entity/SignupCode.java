package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import com.njwenglish.entity.enums.UserRole;
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
 * 개인 코드. 발급 시 등록된 전화번호와 대조하므로 본인 확인이 된다.
 * 반 코드(class_rooms.join_code)와 달리 1회용이고 7일 만료다.
 */
@Entity
@Table(name = "signup_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignupCode extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** STUDENT 또는 PARENT만 유효하다. TEACHER는 CHECK 제약이 막는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false, length = 20)
    private UserRole targetRole;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    /** 유효기간 7일. 학생이 반 코드로 가입할 때 학부모용 1장이 자동 발급된다. */
    public static final int VALID_DAYS = 7;

    public static SignupCode issue(Student student, UserRole targetRole, String code,
                                   String normalizedPhone, OffsetDateTime now) {
        SignupCode signupCode = new SignupCode();
        signupCode.student = student;
        signupCode.targetRole = targetRole;
        signupCode.code = code;
        signupCode.phone = normalizedPhone;
        signupCode.expiresAt = now.plusDays(VALID_DAYS);
        return signupCode;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt.isBefore(now);
    }

    public void markUsed(OffsetDateTime now) {
        this.usedAt = now;
    }

    /** 선생님이 번호를 잘못 입력한 경우. 코드 문자열은 이미 전달됐을 수 있어 그대로 둔다. */
    public void changePhone(String normalizedPhone) {
        this.phone = normalizedPhone;
    }
}
