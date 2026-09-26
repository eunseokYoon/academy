import 'user_role.dart';

/// 가입 결과. 웹은 이 값으로 성공 화면에 이름·반·아이디를 보여준다.
///
/// **버리지 마라.** 반 코드에는 전화번호 대조가 없어서 오타나 남의 반 코드로
/// 가입해도 서버는 조용히 받아들인다. 반 이름을 화면에 보여주는 것이 학생이
/// 그 자리에서 알아차리는 유일한 방법이다.
///
/// **백엔드 `SignupResponse.java`(record)를 따른다.** 태스크 명세 초안에 있던
/// `studentId`는 실제 응답에 없다 — 대신 `role`·`initialPassword`가 있다.
/// `role`은 화면에서 쓰지 않지만(서버가 이미 코드로 종류를 판별했다) 백엔드
/// 필드와 일치시키려고 남긴다.
///
/// `classRoomName`은 개인 코드 가입에서 null일 수 있다 — 그 경로는
/// 반 배정이 이미 끝나 있거나 없다. 백엔드가 `@JsonInclude(NON_NULL)`이라
/// null일 때 키 자체가 응답에서 빠진다(`fromJson`은 둘 다 null로 받는다).
class SignupResponse {
  const SignupResponse({
    required this.role,
    required this.loginId,
    required this.studentName,
    required this.classRoomName,
    required this.initialPassword,
  });

  final UserRole role;

  /// 전화번호다(`users.login_id = phone`). 화면에서 고정폭으로 보여준다.
  final String loginId;

  final String studentName;
  final String? classRoomName;

  /// 항상 `0000`이다(백엔드 `User.INITIAL_PASSWORD`). 화면이 이 값을 그대로 보여준다 —
  /// 하드코딩하지 마라, 값이 바뀌면 화면 문구가 조용히 틀려진다.
  final String initialPassword;

  factory SignupResponse.fromJson(Map<String, dynamic> json) => SignupResponse(
    role: roleFromJson(json['role'] as String),
    loginId: json['loginId'] as String,
    studentName: json['studentName'] as String,
    classRoomName: json['classRoomName'] as String?,
    initialPassword: json['initialPassword'] as String,
  );
}
