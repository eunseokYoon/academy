import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// 대본대로 응답을 돌려주는 어댑터. `http_mock_adapter` 의존성을 더하지 않으려고
/// 직접 만든다 — 필요한 것이 「순서대로 이 응답들을 주고, 받은 요청을 기록해라」뿐이다.
///
/// **lib/ 안에 두는 것은 의도적이다.** Task 6·7·8의 테스트가 전부 이것을 쓴다.
class FakeReply {
  const FakeReply({required this.statusCode, required this.body, this.headers});

  final int statusCode;
  final Map<String, dynamic> body;
  final Map<String, List<String>>? headers;
}

class FakeAdapter implements HttpClientAdapter {
  FakeAdapter({required List<FakeReply> replies}) : _replies = [...replies];

  final List<FakeReply> _replies;

  /// 순서대로 받은 요청. 인터셉터가 무엇을 보냈는지 검증한다.
  final List<RequestOptions> received = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    received.add(options);
    if (_replies.isEmpty) {
      throw StateError(
        '대본에 없는 요청이다: ${options.method} ${options.path}. '
        'FakeReply를 ${received.length}개 이상 넣어라.',
      );
    }
    final reply = _replies.removeAt(0);
    return ResponseBody.fromString(
      jsonEncode(reply.body),
      reply.statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
        ...?reply.headers,
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
