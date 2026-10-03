import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';

import 'package:academy_app/shared/widgets/youtube_player.dart';

/// 웹뷰를 가짜로 끼운다. JS 채널을 붙잡아 두었다가 「웹뷰가 메시지를 보냈다」를 흉내 낸다.
class _FakePlatform extends WebViewPlatform {
  final controllers = <_FakeController>[];

  @override
  PlatformWebViewController createPlatformWebViewController(
    PlatformWebViewControllerCreationParams params,
  ) {
    final c = _FakeController(params);
    controllers.add(c);
    return c;
  }

  @override
  PlatformNavigationDelegate createPlatformNavigationDelegate(
    PlatformNavigationDelegateCreationParams params,
  ) => _FakeNavigationDelegate(params);

  @override
  PlatformWebViewWidget createPlatformWebViewWidget(
    PlatformWebViewWidgetCreationParams params,
  ) => _FakeWidget(params);
}

class _FakeController extends PlatformWebViewController {
  _FakeController(super.params) : super.implementation();

  final scripts = <String>[];
  JavaScriptChannelParams? channel;

  @override
  Future<void> addJavaScriptChannel(JavaScriptChannelParams params) async =>
      channel = params;

  @override
  Future<void> runJavaScript(String javaScript) async => scripts.add(javaScript);

  @override
  Future<void> loadHtmlString(String html, {String? baseUrl}) async {}

  @override
  Future<void> setJavaScriptMode(JavaScriptMode javaScriptMode) async {}

  @override
  Future<void> setBackgroundColor(Color color) async {}

  @override
  Future<void> setPlatformNavigationDelegate(
    PlatformNavigationDelegate handler,
  ) async {}

  /// 웹뷰의 JS 가 `Watch.postMessage(...)` 를 했다.
  void post(String message) =>
      channel!.onMessageReceived(JavaScriptMessage(message: message));

  int get flushes => scripts.where((s) => s.contains('flushWatch()')).length;
}

class _FakeNavigationDelegate extends PlatformNavigationDelegate {
  _FakeNavigationDelegate(super.params) : super.implementation();

  @override
  Future<void> setOnNavigationRequest(
    NavigationRequestCallback onNavigationRequest,
  ) async {}
}

class _FakeWidget extends PlatformWebViewWidget {
  _FakeWidget(super.params) : super.implementation();

  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}

void main() {
  late _FakePlatform platform;
  late List<(double, List<int>)> reports;

  setUp(() {
    platform = _FakePlatform();
    WebViewPlatform.instance = platform;
    reports = [];
  });

  Future<void> pumpPlayer(WidgetTester tester) => tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: YoutubePlayer(
          embedUrl: 'https://www.youtube.com/embed/abc',
          onWatch: (d, b) => reports.add((d, b)),
        ),
      ),
    ),
  );

  testWidgets('재생 중 보고는 그대로 넘긴다', (tester) async {
    await pumpPlayer(tester);
    platform.controllers.single.post('{"d": 600, "b": [0, 1]}');
    expect(reports.single.$2, [0, 1]);
  });

  // 2026-09-30 리뷰: 떠날 때 보낸 flushWatch() 의 답은 dispose 뒤에 비동기로 온다. 예전에는
  // `!mounted` 에 걸려 매번 버려져 마지막 15초가 시청률에서 빠졌다.
  testWidgets('화면을 떠난 뒤에 도착한 마지막 보고도 넘긴다', (tester) async {
    await pumpPlayer(tester);
    final c = platform.controllers.single;

    await tester.pumpWidget(const SizedBox());
    expect(c.flushes, 1); // 떠날 때 남은 칸을 달라고 했다

    c.post('{"d": 600, "b": [58, 59]}'); // 그 답이 이제 왔다
    expect(reports.single.$2, [58, 59]);
  });

  testWidgets('앱이 뒤로 가면 남은 칸을 보내게 한다(웹의 visibilitychange)', (tester) async {
    await pumpPlayer(tester);
    final c = platform.controllers.single;
    expect(c.flushes, 0);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    expect(c.flushes, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
  });
}
