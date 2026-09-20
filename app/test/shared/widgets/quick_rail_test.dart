import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/icons/icon_paths.dart';
import 'package:academy_app/shared/widgets/quick_rail.dart';

const _items = [
  QuickItem(
    route: '/a',
    icon: AppIconName.homework,
    label: '숙제',
    count: 3,
    primary: true,
  ),
  QuickItem(route: '/b', icon: AppIconName.video, label: '수업'),
  QuickItem(route: '/c', icon: AppIconName.megaphone, label: '공지', count: 2),
];

void main() {
  Widget host({ValueChanged<String>? onTap}) => MaterialApp(
    home: Scaffold(
      body: QuickRail(items: _items, onTap: onTap ?? (_) {}),
    ),
  );

  testWidgets('모든 칸의 라벨을 보여준다', (tester) async {
    await tester.pumpWidget(host());
    for (final i in _items) {
      expect(find.text(i.label), findsOneWidget);
    }
  });

  testWidgets('primary 칸만 남색으로 채운다', (tester) async {
    await tester.pumpWidget(host());
    final first = tester.widget<Container>(find.byKey(const Key('rail-/a')));
    final second = tester.widget<Container>(find.byKey(const Key('rail-/b')));
    expect(
      (first.decoration! as BoxDecoration).color!.toARGB32(),
      AppColors.brand900.toARGB32(),
    );
    expect(
      (second.decoration! as BoxDecoration).color!.toARGB32(),
      AppColors.brand50.toARGB32(),
    );
  });

  testWidgets('count 가 0보다 크면 점이 붙고 숫자는 안 쓴다', (tester) async {
    // 알아야 하는 건 개수가 아니라 「볼 게 있다」뿐이다.
    await tester.pumpWidget(host());
    expect(find.byKey(const Key('dot-/a')), findsOneWidget);
    expect(find.byKey(const Key('dot-/c')), findsOneWidget);
    expect(find.byKey(const Key('dot-/b')), findsNothing);
    expect(find.text('3'), findsNothing);
    expect(find.text('2'), findsNothing);
  });

  testWidgets('칸을 누르면 경로를 돌려준다', (tester) async {
    final taps = <String>[];
    await tester.pumpWidget(host(onTap: taps.add));
    await tester.tap(find.text('수업'));
    await tester.pump();
    expect(taps, ['/b']);
  });

  testWidgets('가로로 스크롤된다', (tester) async {
    await tester.pumpWidget(host());
    final list = tester.widget<ListView>(find.byType(ListView));
    expect(list.scrollDirection, Axis.horizontal);
  });

  testWidgets('레일이 자기 가로 여백을 갖는다', (tester) async {
    // 카드가 가로 패딩을 주지 않으므로 레일이 직접 16 을 갖는다.
    // 그래야 마지막 칸이 화면 밖으로 잘려 나간다 — 이 레일의 유일한 어포던스다.
    await tester.pumpWidget(host());
    final list = tester.widget<ListView>(find.byType(ListView));
    expect(list.padding, const EdgeInsets.symmetric(horizontal: 16));
  });
}
