/// 서버가 내려준 `{code, message}`를 그대로 담는다.
///
/// **message를 앱에서 다시 만들지 마라.** 백엔드 ErrorCode의 문구가 이미
/// 한국어 사용자 문구이고, 두 곳에서 문구를 만들면 같은 실패가 화면마다
/// 다르게 보인다.
class ApiException implements Exception {
  const ApiException({
    required this.code,
    required this.message,
    this.statusCode,
  });

  final String code;
  final String message;
  final int? statusCode;

  @override
  String toString() => 'ApiException($statusCode $code): $message';
}
