import 'package:cookie_jar/cookie_jar.dart';

import 'key_value_store.dart';

/// 리프레시 토큰은 응답 본문에 없다 — HttpOnly 쿠키 `refreshToken`으로만 온다.
/// HttpOnly는 브라우저 JS에만 의미가 있어서, 네이티브 클라이언트에게는 그냥
/// `Set-Cookie` 헤더다. 그래서 백엔드를 고치지 않고 그대로 쓴다.
///
/// **`PersistCookieJar`를 쓰지 마라.** 그것은 평문 파일에 쓴다. 리프레시 토큰은
/// 수명이 긴 자격증명이라 Keychain·Keystore에 들어가야 한다. 우리가 신경 쓰는
/// 쿠키는 하나뿐이므로 직접 넣고 뺀다.
class CookieStore {
  CookieStore(this._kv, this._authUri);

  static const _key = 'refresh_cookie';
  static const _name = 'refreshToken';

  /// 쿠키의 `path`가 `/api/auth`다. 이 URI로만 실려 나간다.
  final Uri _authUri;
  final KeyValueStore _kv;

  Future<void> persist(CookieJar jar) async {
    final cookies = await jar.loadForRequest(_authUri);
    final found = cookies.where((c) => c.name == _name);
    final value = found.isEmpty ? '' : found.first.value;

    // 로그아웃·비밀번호 변경은 쿠키를 value="" · maxAge=0 으로 만료시킨다.
    // 빈 값을 저장하면 죽은 쿠키로 refresh를 계속 시도한다.
    if (value.isEmpty) {
      await _kv.delete(_key);
      return;
    }
    await _kv.write(_key, value);
  }

  Future<void> restore(CookieJar jar) async {
    final value = await _kv.read(_key);
    if (value == null || value.isEmpty) return;
    await jar.saveFromResponse(_authUri, [
      Cookie(_name, value)
        ..path = _authUri.path
        ..httpOnly = true,
    ]);
  }

  Future<void> clear() => _kv.delete(_key);
}
