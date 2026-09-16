/// 앱이 **특별히 취급하는** 코드만 둔다. 나머지는 message를 그대로 보여주면 되므로
/// 상수가 필요 없다 — 백엔드 ErrorCode 전체를 복사하지 마라. 복사하면 백엔드가
/// 코드를 늘릴 때마다 여기가 뒤처진다.
class ErrorCodes {
  const ErrorCodes._();

  /// 액세스 토큰 만료. 리프레시를 시도한다.
  static const tokenExpired = 'TOKEN_EXPIRED';

  /// 토큰이 유효하지 않다. 리프레시해도 소용없으므로 로그아웃한다.
  static const tokenInvalid = 'TOKEN_INVALID';

  /// 초기 비밀번호가 전원 0000이라, 바꾸기 전에는 3경로 외 전부 막힌다.
  static const passwordChangeRequired = 'PASSWORD_CHANGE_REQUIRED';

  /// 경로 접두사 위반. 앱에서 나면 코드 버그다.
  static const roleNotAllowed = 'ROLE_NOT_ALLOWED';

  static const invalidCredentials = 'INVALID_CREDENTIALS';

  /// 엔벌로프가 깨졌을 때 앱이 만들어 쓰는 값이다.
  static const internalError = 'INTERNAL_ERROR';
}
