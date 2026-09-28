package com.njwenglish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일괄 저장 트리거를 학생당 하루 1건으로 묶는 표식. <b>묶기 전용이고 읽는 화면이 없다.</b>
 * 쓰기는 {@code PushDailySendRepository.claim} 의 {@code ON CONFLICT DO NOTHING} 하나뿐이다.
 */
@Entity
@Table(name = "push_daily_sends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDailySend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(name = "sent_on", nullable = false)
    private LocalDate sentOn;
}
