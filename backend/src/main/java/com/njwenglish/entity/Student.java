package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.StudentStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이름은 users.name이 아니라 여기 있다. 미가입 학생은 users 행이 없어서
 * users에서 읽으면 출석부·숙제 명단에서 통째로 사라진다.
 *
 * <p>user와 parent는 둘 다 null일 수 있다. 계정은 당사자가 가입할 때 생긴다.
 * getUser()의 null 검사를 빠뜨리지 마라.
 */
@Entity
@Table(name = "students")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 회원가입 전에는 null이다. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /** 학부모가 가입해 연결되기 전에는 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status;

    @Column(name = "withdrawn_at")
    private LocalDate withdrawnAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    /** 계정보다 학생 행이 먼저 생긴다. 이름은 언제나 여기 있다. */
    public static Student create(String name) {
        Student student = new Student();
        student.name = name;
        student.status = StudentStatus.ENROLLED;
        return student;
    }

    public void linkUser(User user) {
        this.user = user;
    }

    public void linkParent(Parent parent) {
        this.parent = parent;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }

    public boolean isEnrolled() {
        return status == StudentStatus.ENROLLED;
    }

    /** 데이터는 지우지 않는다. 과거 출석·성적은 그대로 두고 상태만 바꾼다. */
    public void withdraw(LocalDate withdrawnAt) {
        this.status = StudentStatus.WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
    }

    /** 퇴원의 역연산이지만 반 배정은 되살리지 않는다. 그 반이 이미 끝났을 수 있다. */
    public void restore() {
        this.status = StudentStatus.ENROLLED;
        this.withdrawnAt = null;
    }
}
