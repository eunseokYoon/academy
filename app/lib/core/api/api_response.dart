import 'api_exception.dart';
import 'error_codes.dart';

/// 백엔드 응답은 전부 `{success, data, error}`다(`ApiResponse<T>`).
/// 이 함수가 엔벌로프를 벗기는 **유일한 곳**이다 — 화면마다 벗기면
/// 어느 한 곳에서 error를 안 보고 넘어간다.
T unwrap<T>(
  Map<String, dynamic> body,
  int? statusCode,
  T Function(Object? data) parse,
) {
  final success = body['success'];
  if (success == true) {
    return parse(body['data']);
  }

  final error = body['error'];
  if (error is Map) {
    throw ApiException(
      code: (error['code'] as String?) ?? ErrorCodes.internalError,
      message: (error['message'] as String?) ?? '서버 오류가 발생했습니다.',
      statusCode: statusCode,
    );
  }

  // success가 false인데 error가 없거나, 애초에 엔벌로프가 아니다.
  // 계약 위반이지만 여기서 삼켜서 화면이 통째로 죽는 것만 막는다.
  throw ApiException(
    code: ErrorCodes.internalError,
    message: '서버 오류가 발생했습니다.',
    statusCode: statusCode,
  );
}
