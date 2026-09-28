import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/widgets/youtube_player.dart';

void main() {
  test('웹과 같은 재생 인자를 붙이고 playsinline 만 더한다', () {
    expect(
      playerUrlOf('https://www.youtube.com/embed/abc'),
      'https://www.youtube.com/embed/abc'
      '?rel=0&modestbranding=1&autoplay=1&playsinline=1',
    );
  });

  test('재생목록 링크(?list=)는 & 로 잇는다 — 서버 주소를 다시 조립하지 않는다', () {
    expect(
      playerUrlOf('https://www.youtube.com/embed/abc?list=PL1'),
      'https://www.youtube.com/embed/abc?list=PL1'
      '&rel=0&modestbranding=1&autoplay=1&playsinline=1',
    );
  });

  test('플레이어 페이지는 웹 도메인에서 연 것으로 싣는다(Referer 가 없으면 오류 153)', () {
    expect(kPlayerOrigin, 'https://njwenglish.com');
  });
}
