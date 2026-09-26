// `Cookie`는 cookie_jar가 re-export한다. `import 'dart:io';`를 넣으면
// unnecessary_import 린트로 analyze가 깨진다 — lib/core/storage/cookie_store.dart의 주석을 봐라.

import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';

void main() {
  // 쿠키의 path 가 /api/auth 라서, 이 URI 로만 실려 나간다.
  final authUri = Uri.parse('https://example.test/api/auth');

  CookieStore storeWith(KeyValueStore kv) => CookieStore(kv, authUri);

  test('jar 의 refreshToken 을 저장하고 되살린다', () async {
    final kv = InMemoryKeyValueStore();
    final saving = CookieJar();
    await saving.saveFromResponse(authUri, [
      Cookie('refreshToken', 'rt-1')..path = '/api/auth',
    ]);

    await storeWith(kv).persist(saving);

    final restored = CookieJar();
    await storeWith(kv).restore(restored);

    final cookies = await restored.loadForRequest(authUri);
    expect(cookies.map((c) => c.name), contains('refreshToken'));
    expect(cookies.firstWhere((c) => c.name == 'refreshToken').value, 'rt-1');
  });

  test('빈 값이면 저장이 아니라 삭제다', () async {
    // 로그아웃과 비밀번호 변경이 쿠키를 value="" · maxAge=0 으로 만료시킨다.
    // 그 빈 값을 그대로 저장하면 죽은 쿠키로 refresh 를 계속 시도한다.
    final kv = InMemoryKeyValueStore();
    await storeWith(kv).restore(CookieJar()); // no-op
    await kv.write('refresh_cookie', 'rt-old');

    final expired = CookieJar();
    await expired.saveFromResponse(authUri, [
      Cookie('refreshToken', '')..path = '/api/auth',
    ]);
    await storeWith(kv).persist(expired);

    expect(await kv.read('refresh_cookie'), isNull);
  });

  test('저장된 것이 없으면 restore 가 아무것도 심지 않는다', () async {
    final restored = CookieJar();
    await storeWith(InMemoryKeyValueStore()).restore(restored);
    expect(await restored.loadForRequest(authUri), isEmpty);
  });

  test('clear 가 저장된 쿠키를 지운다', () async {
    final kv = InMemoryKeyValueStore();
    await kv.write('refresh_cookie', 'rt-1');
    await storeWith(kv).clear();
    expect(await kv.read('refresh_cookie'), isNull);
  });
}
