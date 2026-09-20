import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/app_theme.dart';
import 'package:academy_app/shared/widgets/section.dart';

void main() {
  testWidgets('제목을 보여준다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SectionHead(title: '안 낸 숙제')),
      ),
    );
    expect(find.text('안 낸 숙제'), findsOneWidget);
  });

  testWidgets('count 를 주면 필이 붙는다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SectionHead(title: '안 낸 숙제', count: 3)),
      ),
    );
    expect(find.text('3'), findsOneWidget);

    // 필 배경은 옅은 틴트가 아니라 COUNT 맵의 진한 색이고, 글자는 흰색이다.
    final pill = tester.widget<Container>(
      find.byKey(const Key('section-count')),
    );
    final d = pill.decoration! as BoxDecoration;
    expect(d.color!.toARGB32(), AppColors.brand600.toARGB32());

    final text = tester.widget<Text>(
      find.descendant(
        of: find.byKey(const Key('section-count')),
        matching: find.byType(Text),
      ),
    );
    expect(text.style!.color, Colors.white);
  });

  testWidgets('count 가 null 이면 필이 없다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SectionHead(title: '공지')),
      ),
    );
    expect(find.byKey(const Key('section-count')), findsNothing);
  });

  testWidgets('count 가 0 이면 필이 없다', (tester) async {
    // 웹의 규칙이다 — 회색 0은 "없음"을 굳이 강조한다.
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SectionHead(title: '공지', count: 0)),
      ),
    );
    expect(find.byKey(const Key('section-count')), findsNothing);
  });

  testWidgets('tone 마다 바 색이 다르다', (tester) async {
    for (final e in {
      SectionTone.accent: AppColors.accent500,
      SectionTone.brand: AppColors.brand600,
      SectionTone.neutral: AppColors.slate300,
    }.entries) {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SectionHead(tone: e.key, title: '제목'),
          ),
        ),
      );
      final bar = tester.widget<Container>(
        find.byKey(const Key('section-bar')),
      );
      expect(
        (bar.decoration! as BoxDecoration).color!.toARGB32(),
        e.value.toARGB32(),
        reason: '${e.key} 의 바 색이 다르다',
      );
    }
  });

  testWidgets('tone 마다 필 색이 다르고, neutral 에서 바와 갈라진다', (tester) async {
    for (final e in {
      SectionTone.accent: AppColors.accent500,
      SectionTone.brand: AppColors.brand600,
      SectionTone.neutral: AppColors.slate400,
    }.entries) {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SectionHead(tone: e.key, title: '제목', count: 3),
          ),
        ),
      );
      final pill = tester.widget<Container>(
        find.byKey(const Key('section-count')),
      );
      expect(
        (pill.decoration! as BoxDecoration).color!.toARGB32(),
        e.value.toARGB32(),
        reason: '${e.key} 의 필 색이 다르다',
      );
    }

    // neutral 에서만 바(slate300)와 필(slate400)이 갈라진다 — 두 맵을 하나로
    // 합치는 실수를 이 단언이 막는다.
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SectionHead(tone: SectionTone.neutral, title: '제목', count: 3),
        ),
      ),
    );
    final bar = tester.widget<Container>(find.byKey(const Key('section-bar')));
    final pill = tester.widget<Container>(
      find.byKey(const Key('section-count')),
    );
    final barColor = (bar.decoration! as BoxDecoration).color!;
    final pillColor = (pill.decoration! as BoxDecoration).color!;
    expect(barColor.toARGB32(), AppColors.slate300.toARGB32());
    expect(pillColor.toARGB32(), AppColors.slate400.toARGB32());
    expect(barColor.toARGB32(), isNot(pillColor.toARGB32()));
  });

  testWidgets('onTapAction 을 주면 액션이 보이고 눌린다', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SectionHead(title: '공지', onTapAction: () => taps++),
        ),
      ),
    );
    // 「전체」만으로는 이제 아무것도 못 찾는다 — 화살표가 붙는다.
    expect(find.text('전체'), findsNothing);
    expect(find.text('전체 ›'), findsOneWidget);
    await tester.tap(find.text('전체 ›'));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('onTapAction 이 없으면 액션이 없다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: SectionHead(title: '공지')),
      ),
    );
    expect(find.text('전체 ›'), findsNothing);
  });

  testWidgets('긴 제목이 360px 에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    final longTitle = List.filled(10, '아주 긴 구획 제목 ').join();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SectionHead(title: longTitle, count: 3, onTapAction: () {}),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('TintBlock 의 띠가 tone 색이고 위아래로 꽉 찬다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: TintBlock(
            tone: SectionTone.accent,
            children: [Padding(padding: EdgeInsets.all(16), child: Text('내용'))],
          ),
        ),
      ),
    );
    final stripe = tester.widget<ColoredBox>(
      find.byKey(const Key('tint-stripe')),
    );
    expect(stripe.color.toARGB32(), AppColors.accent500.toARGB32());

    // 폭 4는 단언하지 않는다. 높이만 블록 자식 영역과 같아야 한다.
    final stripeSize = tester.getSize(find.byKey(const Key('tint-stripe')));
    final blockSize = tester.getSize(find.byKey(const Key('tint-block')));
    expect(stripeSize.height, blockSize.height);
  });

  testWidgets('TintBlock 이 AppShadows.card 를 쓴다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: TintBlock(children: [Text('내용')])),
      ),
    );
    final box = tester.widget<Container>(find.byKey(const Key('tint-block')));
    final d = box.decoration! as BoxDecoration;
    expect(d.boxShadow, AppShadows.card);
  });

  testWidgets('TintBlock 이 자식을 자른다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: TintBlock(children: [Text('내용')])),
      ),
    );
    final box = tester.widget<Container>(find.byKey(const Key('tint-block')));
    expect(box.clipBehavior, isNot(Clip.none));
  });

  testWidgets('자식 N 개면 선이 N-1 개다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: TintBlock(
            tone: SectionTone.brand,
            children: [Text('하나'), Text('둘'), Text('셋')],
          ),
        ),
      ),
    );
    expect(find.byKey(const Key('tint-divider')), findsNWidgets(2));
    final divider = tester.widget<Container>(
      find.byKey(const Key('tint-divider')).first,
    );
    expect(divider.color!.toARGB32(), AppColors.brand100.toARGB32());

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: TintBlock(children: [Text('하나')])),
      ),
    );
    expect(find.byKey(const Key('tint-divider')), findsNothing);
  });

  testWidgets('neutral 배경은 흰색이고 기본 tone 은 brand 다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: TintBlock(tone: SectionTone.neutral, children: [Text('내용')]),
        ),
      ),
    );
    final neutralBox = tester.widget<Container>(
      find.byKey(const Key('tint-block')),
    );
    expect((neutralBox.decoration! as BoxDecoration).color, Colors.white);

    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: TintBlock(children: [Text('내용')])),
      ),
    );
    final defaultBox = tester.widget<Container>(
      find.byKey(const Key('tint-block')),
    );
    expect(
      (defaultBox.decoration! as BoxDecoration).color!.toARGB32(),
      AppColors.brand50.toARGB32(),
    );
  });

  testWidgets('틴트 블록의 세 값은 tone 마다 한 벌로 움직인다', (tester) async {
    // 웹 `Section.tsx`의 BLOCK 주석: 「틴트 블록의 세 값이 한 벌이다 — 왼쪽 바 /
    // 배경 면 / 행 사이 선. 셋 중 하나만 바꾸면 블록이 지저분해진다. 같이 고쳐라」.
    // 그 규칙을 여기서 잠근다 — 셋 중 하나만 다른 tone 것으로 바뀌면 FAIL 이다.
    const triples = {
      SectionTone.accent: (
        AppColors.accent500,
        AppColors.accent50,
        AppColors.accent100,
      ),
      SectionTone.brand: (
        AppColors.brand600,
        AppColors.brand50,
        AppColors.brand100,
      ),
      SectionTone.neutral: (
        AppColors.slate300,
        Colors.white,
        AppColors.slate100,
      ),
    };

    for (final e in triples.entries) {
      final (stripe, background, divider) = e.value;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TintBlock(
              tone: e.key,
              children: const [Text('하나'), Text('둘')],
            ),
          ),
        ),
      );

      final block = tester.widget<Container>(
        find.byKey(const Key('tint-block')),
      );
      expect(
        (block.decoration! as BoxDecoration).color!.toARGB32(),
        background.toARGB32(),
        reason: '${e.key} 의 배경 면',
      );

      final stripeBox = tester.widget<ColoredBox>(
        find.byKey(const Key('tint-stripe')),
      );
      expect(
        stripeBox.color.toARGB32(),
        stripe.toARGB32(),
        reason: '${e.key} 의 왼쪽 바',
      );

      final dividerBox = tester.widget<Container>(
        find.byKey(const Key('tint-divider')),
      );
      expect(
        dividerBox.color!.toARGB32(),
        divider.toARGB32(),
        reason: '${e.key} 의 행 사이 선',
      );
    }
  });
}
