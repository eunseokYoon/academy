import 'dart:async';

import 'package:academy_app/core/push/push_link.dart';
import 'package:academy_app/core/push/push_messaging.dart';
import 'package:academy_app/core/push/push_setting.dart';

/// 알림 스위치의 서버. [enabled] 가 서버 값이다.
class FakePushSettingRepository implements PushSettingRepository {
  FakePushSettingRepository({this.enabled = true});

  bool enabled;
  Object? getError;
  Object? changeError;
  final List<bool> sent = [];

  /// 설정하면 change 가 이것을 기다린다(바꾸는 중 상태를 보려고).
  Completer<void>? gate;

  @override
  Future<bool> get() async {
    final e = getError;
    if (e != null) throw e;
    return enabled;
  }

  @override
  Future<bool> change(bool value) async {
    sent.add(value);
    await gate?.future;
    final e = changeError;
    if (e != null) throw e;
    enabled = value;
    return enabled;
  }
}

PushSettingController fakePushSetting([FakePushSettingRepository? repo]) =>
    PushSettingController(repository: repo ?? FakePushSettingRepository());

/// FCM 대역. 스트림에 직접 넣는다.
class FakePushMessaging implements PushMessaging {
  FakePushMessaging({this.nextToken = 'token-1', this.initial});

  String? nextToken;
  PushLink? initial;
  int permissionAsks = 0;
  int deletes = 0;
  final refreshes = StreamController<String>.broadcast();
  final opened = StreamController<PushLink>.broadcast();
  final foreground = StreamController<ForegroundPush>.broadcast();

  @override
  String get platform => 'ANDROID';

  @override
  Future<void> requestPermission() async => permissionAsks++;

  @override
  Future<String?> token() async => nextToken;

  @override
  Stream<String> get onTokenRefresh => refreshes.stream;

  @override
  Future<void> deleteToken() async {
    deletes++;
    nextToken = null;
  }

  @override
  Future<PushLink?> initialLink() async {
    final link = initial;
    initial = null;
    return link;
  }

  @override
  Stream<PushLink> get onOpened => opened.stream;

  @override
  Stream<ForegroundPush> get onForeground => foreground.stream;
}
