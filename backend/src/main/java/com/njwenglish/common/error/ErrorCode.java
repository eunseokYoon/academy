package com.njwenglish.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해 주세요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
    ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    STUDENT_NOT_ACCESSIBLE(HttpStatus.FORBIDDEN, "해당 학생 정보에 접근할 수 없습니다."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 먼저 변경해 주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 출석입니다."),
    DUE_DATE_PASSED(HttpStatus.CONFLICT, "마감 시간이 지났습니다."),
    PHOTO_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "사진은 최대 20장까지 첨부할 수 있습니다."),
    /** 게시판은 5장이다. 숙제(20장)와 상한이 달라서 메시지를 공유할 수 없다. */
    QNA_PHOTO_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "사진은 최대 5장까지 첨부할 수 있습니다."),
    ATTACHMENT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "첨부 파일은 최대 5개까지 올릴 수 있습니다."),
    CLINIC_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "정원이 모두 찼습니다."),
    SUBMISSION_EXISTS(HttpStatus.CONFLICT, "제출한 학생이 있어 삭제할 수 없습니다."),
    NO_RESUBMIT_TARGET(HttpStatus.CONFLICT, "다시 제출할 학생이 없습니다."),
    RESUBMIT_NOT_REQUIRED(HttpStatus.CONFLICT, "다시 제출할 숙제가 아닙니다."),
    STUDENT_HAS_RECORDS(HttpStatus.CONFLICT, "운영 기록이 있어 삭제할 수 없습니다. 퇴원 처리를 사용해 주세요."),
    // 공개 후 정답을 고치면 이미 응시한 학생의 점수가 소급 변경된다. 삭제 후 재출제가 유일한 경로다
    TEST_ALREADY_PUBLISHED(HttpStatus.CONFLICT,
        "공개된 테스트의 정답과 문항 수는 수정할 수 없습니다. 삭제 후 다시 출제해 주세요."),
    CLASS_ROOM_HAS_RECORDS(HttpStatus.CONFLICT, "수업·배정 기록이 있어 삭제할 수 없습니다. 종료 처리를 사용해 주세요."),
    LESSON_HAS_RECORDS(HttpStatus.CONFLICT, "출석·숙제 기록이 있어 삭제할 수 없습니다."),
    CLINIC_HAS_RECORDS(HttpStatus.CONFLICT,
        "배정·변경 기록이 있어 삭제할 수 없습니다. 닫기를 사용해 주세요."),
    INVITE_CODE_INVALID(HttpStatus.BAD_REQUEST, "초대코드가 유효하지 않습니다."),
    INVITE_CODE_USED(HttpStatus.BAD_REQUEST, "이미 사용된 초대코드입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 너무 큽니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
