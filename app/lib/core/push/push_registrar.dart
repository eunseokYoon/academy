import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/api_response.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_status.dart';
import '../auth/models/user_role.dart';
import 'push_messaging.dart';

/// 이 기기의 FCM 토큰을 **지금 로그인한 사람**에게 묶는다.
///
/// - `ready` 가 되면(학생·학부모만) 권한을 묻고 `PUT /api/push/devices`.
///   서버가 같은 토큰의 주인을 옮기므로(UPSERT) 형제가 폰을 바꿔 써도 앞 사람의
///   알림이 오지 않는다.
/// - 토큰이 바뀌면 다시 올린다.
/// - **로그아웃 직전에** [unregister] 가 `DELETE` 를 부른다 — 뒤에는 인증이 없다.
/// - `loggedOut` 이 되면 기기 토큰 자체를 지운다. 세션 만료처럼 서버에서 못 지운
///   경우에도 FCM 이 다음 발송에 UNREGISTERED 를 돌려 서버가 정리한다. 이게 없으면
///   로그아웃한 폰이 앞 사람의 알림을 계속 받는다.
///
/// 선생님은 등록하지 않는다(15-4, 서버도 403). 실패는 로그로 끝난다 — 알림은
/// 곁다리라 로그인·로그아웃을 막지 않는다.
///
/// 작업은 한 줄로 이어 붙인다. 로그아웃의 토큰 삭제와 곧바로 이어진 다음 로그인의
/// 토큰 발급이 엇갈리면 새 토큰을 지워 버린다.
class PushRegistrar {
  PushRegistrar({
    required this._dio,
    required this._messaging,
    required this._auth,
  });

  static const _unregisterTimeout = Duration(seconds: 5);

  final Dio _dio;
  final PushMessaging _messaging;
  final AuthController _auth;

  /// 서버에 지금 사람 몫으로 올린 토큰. 없으면 지울 것도 없다.
  String? _registered;
  AuthStatus? _lastStatus;
  StreamSubscription<String>? _refreshSub;
  Future<void> _queue = Future<void>.value();

  /// 한 번 부른다. 부르는 순간의 상태부터 따라간다.
  void start() {
    _auth.addListener(_onAuth);
    _refreshSub = _messaging.onTokenRefresh.listen(
      (token) => _enqueue(() => _put(token)),
    );
    _onAuth();
  }

  void dispose() {
    _auth.removeListener(_onAuth);
    _refreshSub?.cancel();
  }

  /// 테스트가 이어 붙인 작업이 끝나기를 기다린다.
  @visibleForTesting
  Future<void> get idle => _queue;

  bool get _eligible {
    final s = _auth.snapshot;
    return s.status == AuthStatus.ready &&
        (s.role == UserRole.student || s.role == UserRole.parent);
  }

  void _onAuth() {
    final status = _auth.snapshot.status;
    if (status == _lastStatus) return;
    _lastStatus = status;
    if (_eligible) {
      _enqueue(_register);
    } else if (status == AuthStatus.loggedOut) {
      _enqueue(_forget);
    }
  }

  /// 로그아웃 직전. `AuthController.logout` 이 서버 로그아웃보다 먼저 부른다.
  /// 망이 끊겼으면 짧게 기다리고 포기한다 — 로그아웃이 멈추면 안 된다.
  ///
  /// 토큰은 줄의 차례가 왔을 때 읽는다. 로그인 직후의 등록이 아직 줄에 있으면
  /// 그게 끝나야 지울 토큰이 생긴다.
  Future<void> unregister() {
    return _enqueue(() async {
      final token = _registered;
      _registered = null;
      if (token == null) return;
      await _dio
          .delete<Map<String, dynamic>>(
            '/api/push/devices',
            data: {'token': token},
          )
          .timeout(_unregisterTimeout);
    });
  }

  Future<void> _register() async {
    await _messaging.requestPermission();
    if (!_eligible) return;
    final token = await _messaging.token();
    if (token == null) return; // 나중에 onTokenRefresh 가 준다
    await _put(token);
  }

  Future<void> _put(String token) async {
    if (!_eligible) return;
    await unwrapCall<void>(
      () => _dio.put<Map<String, dynamic>>(
        '/api/push/devices',
        data: {'token': token, 'platform': _messaging.platform},
      ),
      (_) {},
    );
    // 올리는 사이 로그아웃했으면 기록하지 않는다 — 다음 사람의 unregister 가
    // 앞 사람 토큰으로 DELETE 를 치게 된다(서버는 내 토큰만 지워서 무해하지만).
    if (_eligible) _registered = token;
  }

  Future<void> _forget() async {
    _registered = null;
    await _messaging.deleteToken();
  }

  Future<void> _enqueue(Future<void> Function() op) {
    final next = _queue.then((_) async {
      try {
        await op();
      } catch (e) {
        debugPrint('[push] 등록 작업 실패: $e');
      }
    });
    _queue = next;
    return next;
  }
}
