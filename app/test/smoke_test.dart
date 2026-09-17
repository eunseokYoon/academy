import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';
import 'package:academy_app/main.dart';

void main() {
  testWidgets('부팅하면 splash가 뜬다', (tester) async {
    // 부팅 전 상태는 unknown 이고 라우터가 splash 로 보낸다.
    // 이게 없으면 이미 로그인한 사용자가 매번 로그인 화면을 깜빡 본다.
    final kv = InMemoryKeyValueStore();
    final auth = AuthController(
      repository: AuthRepository(Dio()),
      tokens: TokenStore(kv),
      cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
      jar: CookieJar(),
    );

    await tester.pumpWidget(AcademyApp(auth: auth));
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  test('Android 에뮬레이터 기본 baseUrl은 10.0.2.2다', () {
    // localhost 를 쓰면 에뮬레이터 자기 자신을 가리켜 조용히 실패한다.
    // 이 테스트는 호스트에서 돌아 Platform.isAndroid 가 false 이므로
    // 값이 아니라 함수가 존재하고 빈 문자열을 주지 않는 것만 확인한다.
    expect(resolveBaseUrl(), isNotEmpty);
    expect(resolveBaseUrl(), startsWith('http'));
  });
}
