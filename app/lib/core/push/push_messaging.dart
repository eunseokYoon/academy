import 'dart:async';
import 'dart:io';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

import 'push_link.dart';

/// 앱이 떠 있을 때 도착한 알림. 시스템 알림이 뜨지 않으므로 앱이 스낵바로 알린다.
class ForegroundPush {
  const ForegroundPush({required this.title, required this.link});

  final String? title;
  final PushLink? link;
}

/// FCM 과 앱 사이의 경계. 테스트는 가짜를 넣는다 — 위젯 테스트에는 Firebase 가 없다.
abstract class PushMessaging {
  /// 서버 `DevicePlatform` 값.
  String get platform;

  /// Android 13+ 와 iOS 의 권한 창. 거절해도 등록은 한다 — 서버가 보내도 기기가
  /// 안 띄울 뿐이고, 설정에서 켜면 그때부터 온다.
  Future<void> requestPermission();

  /// 이 기기의 등록 토큰. 아직 못 받으면 null — [onTokenRefresh] 가 나중에 준다.
  Future<String?> token();

  Stream<String> get onTokenRefresh;

  /// 이 기기의 토큰을 무효로 만든다. 서버에서 못 지운 토큰(세션 만료로 로그아웃)도
  /// 다음 발송 때 FCM 이 UNREGISTERED 를 돌려 서버가 지운다.
  Future<void> deleteToken();

  /// 앱이 꺼져 있을 때 알림을 눌러 켰으면 그 링크. 한 번만 준다.
  Future<PushLink?> initialLink();

  /// 백그라운드에서 알림을 눌러 돌아왔다.
  Stream<PushLink> get onOpened;

  Stream<ForegroundPush> get onForeground;
}

/// 실제 FCM. `main()` 에서 [create] 로 만든다.
///
/// 설정 파일(`google-services.json`·`GoogleService-Info.plist`)은 저장소에 없다
/// (커밋하지 않기로 했다). 없는 기계에서 빌드한 앱은 초기화가 실패하고, 그때는
/// [DisabledPushMessaging] 으로 떨어져 알림만 빠진다 — 로그인·화면은 멀쩡하다.
class FirebasePushMessaging implements PushMessaging {
  FirebasePushMessaging._(this._fm);

  final FirebaseMessaging _fm;

  static Future<PushMessaging> create() async {
    try {
      await Firebase.initializeApp();
      return FirebasePushMessaging._(FirebaseMessaging.instance);
    } catch (e) {
      debugPrint('[push] Firebase 초기화 실패 — 알림 없이 뜬다: $e');
      return const DisabledPushMessaging();
    }
  }

  @override
  String get platform => Platform.isIOS ? 'IOS' : 'ANDROID';

  @override
  Future<void> requestPermission() async {
    await _fm.requestPermission();
  }

  @override
  Future<String?> token() async {
    if (Platform.isIOS) {
      // APNs 토큰이 오기 전에 getToken 을 부르면 apns-token-not-set 으로 던진다.
      // 조금 기다려 보고, 그래도 없으면 onTokenRefresh 에 맡긴다.
      for (var i = 0; i < 5; i++) {
        if (await _fm.getAPNSToken() != null) break;
        await Future<void>.delayed(const Duration(seconds: 1));
      }
      if (await _fm.getAPNSToken() == null) return null;
    }
    final token = await _fm.getToken();
    // 개발용. 콘솔 「테스트 메시지 보내기」에 붙여 넣는다. 릴리스에는 안 찍힌다.
    if (kDebugMode && token != null) debugPrint('[push] FCM token: $token');
    return token;
  }

  @override
  Stream<String> get onTokenRefresh => _fm.onTokenRefresh;

  @override
  Future<void> deleteToken() => _fm.deleteToken();

  @override
  Future<PushLink?> initialLink() async {
    final m = await _fm.getInitialMessage();
    return m == null ? null : PushLink.fromData(m.data);
  }

  @override
  Stream<PushLink> get onOpened => FirebaseMessaging.onMessageOpenedApp
      .map((m) => PushLink.fromData(m.data))
      .where((l) => l != null)
      .cast<PushLink>();

  @override
  Stream<ForegroundPush> get onForeground => FirebaseMessaging.onMessage.map(
    (m) => ForegroundPush(
      title: m.notification?.title,
      link: PushLink.fromData(m.data),
    ),
  );
}

/// 알림이 없는 앱. Firebase 설정이 없는 빌드와 테스트의 기본값이다.
class DisabledPushMessaging implements PushMessaging {
  const DisabledPushMessaging();

  @override
  String get platform => Platform.isIOS ? 'IOS' : 'ANDROID';

  @override
  Future<void> requestPermission() async {}

  @override
  Future<String?> token() async => null;

  @override
  Stream<String> get onTokenRefresh => const Stream.empty();

  @override
  Future<void> deleteToken() async {}

  @override
  Future<PushLink?> initialLink() async => null;

  @override
  Stream<PushLink> get onOpened => const Stream.empty();

  @override
  Stream<ForegroundPush> get onForeground => const Stream.empty();
}
