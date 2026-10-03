import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:webview_flutter_wkwebview/webview_flutter_wkwebview.dart';

import 'package:academy_app/shared/lib/url_opener.dart';

/// 플레이어가 앉은 페이지의 주소. **웹 서비스의 도메인이어야 한다.**
///
/// YouTube 임베드는 Referer 가 없으면 재생을 거절한다(「오류 153 · 동영상 플레이어
/// 구성 오류」). 앱의 웹뷰는 페이지 주소가 없어 Referer 가 비므로, iframe 을 담은
/// HTML 을 이 주소에서 연 것처럼 싣는다. 웹과 같은 도메인이라 YouTube 가 보는
/// 조건도 웹과 같다 — 웹에서 재생되는 영상은 앱에서도 재생된다.
const kPlayerOrigin = 'https://njwenglish.com';

/// 웹 `StudentLessonDetailPage.tsx` 가 iframe 주소에 붙이는 재생 인자와 같다.
/// `playsinline=1` 만 더한다 — iOS 는 그게 없으면 누르자마자 전체 화면으로 튄다.
///
/// **주소를 다시 조립하지 마라** — 서버의 `embedUrl` 에 인자만 붙인다
/// (`YoutubeUrls.embedUrlOf` 가 정본이다. 재생목록 `?list=` 가 이미 있을 수 있다).
String playerUrlOf(String embedUrl) =>
    '$embedUrl${embedUrl.contains('?') ? '&' : '?'}'
    'rel=0&modestbranding=1&autoplay=1&playsinline=1'
    // IFrame API 가 이 플레이어를 붙잡으려면 둘 다 있어야 한다(시청 기록).
    '&enablejsapi=1&origin=${Uri.encodeComponent(kPlayerOrigin)}';

/// 시청 기록의 칸 길이(초). 서버 `LessonVideoWatch.BUCKET_SECONDS` 와 같아야 한다.
const kWatchBucketSeconds = 10;

/// 웹뷰의 JS 가 보낸 `{"d": 길이, "b": [칸...]}`. 모양이 틀리면 null 이다(버린다).
(double, List<int>)? parseWatchReport(String raw) {
  try {
    final json = jsonDecode(raw) as Map<String, dynamic>;
    final duration = (json['d'] as num).toDouble();
    final buckets = [
      for (final b in json['b'] as List<dynamic>) (b as num).toInt(),
    ];
    if (duration <= 0 || buckets.isEmpty) return null;
    return (duration, buckets);
  } catch (_) {
    return null;
  }
}

/// 수업 영상 한 편. 웹처럼 화면 안의 16:9 칸에서 재생한다.
///
/// 누르기 전에는 만들지 않는다(부르는 쪽이 버튼을 먼저 그린다) — 웹이 처음부터
/// iframe 을 심지 않는 것과 같은 이유로, 웹뷰는 무겁다.
///
/// 플레이어 안의 링크(YouTube 로고·「YouTube에서 보기」)는 웹뷰 안에서 열지 않고
/// 기기로 넘긴다. 안에서 열면 youtube.com 전체가 16:9 칸 안에 뜬다.
class YoutubePlayer extends StatefulWidget {
  const YoutubePlayer({
    super.key,
    required this.embedUrl,
    this.openUrl,
    this.onWatch,
  });

  final String embedUrl;

  /// 시청 기록(2026-09-29). 플레이어가 **실제로 재생한** 10초 칸 번호와 영상 길이를
  /// 15초마다·멈춤·끝에 넘긴다. 웹 `shared/lesson/youtubeWatch.ts` 와 같은 규칙이다 —
  /// 화면을 열어 두기만 하거나 건너뛴 구간은 안 쌓인다.
  final void Function(double durationSeconds, List<int> buckets)? onWatch;

  /// 플레이어 밖으로 나가는 링크. 없으면 시스템 브라우저·YouTube 앱이다.
  final UrlOpener? openUrl;

  @override
  State<YoutubePlayer> createState() => _YoutubePlayerState();
}

class _YoutubePlayerState extends State<YoutubePlayer> {
  late final WebViewController _controller = _build();

  /// 시청 보고를 받을 곳. **화면이 사라진 뒤에도 쓴다** — 떠날 때 보낸 마지막 보고는
  /// [dispose] 뒤에 비동기로 도착한다(2026-09-30 리뷰: 예전에는 `!mounted` 에 걸려 매번
  /// 버려졌다). 받는 쪽은 세션 수명 컨트롤러라 화면이 없어도 서버로 보낼 수 있다.
  void Function(double, List<int>)? _onWatch;

  /// 앱이 뒤로 가면 남은 칸을 보낸다 — 웹 `youtubeWatch.ts` 의 `visibilitychange` 와 같다.
  /// 다시 안 돌아오면(앱이 종료되면) 그 칸이 마지막이다.
  late final AppLifecycleListener _lifecycle = AppLifecycleListener(onHide: _flush);

  @override
  void initState() {
    super.initState();
    _onWatch = widget.onWatch;
    _lifecycle; // 리스너를 지금 붙인다
  }

