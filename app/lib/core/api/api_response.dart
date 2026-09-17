import 'package:dio/dio.dart';

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

/// dio는 2xx가 아니면 `DioException`을 던진다. Task 6의 401 리프레시와 Task 7의
/// 403 게이트가 그 예외(`onError`)에 기대고 있어서 `validateStatus`를 늘릴 수 없다.
/// 그래서 **저장소 경계에서** DioException을 ApiException으로 바꾼다 —
/// 화면은 ApiException 하나만 알면 된다.
///
/// 엔벌로프 본문이 있으면 `unwrap`에 넘긴다(그쪽이 `success: false`를
/// ApiException으로 바꾼다). 본문이 없는 전송 실패(연결 불가·타임아웃·HTML 오류
/// 페이지)는 **그대로 rethrow한다** — 서버가 준 문구가 없으니 앱이 문구를 만들면
/// 안 되고, 화면의 일반 catch가 받는다.
Future<T> unwrapCall<T>(
  Future<Response<Map<String, dynamic>>> Function() call,
  T Function(Object? data) parse,
) async {
  try {
    final res = await call();
    // ignore: unawaited_return_in_try_block — unwrap은 동기 함수라서 await 불필요
    return unwrap(res.data ?? const {}, res.statusCode, parse);
  } on DioException catch (e) {
    final body = e.response?.data;
    if (body is Map<String, dynamic>) {
      // ignore: unawaited_return_in_try_block — unwrap은 동기 함수라서 await 불필요
      return unwrap(body, e.response?.statusCode, parse);
    }
    rethrow;
  }
}
