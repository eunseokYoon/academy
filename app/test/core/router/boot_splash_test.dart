import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

/// 서버에 닿지 못한다. 부팅의 me() 만 부른다.
class _OfflineRepo implements AuthRepository {
  int meCalls = 0;

  @override
  Future<MeResponse> me() async {
    meCalls++;
    throw DioException(
      requestOptions: RequestOptions(path: '/api/auth/me'),
      type: DioExceptionType.connectionError,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  // 2026-09-30 리뷰: 망이 없을 때 앱을 열면 로그인 정보를 지우고 로그인 화면으로 보냈다.
  testWidgets('부팅 복원이 연결 문제로 실패하면 스플래시에 다시 시도를 띄운다', (tester) async {
    final tokens = TokenStore(InMemoryKeyValueStore());
    await tokens.write('at-1');
    final repo = _OfflineRepo();
    final auth = AuthController(
      repository: repo,
      tokens: tokens,
      cookies: CookieStore(
        InMemoryKeyValueStore(),
        Uri.parse('https://example.test/api/auth'),
      ),
      jar: CookieJar(),
    );
    addTearDown(auth.dispose);
    final router = buildRouter(auth: auth, routes: const []);
    addTearDown(router.dispose);

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await auth.bootstrap();
    await tester.pump();

    expect(find.text('다시 시도'), findsOneWidget);
    expect(await tokens.read(), 'at-1');

    await tester.tap(find.text('다시 시도'));
    await tester.pump();
    expect(repo.meCalls, 2);
  });
}
