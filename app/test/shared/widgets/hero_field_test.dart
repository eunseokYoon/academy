import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/app_theme.dart';
import 'package:academy_app/shared/widgets/hero_field.dart';

void main() {
  Widget host({List<HeroStat> stats = const [], Widget? child}) => MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        child: HeroField(
          eyebrow: '9월 19일 (토)',
          title: '김하늘 님,',
          stats: stats,
          child: child,
        ),
      ),
    ),
  );

  test('그라디언트 맨 위는 brand900 이다', () {
    // 앱바가 그 단색이라, 다른 값으로 시작하면 지면이 앱바를 파고든 자리에
    // 가로줄이 그어져 한 덩어리가 아니라 상자 둘로 보인다.
    expect(
      fieldGradient.colors.first.toARGB32(),
      AppColors.brand900.toARGB32(),
    );
    expect(fieldGradient.stops!.first, 0.0);
    // 아래로만 어두워진다. 밝히는 방향으로 뒤집지 마라.
    expect(fieldGradient.colors.last.toARGB32(), 0xFF16233A);
  });

  testWidgets('eyebrow 와 title 을 보여준다', (tester) async {
    await tester.pumpWidget(host());
    expect(find.text('9월 19일 (토)'), findsOneWidget);
    expect(find.text('김하늘 님,'), findsOneWidget);
  });

  testWidgets('칸을 준 개수만큼 그린다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2', sub: '09/21 19:00'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    expect(find.text('다음 수업'), findsOneWidget);
    expect(find.text('D-2'), findsOneWidget);
    expect(find.text('09/21 19:00'), findsOneWidget);
    expect(find.text('안 낸 숙제'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('칸이 없으면 칸 줄 자체를 안 그린다', (tester) async {
    await tester.pumpWidget(host());
    expect(find.byKey(const Key('hero-stats')), findsNothing);
  });

  testWidgets('hot 칸만 주황 계열이고 나머지는 흰 반투명이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    final normal = tester.widget<Container>(find.byKey(const Key('stat-0')));
    final hot = tester.widget<Container>(find.byKey(const Key('stat-1')));
    final nd = normal.decoration! as BoxDecoration;
    final hd = hot.decoration! as BoxDecoration;
    // 평소 칸은 남색 위에 흰 반투명을 판다 — 흰 카드를 얹으면 지면이 사라진다.
    expect(nd.color, Colors.white.withValues(alpha: 0.07));
    // hot 은 accent400 을 22% 로 깐다.
    expect(hd.color, AppColors.accent400.withValues(alpha: 0.22));
    expect(nd.borderRadius, BorderRadius.circular(AppRadii.xxl));
  });

  testWidgets('extra 를 주면 같은 칸 안에 둘째 줄이 생긴다', (tester) async {
    // 칸을 넷으로 늘리는 대신 쓴다. 360px 에서 칸이 넷이면 D-61 이 줄바꿈된다.
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(
            label: '다음 수업',
            value: 'D-2',
            extra: HeroStatExtra(
              label: '다음 클리닉',
              value: 'D-1',
              sub: '09/20 17:00',
            ),
          ),
        ],
      ),
    );
    expect(find.byKey(const Key('stat-0')), findsOneWidget);
    expect(find.text('다음 클리닉'), findsOneWidget);
    expect(find.text('D-1'), findsOneWidget);
  });

  testWidgets('child 를 주면 칸 아래에 그린다', (tester) async {
    // 학부모 홈의 자녀 선택이 여기 들어간다.
    await tester.pumpWidget(
      host(
        stats: const [HeroStat(label: '다음 수업', value: 'D-2')],
        child: const Text('자녀선택자리'),
      ),
    );
    expect(find.text('자녀선택자리'), findsOneWidget);
  });

  testWidgets('아래 여백이 64 다', (tester) async {
    // 다음 카드가 40 올라와 걸터앉을 자리다. 줄이면 카드가 지면 밖으로 나간다.
    await tester.pumpWidget(host());
    final box = tester.widget<Container>(find.byKey(const Key('hero-field')));
    expect((box.padding as EdgeInsets).bottom, 64);
  });

  testWidgets('360px 에서 칸 셋이 가로로 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-61', sub: '11/20 19:00'),
          HeroStat(label: '다음 시험', value: 'D-12', sub: '10/01'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('stat 칸은 내용 높이가 다르면 stretch 로 같게 맞춘다', (tester) async {
    // IntrinsicHeight + stretch 로 해야 다른 높이의 칸들이 같아진다.
    // 한 칸에는 sub 가 있고 다른 칸에는 없으면, stretch 없이는 높이가 다르다.
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2', sub: '09/21 19:00'),
          HeroStat(label: '안 낸 숙제', value: '3'),
        ],
      ),
    );
    final stat0 = tester.getRect(find.byKey(const Key('stat-0')));
    final stat1 = tester.getRect(find.byKey(const Key('stat-1')));
    // 두 칸의 높이가 같아야 한다 (IntrinsicHeight + stretch).
    expect(stat0.height, stat1.height);
  });

  testWidgets('hot 칸의 라벨은 accent200 이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    // "안 낸 숙제" 라벨을 찾기 위해 hot 칸의 text widget들을 검색
    final hotLabelText = tester.widget<Text>(
      find
          .descendant(
            of: find.byKey(const Key('stat-1')),
            matching: find.byType(Text),
          )
          .first,
    );
    final style = hotLabelText.style!;
    expect(style.color, AppColors.accent200);
  });

  testWidgets('hot 칸의 값은 accent100 이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    // hot 칸의 두 번째 Text widget (값)을 찾기
    final stat1Texts = find.descendant(
      of: find.byKey(const Key('stat-1')),
      matching: find.byType(Text),
    );
    final valueText = tester.widget<Text>(stat1Texts.at(1));
    final style = valueText.style!;
    expect(style.color, AppColors.accent100);
  });

  testWidgets('일반 칸의 라벨은 brand200 75% 이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    // 일반 칸(stat-0)의 첫 번째 Text widget (라벨)
    final stat0Texts = find.descendant(
      of: find.byKey(const Key('stat-0')),
      matching: find.byType(Text),
    );
    final labelText = tester.widget<Text>(stat0Texts.first);
    final style = labelText.style!;
    expect(style.color, AppColors.brand200.withValues(alpha: 0.75));
  });

  testWidgets('일반 칸의 값은 흰색이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2'),
          HeroStat(label: '안 낸 숙제', value: '3', hot: true),
        ],
      ),
    );
    // 일반 칸(stat-0)의 두 번째 Text widget (값)
    final stat0Texts = find.descendant(
      of: find.byKey(const Key('stat-0')),
      matching: find.byType(Text),
    );
    final valueText = tester.widget<Text>(stat0Texts.at(1));
    final style = valueText.style!;
    expect(style.color, Colors.white);
  });

  testWidgets('sub 텍스트는 brand200 55% 이다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(label: '다음 수업', value: 'D-2', sub: '09/21 19:00'),
        ],
      ),
    );
    // sub 텍스트를 찾기 (세 번째 Text widget)
    final stat0Texts = find.descendant(
      of: find.byKey(const Key('stat-0')),
      matching: find.byType(Text),
    );
    final subText = tester.widget<Text>(stat0Texts.at(2));
    final style = subText.style!;
    expect(style.color, AppColors.brand200.withValues(alpha: 0.55));
  });

  testWidgets('extra 블록 위에 divider 가 있다', (tester) async {
    await tester.pumpWidget(
      host(
        stats: const [
          HeroStat(
            label: '다음 수업',
            value: 'D-2',
            extra: HeroStatExtra(label: '다음 클리닉', value: 'D-1'),
          ),
        ],
      ),
    );
    // stat-0 안의 Divider를 찾기
    final dividers = find.descendant(
      of: find.byKey(const Key('stat-0')),
      matching: find.byType(Divider),
    );
    expect(dividers, findsOneWidget);
    // divider 색 확인
    final divider = tester.widget<Divider>(dividers);
    expect(divider.color, AppColors.brand200.withValues(alpha: 0.25));
  });
}
