import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 값은 테마 한 곳, 구조는 위젯 — 그 경계를 지키는 장치다.
///
/// 이 테스트가 없어서 A단계의 세 화면이 `#B91C1C` 를 각자 하드코딩했고
/// (최종 리뷰 I7), B단계는 화면이 13개다. 색이 화면으로 새면 「빨강은
/// 결석·위험 하나만」 같은 규칙이 지켜지는지 아무도 확인할 수 없다.
///
/// 잡는 대상은 `app/lib/shared/widgets/` 와 `app/lib/features/` 다(화면이
/// 사는 곳 — B2 에서 넓혔다). `core/theme/` 는 값이 사는 곳이므로 당연히
/// 리터럴이 있다.
void main() {
  test('공용 위젯·화면 안에 16진 색 리터럴이 없다', () {
    final dirs = [Directory('lib/shared/widgets'), Directory('lib/features')];
    for (final dir in dirs) {
      expect(dir.existsSync(), isTrue, reason: '${dir.path} 가 있어야 한다');
    }

    // Color(0x…), Color.fromARGB(…), 맨 16진수 0xAARRGGBB, #RRGGBB 를 잡는다.
    // 맨 16진수 대안이 있는 이유 — `Color(` 가 줄바꿈으로 쪼개져도
    // 포맷팅과 상관없이 잡힌다.
    final pattern = RegExp(
      r'Color\(0x|Color\.fromARGB|\b0x[0-9a-fA-F]{8}\b|#[0-9a-fA-F]{6}\b',
    );
    final offenders = <String>[];

    final files = [
      for (final dir in dirs)
        ...dir.listSync(recursive: true).whereType<File>(),
    ];
    for (final f in files) {
      if (!f.path.endsWith('.dart')) continue;
      final lines = f.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final line = lines[i];
        // 주석에서 웹의 값을 인용하는 것은 허용한다 — 출처를 적는 것이 권장된다.
        final trimmed = line.trimLeft();
        if (trimmed.startsWith('//') || trimmed.startsWith('///')) continue;
        if (pattern.hasMatch(line)) {
          offenders.add('${f.path}:${i + 1}  $trimmed');
        }
      }
    }

    expect(
      offenders,
      isEmpty,
      reason:
          '색은 AppColors 에서 읽어라. 위젯 안에 값을 두지 마라:\n'
          '${offenders.join('\n')}',
    );
  });
}
