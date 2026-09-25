import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/features/parent/home/child_selector.dart';
import 'package:academy_app/features/parent/selected_child.dart';

const _two = [
  Child(studentId: 1, name: '김하늘'),
  Child(studentId: 2, name: '김바다'),
];

void main() {
  Widget host(
    List<Child> kids, {
    int? selected = 1,
    ValueChanged<int>? onSelect,
  }) => MaterialApp(
    home: Scaffold(
      body: ChildSelector(
        children: kids,
        selectedStudentId: selected,
        onSelect: onSelect ?? (_) {},
      ),
    ),
  );

  testWidgets('자녀가 하나면 아무것도 그리지 않는다', (tester) async {
    // 고를 게 없는 선택지는 화면만 어지럽힌다.
    await tester.pumpWidget(host(const [Child(studentId: 1, name: '김하늘')]));
    expect(find.text('김하늘'), findsNothing);
    expect(find.text('자녀 선택'), findsNothing);
    expect(find.byType(SizedBox), findsWidgets); // 빈 자리만
  });

  testWidgets('자녀가 둘이면 현재 선택을 보여준다', (tester) async {
    await tester.pumpWidget(host(_two));
    expect(find.text('자녀 선택'), findsOneWidget);
    expect(find.text('김하늘'), findsOneWidget);
  });

  testWidgets('고르면 그 id 를 돌려준다', (tester) async {
    final picks = <int>[];
    await tester.pumpWidget(host(_two, onSelect: picks.add));
    await tester.tap(find.text('김하늘'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('김바다').last);
    await tester.pumpAndSettle();
    expect(picks, [2]);
  });

  testWidgets('목록이 비면 아무것도 그리지 않는다', (tester) async {
    await tester.pumpWidget(host(const [], selected: null));
    expect(tester.takeException(), isNull);
    expect(find.text('자녀 선택'), findsNothing);
  });

  testWidgets('글자는 16 이상이다', (tester) async {
    // iOS 는 16 미만이면 포커스 시 뷰포트를 확대한다(CLAUDE.md 13-4).
    // 닫힌 버튼 글자와 펼친 목록 글자 둘 다 본다.
    await tester.pumpWidget(host(_two));
    final closed = tester.widget<Text>(find.text('김하늘'));
    expect(closed.style!.fontSize, greaterThanOrEqualTo(16));

    await tester.tap(find.text('김하늘'));
    await tester.pumpAndSettle();
    for (final t in tester.widgetList<Text>(find.text('김바다'))) {
      expect(t.style!.fontSize, greaterThanOrEqualTo(16));
    }
  });

  testWidgets('남색 위 버튼은 흰 글씨, 펼친 목록은 흰 바탕에 brand900 이다', (tester) async {
    // 기본 스타일을 두면 남색 위에서 안 보이고, 목록까지 흰 글씨면 흰
    // 목록 위에서 안 보인다.
    await tester.pumpWidget(host(_two));
    expect(tester.widget<Text>(find.text('김하늘')).style!.color, Colors.white);
    final dropdown = tester.widget<DropdownButton<int>>(
      find.byType(DropdownButton<int>),
    );
    expect(dropdown.dropdownColor, Colors.white);

    await tester.tap(find.text('김하늘'));
    await tester.pumpAndSettle();
    // 펼친 목록의 항목(`.last` 가 메뉴 쪽이다).
    expect(
      tester.widget<Text>(find.text('김바다').last).style!.color,
      AppColors.brand900,
    );
  });
}
