import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/branding.dart';
import 'package:academy_app/shared/widgets/logo.dart';

void main() {
  test('상호는 두 조각으로 나뉘어 있다', () {
    // 로고와 같은 잠금이다 — 한글은 흰색, LAB 만 주황.
    // 붙여 쓰는 이유는 상호가 "남지원영어LAB" 이라 사이에 공백이 없기 때문이다.
    expect(academyNameHead, '남지원영어');
    expect(academyNameTail, 'LAB');
    expect(academyName, '남지원영어LAB');
  });

  testWidgets('워드마크가 LAB 만 주황으로 그린다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: Wordmark())),
    );

    // 이름을 통째로 흰색으로 두면 로고와 다른 물건으로 보인다.
    // 주황 두 글자가 앱바와 로고를 같은 것으로 묶는다.
    final text = tester.widget<Text>(find.byType(Text));
    final spans = (text.textSpan! as TextSpan).children!;
    expect((spans[0] as TextSpan).text, academyNameHead);
    expect((spans[1] as TextSpan).text, academyNameTail);
    expect((spans[1] as TextSpan).style?.color, isNotNull);
  });

  testWidgets('로고 마크가 그려진다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: LogoMark(size: 42))),
    );
    expect(find.byType(CustomPaint), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('배지가 마크를 감싼다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: LogoBadge(size: 68, markSize: 42)),
      ),
    );
    expect(find.byType(LogoMark), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
