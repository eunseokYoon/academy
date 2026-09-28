package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 아이디는 전화번호다. login_id는 항상 phone의 정규화된 값이며
 * 두 컬럼을 따로 고치지 않는다. 번호 변경은 changePhone() 한 곳에서만 처리한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /**
     * 초기 비밀번호(0000) 상태. true면 비밀번호 변경 외 모든 API가 403이다.
     * 전원이 아는 값이라 이 차단이 없으면 방치된 계정으로 남의 성적이 샌다.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * 푸시 알림 전체 끄기. 종류별 토글은 없다. 기본값이 true 다(V25) —
     * false 로 깔면 앱을 깐 전원이 알림을 못 받고 아무도 원인을 모른다.
     */
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    /**
     * 가입 시 서버가 넣는 초기 비밀번호. 전원이 아는 값이라 mustChangePassword와 짝으로만 쓴다.
     * 둘 중 하나만 있으면 반 친구가 남의 번호로 로그인해 성적을 본다.
     */
    public static final String INITIAL_PASSWORD = "0000";

    /** 가입으로 만들어지는 계정. 비밀번호는 언제나 초기값이라 변경 강제 상태로 시작한다. */
    public static User create(UserRole role, String normalizedPhone, String name, String passwordHash) {
        User user = new User();
        user.role = role;
        user.loginId = normalizedPhone;
        user.phone = normalizedPhone;
        user.name = name;
        user.passwordHash = passwordHash;
        user.status = UserStatus.ACTIVE;
        user.mustChangePassword = true;
        return user;
    }

    /**
     * login_id는 phone의 정규화 값이다. 둘을 따로 고치면 그 사용자는 로그인하지 못한다.
     * 번호 변경은 반드시 이 메서드 하나를 거친다.
     */
    public void changePhone(String normalizedPhone) {
        this.phone = normalizedPhone;
        this.loginId = normalizedPhone;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    /**
     * 선생님이 초기화한 비밀번호. 본인이 정한 값이 아니므로 변경 강제 상태로 되돌린다.
     * 호출부에서 리프레시 토큰도 전부 폐기해야 한다 — 분실이 곧 유출일 수 있다.
     */
    public void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
    }

    public void changePushEnabled(boolean enabled) {
        this.pushEnabled = enabled;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** 퇴원 처리. 로그인 자체가 막힌다. */
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }
}
