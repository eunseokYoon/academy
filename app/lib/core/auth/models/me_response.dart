import 'user_role.dart';

/// `phone`은 **본인 번호라 마스킹하지 않는다.** 자기 번호를 자기가 보는 것이다.
class MeResponse {
  const MeResponse({
    required this.id,
    required this.name,
    required this.role,
    required this.phone,
    required this.mustChangePassword,
  });

  final int id;
  final String name;
  final UserRole role;
  final String phone;
  final bool mustChangePassword;

  factory MeResponse.fromJson(Map<String, dynamic> json) => MeResponse(
    id: json['id'] as int,
    name: json['name'] as String,
    role: roleFromJson(json['role'] as String),
    phone: json['phone'] as String,
    mustChangePassword: json['mustChangePassword'] as bool,
  );
}
