import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/widgets/stub_page.dart';

void main() {
  testWidgets('제목과 단계 안내를 보여준다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: StubPage(title: '숙제', stage: 'B2'),
      ),
    );
    expect(find.text('숙제'), findsOneWidget);
    expect(find.textContaining('B2'), findsOneWidget);
  });

  testWidgets('bottom 을 안 주면 아무것도 더 그리지 않는다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: StubPage(title: '수업', stage: 'B3'),
      ),
    );
    expect(find.byKey(const Key('stub-bottom')), findsNothing);
  });

  testWidgets('bottom 을 주면 그 자리에 그린다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: StubPage(
          title: '성적',
          stage: 'B3',
          bottom: TextButton(
            key: const Key('probe'),
            onPressed: () {},
            child: const Text('로그아웃'),
          ),
        ),
      ),
    );
    expect(find.byKey(const Key('probe')), findsOneWidget);
    expect(find.text('로그아웃'), findsOneWidget);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      const MaterialApp(
        home: StubPage(title: '개인정보처리방침', stage: 'B3'),
      ),
    );
    expect(tester.takeException(), isNull);
  });
}
