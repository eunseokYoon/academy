import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/api_response.dart';

void main() {
  group('unwrap', () {
    test('성공 응답에서 data를 꺼낸다', () {
      final body = {
        'success': true,
        'data': {'accessToken': 'abc'},
        'error': null,
      };
      final token = unwrap<String>(
        body,
        200,
        (data) => (data as Map<String, dynamic>)['accessToken'] as String,
      );
      expect(token, 'abc');
    });

    test('data가 null인 성공 응답도 통과한다', () {
      // ApiResponse.ok() 가 내려오는 경로가 있다 — 로그아웃·비밀번호 변경
      final body = {'success': true, 'data': null, 'error': null};
      expect(unwrap<Null>(body, 200, (_) => null), isNull);

      // Task 8의 logout·changePassword가 쓰는 형태다. Dart는 void 식을 값으로
      // 쓰지 못하므로 위처럼 단정할 수 없고, 문장 위치에서 컴파일되는 것이
      // 확인해야 하는 전부다. 이 줄을 지우면 T = void 경로가 검증되지 않는다.
      unwrap<void>(body, 200, (_) {});
    });

    test('실패 응답은 code와 message를 담아 던진다', () {
      final body = {
        'success': false,
        'data': null,
        'error': {'code': 'INVALID_CREDENTIALS', 'message': '아이디 또는 비밀번호가 올바르지 않습니다.'},
      };
      expect(
        () => unwrap<String>(body, 401, (data) => data as String),
        throwsA(
          isA<ApiException>()
              .having((e) => e.code, 'code', 'INVALID_CREDENTIALS')
              .having((e) => e.message, 'message', '아이디 또는 비밀번호가 올바르지 않습니다.')
              .having((e) => e.statusCode, 'statusCode', 401),
        ),
      );
    });

    test('success가 false인데 error가 없으면 계약 위반으로 던진다', () {
      // 계약상 있을 수 없지만, 계약을 어긴 쪽이 앱을 무너뜨리게 두지 않는다.
      // 웹에서 2026-08-24에 같은 종류의 사고가 있었다 — 옛 응답의 필드가
      // undefined라 화면 전체가 죽었다.
      final body = {'success': false, 'data': null, 'error': null};
      expect(
        () => unwrap<String>(body, 500, (data) => data as String),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'INTERNAL_ERROR')),
      );
    });

    test('엔벌로프가 아닌 본문도 던진다', () {
      // nginx가 502를 HTML로 돌려주는 경우다
      expect(
        () => unwrap<String>(<String, dynamic>{}, 502, (data) => data as String),
        throwsA(isA<ApiException>()),
      );
    });
  });
}
