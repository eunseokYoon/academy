import 'key_value_store.dart';

/// 액세스 토큰만 다룬다. 리프레시 토큰은 쿠키라서 `CookieStore`가 맡는다.
class TokenStore {
  TokenStore(this._kv);

  static const _key = 'access_token';

  final KeyValueStore _kv;

  Future<String?> read() async {
    final value = await _kv.read(_key);
    return (value == null || value.isEmpty) ? null : value;
  }

  Future<void> write(String token) => _kv.write(_key, token);

  Future<void> clear() => _kv.delete(_key);
}
