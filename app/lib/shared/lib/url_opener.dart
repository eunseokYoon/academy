import 'package:url_launcher/url_launcher.dart';

/// 주소를 기기에 넘긴다(공지 첨부 받기, 수업 영상 보기). 테스트가 가짜를 넣는다.
/// 못 열었으면 false 다.
typedef UrlOpener = Future<bool> Function(Uri url);

/// 기본값 — 앱 밖(시스템 브라우저·YouTube 앱)으로 연다.
Future<bool> openExternally(Uri url) =>
    launchUrl(url, mode: LaunchMode.externalApplication);
