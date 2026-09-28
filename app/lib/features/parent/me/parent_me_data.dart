import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';

import 'package:academy_app/shared/lib/phone.dart';

import '../selected_child.dart';

/// 백엔드 `ParentMeResponse`.
class ParentMe {
  const ParentMe({
    required this.name,
    required this.phone,
    required this.children,
  });

  final String name;

  /// 본인 번호라 마스킹하지 않는다. 숫자만 온다.
  final String phone;
  final List<Child> children;

  factory ParentMe.fromJson(Map<String, dynamic> json) => ParentMe(
    name: json['name'] as String,
    phone: json['phone'] as String,
    children: ((json['children'] as List<dynamic>?) ?? const [])
        .map((e) => Child.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class ParentMeRepository {
  const ParentMeRepository(this._dio);

  final Dio _dio;

  Future<ParentMe> me() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/parent/me'),
    (data) => ParentMe.fromJson(data! as Map<String, dynamic>),
  );

  /// 번호를 바꾸면 **로그인 아이디도 함께 바뀐다**(`changePhone()` 한 곳).
  /// 다음 로그인부터 새 번호다. 하이픈을 떼고 숫자만 보낸다.
  Future<ParentMe> changePhone(String phone) => unwrapCall(
    () => _dio.patch<Map<String, dynamic>>(
      '/api/parent/me',
      data: {'phone': digitsOnly(phone)},
    ),
    (data) => ParentMe.fromJson(data! as Map<String, dynamic>),
  );
}

/// P-5. 매개변수가 없는 화면이라 빈 레코드를 쓴다.
class ParentMeController extends ParamController<(), ParentMe> {
  ParentMeController({required this.repository, super.staleAfter, super.now});

  /// 연락처 변경이 직접 부른다 — 저장은 목록 상태가 아니다. 성공하면 [reload].
  final ParentMeRepository repository;

  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<ParentMe> fetch(() param) => repository.me();
}