  @override
  void didUpdateWidget(covariant YoutubePlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    _onWatch = widget.onWatch;
  }

  void _flush() {
    _controller.runJavaScript('window.flushWatch && flushWatch()').catchError((_) {});
  }

  WebViewController _build() {
    final params = Platform.isIOS
        ? WebKitWebViewControllerCreationParams(
            allowsInlineMediaPlayback: true,
            mediaTypesRequiringUserAction: const <PlaybackMediaTypes>{},
          )
        : const PlatformWebViewControllerCreationParams();
    final controller = WebViewController.fromPlatformCreationParams(params)
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.black)
      ..setNavigationDelegate(
        NavigationDelegate(onNavigationRequest: _onNavigation),
      )
      ..addJavaScriptChannel('Watch', onMessageReceived: _onWatchMessage);
    final platform = controller.platform;
    if (platform is AndroidWebViewController) {
      // 사용자가 이미 「시청하기」를 눌렀다. 한 번 더 누르게 하지 않는다.
      platform.setMediaPlaybackRequiresUserGesture(false);
      platform.setCustomWidgetCallbacks(
        onShowCustomWidget: _showFullscreen,
        onHideCustomWidget: _hideFullscreen,
      );
    }
    controller.loadHtmlString(_html(widget.embedUrl), baseUrl: kPlayerOrigin);
    return controller;
  }

  /// `mounted` 를 보지 않는다 — [_onWatch] 주석을 봐라. 화면을 그리지 않으니 안전하다.
  void _onWatchMessage(JavaScriptMessage message) {
    final report = parseWatchReport(message.message);
    if (report == null) return;
    _onWatch?.call(report.$1, report.$2);
  }

  @override
  void dispose() {
    // 떠날 때 남은 칸을 보낸다. 웹뷰가 먼저 닫히면 마지막 15초 안쪽은 잃는다(받아들인 손실이다).
    _flush();
    _lifecycle.dispose();
    super.dispose();
  }

  NavigationDecision _onNavigation(NavigationRequest request) {
    // iframe 안의 이동(플레이어 자체)은 둔다. 막는 것은 페이지 전체가 바뀌는 경우다.
    if (!request.isMainFrame ||
        request.url == 'about:blank' ||
        request.url.startsWith(kPlayerOrigin)) {
      return NavigationDecision.navigate;
    }
    final uri = Uri.tryParse(request.url);
    if (uri != null) (widget.openUrl ?? openExternally)(uri);
    return NavigationDecision.prevent;
  }

  // Android 의 전체 화면 버튼. 셸의 탭 바 위로 덮어야 해서 루트 내비게이터에 올린다.
  // (iOS 는 WKWebView 가 알아서 한다.)
  Route<void>? _fullscreen;
  bool _hiddenByPage = false;

  void _showFullscreen(Widget view, void Function() onHidden) {
    _hiddenByPage = false;
    final route = MaterialPageRoute<void>(
      fullscreenDialog: true,
      builder: (_) => ColoredBox(color: Colors.black, child: view),
    );
    _fullscreen = route;
    Navigator.of(context, rootNavigator: true).push(route).then((_) {
      _fullscreen = null;
      // 뒤로 가기로 닫았으면 페이지에 알려야 플레이어가 작은 칸으로 돌아온다.
      if (!_hiddenByPage) onHidden();
    });
  }

  void _hideFullscreen() {
    final route = _fullscreen;
    if (route == null || !mounted) return;
    _hiddenByPage = true;
    Navigator.of(context, rootNavigator: true).removeRoute(route);
    _fullscreen = null;
  }

  @override
  Widget build(BuildContext context) =>
      WebViewWidget(key: const Key('youtube-player'), controller: _controller);
}

String _html(String embedUrl) {
  final src = const HtmlEscape(HtmlEscapeMode.attribute)
      .convert(playerUrlOf(embedUrl));
  return '''<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
</head><body>
<iframe id="p" src="$src" title="수업 영상"
 allow="accelerometer; autoplay; clipboard-write; encrypted-media; picture-in-picture"
 referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
<script>
// 시청 기록. 1초마다 재생 중(1)이면 재생 위치의 10초 칸을 칠하고, 15초마다·멈춤(2)·끝(0)에 보낸다.
var player = null, pending = {};
function flushWatch() {
  if (!player || !window.Watch) return;
  var keys = Object.keys(pending);
  var d = player.getDuration ? player.getDuration() : 0;
  if (keys.length === 0 || !(d > 0)) return;
  pending = {};
  Watch.postMessage(JSON.stringify({d: d, b: keys.map(Number)}));
}
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {events: {onStateChange: function (e) {
    if (e.data === 0 || e.data === 2) flushWatch();
  }}});
}
setInterval(function () {
  if (player && player.getPlayerState && player.getPlayerState() === 1) {
    pending[Math.floor(player.getCurrentTime() / $kWatchBucketSeconds)] = true;
  }
}, 1000);
setInterval(flushWatch, 15000);
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>''';
}
