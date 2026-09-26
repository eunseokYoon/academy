import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/widgets/notice_card.dart';

const _recent = [
  NoticeSummary(id: 1, title: '추석 휴원 안내', createdAt: '09/15', pinned: true),
  NoticeSummary(id: 2, title: '10월 시험 일정', createdAt: '09/12'),
];

void main() {
  Widget host({
    List<NoticeSummary> recent = _recent,
    int total = 7,
    ValueChanged<int>? onOne,
    VoidCallback? onAll,
  }) => MaterialApp(
    home: Scaffold(
      body: NoticeCard(
        totalCount: total,
        recent: recent,
        onTapAll: onAll ?? () {},
        onTapOne: onOne ?? (_) {},
      ),
    ),
  );

  testWidgets('최근 공지 제목과 날짜를 보여준다', (tester) async {
    await tester.pumpWidget(host());
    expect(find.text('추석 휴원 안내'), findsOneWidget);
    expect(find.text('09/15'), findsOneWidget);
    expect(find.text('10월 시험 일정'), findsOneWidget);
  });

  testWidgets('전체 건수가 구획 머리의 필에 붙는다', (tester) async {
    await tester.pumpWidget(host(total: 7));
    expect(
      find.descendant(
        of: find.byKey(const Key('section-count')),
        matching: find.text('7'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('공지가 없으면 빈 문구가 보이고 줄이 없다', (tester) async {
    // 블록 자체는 남긴다 — 사라지면 학생이 공지가 있는 곳을 잊는다.
    await tester.pumpWidget(host(recent: const [], total: 0));
    expect(find.text('등록된 공지가 없습니다.'), findsOneWidget);
    expect(find.byKey(const Key('notice-row')), findsNothing);
  });

  testWidgets('고정 공지에만 채운 남색 「고정」 배지가 붙는다', (tester) async {
    // 웹 주석: 옅게 두면 남색 면 위에서 배경과 붙어 "고정"인지가 안 읽힌다.
    await tester.pumpWidget(host());
    expect(find.text('고정'), findsOneWidget);

    final badge = tester.widget<Container>(
      find.byKey(const Key('notice-pinned')),
    );
    final decoration = badge.decoration! as BoxDecoration;
    expect(decoration.color!.toARGB32(), AppColors.brand600.toARGB32());
  });

  testWidgets('줄을 누르면 그 줄의 id 가 넘어온다', (tester) async {
    final taps = <int>[];
    await tester.pumpWidget(host(onOne: taps.add));
    await tester.tap(find.text('10월 시험 일정'));
    await tester.pump();
    expect(taps, [2]);
  });

  testWidgets('「전체 ›」를 누르면 콜백이 온다', (tester) async {
    var taps = 0;
    await tester.pumpWidget(host(onAll: () => taps++));
    await tester.tap(find.text('전체 ›'));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('줄은 틴트 블록 한 장 안에 들어 있다', (tester) async {
    // 낱장 카드로 되돌아가지 않는다는 잠금이다.
    await tester.pumpWidget(host());
    expect(find.byKey(const Key('tint-block')), findsOneWidget);
    expect(find.byKey(const Key('tint-divider')), findsOneWidget);
  });

  testWidgets('360px 에서 긴 제목이 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      host(
        recent: const [
          NoticeSummary(
            id: 1,
            title: '아주아주아주아주아주아주아주아주아주아주아주아주 긴 공지 제목입니다 그리고 더 깁니다',
            createdAt: '09/15',
            pinned: true,
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
