import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/widgets/youtube_player.dart';

void main() {
  test('웹과 같은 재생 인자를 붙이고 playsinline 만 더한다', () {
    expect(
      playerUrlOf('https://www.youtube.com/embed/abc'),
      'https://www.youtube.com/embed/abc'
      '?rel=0&modestbranding=1&autoplay=1&playsinline=1'
      '&enablejsapi=1&origin=https%3A%2F%2Fnjwenglish.com',
    );
  });

  test('재생목록 링크(?list=)는 & 로 잇는다 — 서버 주소를 다시 조립하지 않는다', () {
    expect(
      playerUrlOf('https://www.youtube.com/embed/abc?list=PL1'),
      'https://www.youtube.com/embed/abc?list=PL1'
      '&rel=0&modestbranding=1&autoplay=1&playsinline=1'
      '&enablejsapi=1&origin=https%3A%2F%2Fnjwenglish.com',
    );
  });

  test('플레이어 페이지는 웹 도메인에서 연 것으로 싣는다(Referer 가 없으면 오류 153)', () {
    expect(kPlayerOrigin, 'https://njwenglish.com');
  });

  test('JS 가 보낸 시청 보고를 읽고, 모양이 틀리면 버린다', () {
    final report = parseWatchReport('{"d": 612.5, "b": [0, 1, 7]}')!;
    expect(report.$1, 612.5);
    expect(report.$2, [0, 1, 7]);
    expect(parseWatchReport('{"d": 0, "b": [1]}'), isNull);
    expect(parseWatchReport('{"d": 10, "b": []}'), isNull);
    expect(parseWatchReport('nope'), isNull);
  });

  test('칸 길이가 서버와 같다(10초)', () {
    expect(kWatchBucketSeconds, 10);
  });
}
