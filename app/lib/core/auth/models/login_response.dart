import 'user_role.dart';

/// **리프레시 토큰은 여기 없다.** HttpOnly 쿠키로만 온다(백엔드
/// `LoginResponse`의 주석이 그렇게 못 박고 있다). 모델에 자리를 만들지 마라 —
/// 만들면 언젠가 누가 본문에서 찾으려 한다.
class LoginResponse {
  const LoginResponse({required this.accessToken, required this.user});

  final String accessToken;
  final UserSummary user;

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
        accessToken: json['accessToken'] as String,
        user: UserSummary.fromJson(json['user'] as Map<String, dynamic>),
      );
}

class UserSummary {
  const UserSummary({
    required this.id,
    required this.name,
    required this.role,
    required this.mustChangePassword,
  });

  final int id;
  final String name;
  final UserRole role;

  /// 초기 비밀번호 `0000`을 아직 안 바꿨다. 참이면 3경로 외 전부 403이다.
  final bool mustChangePassword;

  factory UserSummary.fromJson(Map<String, dynamic> json) => UserSummary(
        id: json['id'] as int,
        name: json['name'] as String,
        role: roleFromJson(json['role'] as String),
        mustChangePassword: json['mustChangePassword'] as bool,
      );
}
