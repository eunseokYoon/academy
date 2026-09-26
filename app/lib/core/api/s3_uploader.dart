import 'package:dio/dio.dart';

/// 서명된 주소로 S3 에 **직접** 올린다(CLAUDE.md 6번 — 서버 경유 금지).
///
/// **인터셉터가 붙은 Dio 를 쓰지 마라.** `Authorization` 헤더가 실리면 서명이
/// 어긋나 403 이 난다(웹 `upload.ts` 가 axios 대신 `fetch` 를 쓰는 이유와 같다).
/// 그래서 이 클래스는 자기 Dio 를 따로 들고, 앱의 Dio 는 받지 않는다.
///
/// `Content-Type` 은 주소를 발급받을 때 보낸 값과 **반드시 같아야** 한다.
/// 서명에 들어가 있다.
class S3Uploader {
  S3Uploader([Dio? dio])
    : _dio =
          dio ??
          Dio(
            BaseOptions(
              connectTimeout: const Duration(seconds: 15),
              // 영상 100MB 를 느린 망에서 올린다. 보내는 시간은 막지 않고,
              // 다 보낸 뒤 S3 의 응답만 기다린다.
              receiveTimeout: const Duration(seconds: 60),
            ),
          );

  final Dio _dio;

  /// [body] 는 바이트 목록 또는 바이트 스트림이다. 스트림이면 [length] 가
  /// 있어야 한다 — S3 PUT 은 청크 전송을 안 받는다.
  ///
  /// 실패하면 [S3UploadException] 을 던진다. S3 의 오류 본문은 XML 이고
  /// 사용자 문구가 아니라서 화면이 자기 문구를 쓴다.
  Future<void> put(
    String url, {
    required Object body,
    required String contentType,
    required int length,
    ProgressCallback? onSendProgress,
  }) async {
    try {
      await _dio.put<void>(
        url,
        data: body,
        options: Options(
          headers: {
            Headers.contentTypeHeader: contentType,
            Headers.contentLengthHeader: length,
          },
          // XML 을 JSON 으로 읽으려다 터지지 않게 본문을 버린다.
          responseType: ResponseType.plain,
        ),
        onSendProgress: onSendProgress,
      );
    } on DioException catch (e) {
      throw S3UploadException(e.response?.statusCode);
    }
  }
}

class S3UploadException implements Exception {
  const S3UploadException(this.statusCode);

  final int? statusCode;

  @override
  String toString() => 'S3UploadException($statusCode)';
}
