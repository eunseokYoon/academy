import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/branding.dart';
import 'package:academy_app/shared/widgets/app_bar_band.dart';
import 'package:academy_app/shared/widgets/logo.dart';

void main() {
  Widget host(String role) => MaterialApp(
    home: Scaffold(body: AppBarBand(role: role)),
  );

  testWidgets('로고와 워드마크와 역할 칩을 보여준다', (tester) async {
    await tester.pumpWidget(host('학생'));
    expect(find.byType(LogoBadge), findsOneWidget);
    expect(find.byType(Wordmark), findsOneWidget);
    expect(find.text('학생'), findsOneWidget);
    expect(find.textContaining(academyNameHead), findsOneWidget);
  });

  testWidgets('역할 문자열을 그대로 쓴다', (tester) async {
    await tester.pumpWidget(host('학부모'));
    expect(find.text('학부모'), findsOneWidget);
    expect(find.text('학생'), findsNothing);
  });

  testWidgets('띠 색이 brand900 이다', (tester) async {
    // 홈의 지면 그라디언트가 같은 값으로 시작한다. 다르면 파고든 자리에
    // 가로줄이 그어져 한 덩어리가 아니라 상자 둘로 보인다.
    await tester.pumpWidget(host('학생'));
    final box = tester.widget<ColoredBox>(
      find
          .descendant(
            of: find.byType(AppBarBand),
            matching: find.byType(ColoredBox),
          )
          .first,
    );
    expect(box.color.toARGB32(), AppColors.brand900.toARGB32());
  });

  testWidgets('아래 32px 자리가 있다', (tester) async {
    // 홈 카드가 걸터앉을 자리다. 줄이면 카드가 로고 줄을 덮는다.
    await tester.pumpWidget(host('학생'));
    final pad = tester.widget<Padding>(
      find.byKey(const Key('app-bar-padding')),
    );
    expect((pad.padding as EdgeInsets).bottom, 12 + 32);
  });

  testWidgets('역할 칩 색이 brand300 이고 무게가 w500 이다', (tester) async {
    await tester.pumpWidget(host('학생'));
    final t = tester.widget<Text>(find.text('학생'));
    expect(t.style!.color!.toARGB32(), AppColors.brand300.toARGB32());
    expect(t.style!.fontSize, 12);
    expect(t.style!.fontWeight, FontWeight.w500);
  });

  testWidgets('360px 너비에서도 깨지지 않는다', (tester) async {
    // 학부모 역할 문자가 가장 길다.
    // ignore: deprecated_member_use
    tester.binding.window.physicalSizeTestValue = const Size(360 * 3, 800 * 3);
    // ignore: deprecated_member_use
    addTearDown(tester.binding.window.clearPhysicalSizeTestValue);

    await tester.pumpWidget(host('학부모'));
    expect(find.byType(AppBarBand), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('SafeArea의 bottom이 false 이다', (tester) async {
    // 제스처 네비게이션 폰의 34px 하단 inset이 로고 줄 아래로 흘러내리면 안 된다.
    await tester.pumpWidget(host('학생'));
    final safeArea = tester.widget<SafeArea>(
      find.descendant(
        of: find.byType(AppBarBand),
        matching: find.byType(SafeArea),
      ),
    );
    expect(safeArea.bottom, false);
  });
}
