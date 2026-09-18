import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/widgets/submit_button.dart';

void main() {
  testWidgets('평소에는 라벨을 보여준다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(label: '로그인', onPressed: () {}),
        ),
      ),
    );
    expect(find.text('로그인'), findsOneWidget);
  });

  testWidgets('대기 중에는 「처리 중…」 으로 바뀌고 눌리지 않는다', (tester) async {
    // 웹과 같은 문구다. 스피너가 아니라 글자인 것도 웹을 따른다.
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(
            label: '로그인',
            pending: true,
            onPressed: () => taps++,
          ),
        ),
      ),
    );

    expect(find.text('처리 중…'), findsOneWidget);
    expect(find.text('로그인'), findsNothing);

    await tester.tap(find.byType(SubmitButton));
    await tester.pump();
    expect(taps, 0, reason: '대기 중에는 눌리지 않아야 한다');
  });

  testWidgets('onPressed 가 null 이면 비활성이다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SubmitButton(label: '로그인', onPressed: null)),
      ),
    );
    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('대기 중에 onPressed 가 호출되지 않는다', (tester) async {
    var callCount = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(
            label: '로그인',
            pending: true,
            onPressed: () => callCount++,
          ),
        ),
      ),
    );

    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    // onPressed should be null when pending
    expect(button.onPressed, isNull);
    expect(callCount, 0);
  });

  testWidgets('버튼의 배경색이 brand600이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(label: '로그인', onPressed: () {}),
        ),
      ),
    );
    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    final style = button.style!;
    final bgColor = style.backgroundColor!.resolve({});
    expect(bgColor, AppColors.brand600);
  });

  testWidgets('버튼의 최소 높이가 52이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(label: '로그인', onPressed: () {}),
        ),
      ),
    );
    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    final style = button.style!;
    final size = style.minimumSize!.resolve({});
    expect(size?.height, 52);
  });

  testWidgets('버튼의 모서리가 xl(12)이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SubmitButton(label: '로그인', onPressed: () {}),
        ),
      ),
    );
    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    final style = button.style!;
    final shape = style.shape!.resolve({}) as RoundedRectangleBorder;
    expect(shape.borderRadius, BorderRadius.circular(12));
  });
}
