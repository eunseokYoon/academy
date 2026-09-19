import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/router/routes.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/icons/app_icon.dart';
import 'package:academy_app/shared/widgets/bottom_tab_bar.dart';

void main() {
  Widget host({int current = 0, ValueChanged<int>? onTap}) => MaterialApp(
    home: Scaffold(
      bottomNavigationBar: BottomTabBar(
        tabs: kStudentTabs,
        currentIndex: current,
        onTap: onTap ?? (_) {},
      ),
    ),
  );

  AppIcon iconAt(WidgetTester tester, int i) => tester.widget<AppIcon>(
    find.descendant(
      of: find.byKey(ValueKey('tab-${kStudentTabs[i].route}')),
      matching: find.byType(AppIcon),
    ),
  );

  Text labelAt(WidgetTester tester, int i) =>
      tester.widget<Text>(find.text(kStudentTabs[i].label));

  testWidgets('다섯 탭의 라벨을 모두 보여준다', (tester) async {
    await tester.pumpWidget(host());
    for (final t in kStudentTabs) {
      expect(find.text(t.label), findsOneWidget);
    }
  });

  testWidgets('현재 탭은 색만이 아니라 굵기도 다르다', (tester) async {
    // 색만으로 구분하면 색각 이상이 있거나 밝은 야외에서 어디 있는지 알 수 없다.
    await tester.pumpWidget(host(current: 1));
    expect(iconAt(tester, 1).strokeWidth, 2.2);
    expect(iconAt(tester, 0).strokeWidth, 1.7);
    expect(labelAt(tester, 1).style!.fontWeight, FontWeight.w700);
    expect(labelAt(tester, 0).style!.fontWeight, FontWeight.w500);
  });

  testWidgets('현재 탭은 accent500, 나머지는 slate400 이다', (tester) async {
    await tester.pumpWidget(host(current: 2));
    expect(iconAt(tester, 2).color.toARGB32(), AppColors.accent500.toARGB32());
    expect(iconAt(tester, 0).color.toARGB32(), AppColors.slate400.toARGB32());
    expect(
      labelAt(tester, 2).style!.color!.toARGB32(),
      AppColors.accent500.toARGB32(),
    );
    expect(
      labelAt(tester, 0).style!.color!.toARGB32(),
      AppColors.slate400.toARGB32(),
    );
  });

  testWidgets('탭을 누르면 그 인덱스를 돌려준다', (tester) async {
    final taps = <int>[];
    await tester.pumpWidget(host(onTap: taps.add));
    await tester.tap(find.text('수업'));
    await tester.pump();
    expect(taps, [2]);
  });

  testWidgets('라벨 글자 크기는 10 이다', (tester) async {
    await tester.pumpWidget(host());
    expect(labelAt(tester, 0).style!.fontSize, 10);
  });

  testWidgets('아이콘 크기는 22 다', (tester) async {
    await tester.pumpWidget(host());
    expect(iconAt(tester, 0).size, 22);
  });

  testWidgets('360px 에서 다섯 탭이 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(host());
    expect(tester.takeException(), isNull);
  });
}
