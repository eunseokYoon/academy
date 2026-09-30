package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온라인 테스트 해설지 한 장(V27, 2026-09-29). 여러 장이다 — 한 칸이던 것을 옮겼다.
 *
 * <p><b>채점 후에만 내려준다.</b> 응시 화면 응답에 넣지 마라({@link OnlineTest} 주석).
 */
@Entity
@Table(name = "online_test_answer_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnlineTestAnswerFile extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_test_id", nullable = false)
    private OnlineTest onlineTest;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    /** 정렬용일 뿐이라 UNIQUE가 없다(V27 주석). */
    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    static OnlineTestAnswerFile of(OnlineTest onlineTest, String s3Key, short sortOrder) {
        OnlineTestAnswerFile file = new OnlineTestAnswerFile();
        file.onlineTest = onlineTest;
        file.s3Key = s3Key;
        file.sortOrder = sortOrder;
        return file;
    }
}
