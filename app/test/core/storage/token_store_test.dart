import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

void main() {
  test('액세스 토큰을 쓰고 읽고 지운다', () async {
    final store = TokenStore(InMemoryKeyValueStore());

    expect(await store.read(), isNull);

    await store.write('abc');
    expect(await store.read(), 'abc');

    await store.clear();
    expect(await store.read(), isNull);
  });
}
