import 'package:dio/dio.dart';

import 'error_codes.dart';

/// 초기 비밀번호가 전원 `0000`이라, 백엔드의 `PasswordChangeRequiredFilter`가
/// 바꾸기 전에는 세 경로(`PATCH /auth/password`, `GET /auth/me`,
/// `POST /auth/logout`) 외 전부를 403 `PASSWORD_CHANGE_REQUIRED`로 막는다.
/// 이게 없으면 전원이 아는 값으로 남의 성적이 샌다.
///
/// 이 인터셉터는 **알리기만 한다.** 화면 이동은 라우터가 한다(Task 10) —
/// 인터셉터가 내비게이션을 직접 하면 테스트에 위젯 트리가 필요해진다.
///
/// **에러를 삼키지 마라.** `handler.resolve`를 부르면 호출한 쪽이 실패를
/// 성공으로 읽는다. 언제나 `next`로 흘려보낸다.
class PasswordGateInterceptor extends Interceptor {
  PasswordGateInterceptor(this._onRequired);

  final void Function() _onRequired;

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 403 &&
        _isPasswordGate(err.response?.data)) {
      _onRequired();
    }
    handler.next(err);
  }

  bool _isPasswordGate(Object? body) {
    if (body is! Map) return false;
    final error = body['error'];
    if (error is! Map) return false;
    return error['code'] == ErrorCodes.passwordChangeRequired;
  }
}
