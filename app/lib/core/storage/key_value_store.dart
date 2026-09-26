import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 보관소를 추상화하는 이유는 테스트다. `flutter_secure_storage`는 플랫폼 채널을
/// 쓰므로 단위 테스트에서 동작하지 않는다.
abstract class KeyValueStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// iOS Keychain · Android Keystore. 리프레시 쿠키와 액세스 토큰이 여기 들어간다.
class SecureKeyValueStore implements KeyValueStore {
  SecureKeyValueStore([FlutterSecureStorage? storage])
    : _storage =
          storage ??
          const FlutterSecureStorage(
            // aOptions를 넘기지 않는다. 11.x의 기본값이 이미 AES-GCM +
            // RSA OAEP 키 래핑이라 암호화가 옵션이 아니라 기본이다 —
            // 옛 `encryptedSharedPreferences: true` 플래그는 API에서 사라졌다.
            // 되살리려 하지 마라. 컴파일되지 않는다.
            iOptions: IOSOptions(
              accessibility: KeychainAccessibility.first_unlock,
            ),
          );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

/// 테스트용. **lib/ 안에 두는 것은 의도적이다** — 여러 태스크의 테스트가 쓴다.
class InMemoryKeyValueStore implements KeyValueStore {
  final Map<String, String> _values = {};

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async => _values[key] = value;

  @override
  Future<void> delete(String key) async => _values.remove(key);
}
