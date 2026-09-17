import 'package:dio/dio.dart';

import '../api/api_response.dart';
import 'models/login_response.dart';
import 'models/me_response.dart';

/// `/api/auth` 다섯 개를 감싼다. **상태를 갖지 않는다** — 토큰 보관과 로그인
/// 상태는 `AuthController`가 맡는다(Task 9).
///
/// `POST /api/auth/refresh`는 여기 없다. 인터셉터가 직접 부른다(Task 6) —
/// 저장소를 거치면 순환 의존이 된다.
class AuthRepository {
  AuthRepository(this._dio);

  final Dio _dio;

  Future<LoginResponse> login({
    required String loginId,
    required String password,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'loginId': loginId, 'password': password},
    );
    return unwrap(
      res.data ?? const {},
      res.statusCode,
      (data) => LoginResponse.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<MeResponse> me() async {
    final res = await _dio.get<Map<String, dynamic>>('/api/auth/me');
    return unwrap(
      res.data ?? const {},
      res.statusCode,
      (data) => MeResponse.fromJson(data as Map<String, dynamic>),
    );
  }

  /// 세 가지 가입이 이 엔드포인트 하나를 쓴다. 서버가 `code`를 보고 종류를
  /// 판별하므로 **사용자는 학생·학부모를 고르지 않는다.**
  /// 비밀번호를 보내지 않는다 — `phone`이 로그인 아이디가 되고 초기값은 `0000`이다.
  Future<void> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/auth/signup',
      data: {
        'code': code,
        'name': name,
        'phone': phone,
        'parentPhone': parentPhone,
      },
    );
    unwrap<void>(res.data ?? const {}, res.statusCode, (_) {});
  }

  /// 성공하면 서버가 리프레시 토큰을 **전부 폐기하고** 쿠키도 만료시킨다.
  /// 부르는 쪽이 보관소를 비우고 다시 로그인시켜야 한다.
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    final res = await _dio.patch<Map<String, dynamic>>(
      '/api/auth/password',
      data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
    );
    unwrap<void>(res.data ?? const {}, res.statusCode, (_) {});
  }

  Future<void> logout() async {
    final res = await _dio.post<Map<String, dynamic>>('/api/auth/logout');
    unwrap<void>(res.data ?? const {}, res.statusCode, (_) {});
  }
}
