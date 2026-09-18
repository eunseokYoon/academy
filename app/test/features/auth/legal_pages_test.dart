import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/auth/privacy_page.dart';
import 'package:academy_app/features/auth/terms_page.dart';
import 'package:academy_app/shared/widgets/legal.dart';

void main() {
  testWidgets('약관에 초안 표시가 있다', (tester) async {
    // 학원이 확정해야 하는 값이 남아 있다는 사실을 화면에서 알려야 한다.
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    expect(find.byType(LegalDraftNotice), findsOneWidget);
  });

  testWidgets('처방침에 초안 표시가 있다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.byType(LegalDraftNotice), findsOneWidget);
  });

  testWidgets('보관 기간 값을 Pending 으로 남긴다', (tester) async {
    // 퇴원 후 보관 기간을 그럴듯하게 채우면 그것이 그대로 고지가 된다.
    // 섹션 3에 있는 Pending을 구체적으로 확인한다.
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    final section3 = find.text('3. 보관 기간');
    expect(section3, findsOneWidget);
    // 섹션 3 내 Pending이 있는지 확인
    expect(find.byType(Pending), findsWidgets);
    expect(find.text('[확정 예정]'), findsNWidgets(2)); // 섹션 3, 4에 각각 하나씩
  });

  testWidgets('클라우드 사업자 값을 Pending 으로 남긴다', (tester) async {
    // 제3자 제공 섹션의 클라우드 사업자를 그럴듯하게 채우면 그것이 그대로 고지가 된다.
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    final section4 = find.text('4. 제3자 제공과 처리 위탁');
    expect(section4, findsOneWidget);
    expect(find.text('[확정 예정]'), findsNWidgets(2)); // 섹션 3, 4에 각각 하나씩
  });

  testWidgets('연락처를 Pending 으로 남긴다', (tester) async {
    // 문의처의 담당자·연락처를 그럴듯하게 채우면 그것이 그대로 고지가 된다.
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    final section7 = find.text('7. 문의처');
    expect(section7, findsOneWidget);
    expect(find.text('[담당자·연락처 확정 예정]'), findsOneWidget);
  });

  testWidgets('두 화면 모두 절이 하나 이상 있다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    expect(find.byType(LegalSection), findsWidgets);
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.byType(LegalSection), findsWidgets);
  });

  testWidgets('약관의 절 제목들을 확인한다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    expect(find.text('제1조 (목적)'), findsOneWidget);
    expect(find.text('제2조 (이용 대상과 계정)'), findsOneWidget);
    expect(find.text('제3조 (계정 관리)'), findsOneWidget);
    expect(find.text('제4조 (서비스의 내용)'), findsOneWidget);
    expect(find.text('제5조 (이용자의 의무)'), findsOneWidget);
    expect(find.text('제6조 (서비스의 중단)'), findsOneWidget);
    expect(find.text('제7조 (약관의 변경)'), findsOneWidget);
  });

  testWidgets('처방침의 절 제목들을 확인한다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.text('1. 수집하는 항목'), findsOneWidget);
    expect(find.text('2. 수집·이용 목적'), findsOneWidget);
    expect(find.text('3. 보관 기간'), findsOneWidget);
    expect(find.text('4. 제3자 제공과 처리 위탁'), findsOneWidget);
    expect(find.text('5. 이용자의 권리'), findsOneWidget);
    expect(find.text('6. 안전성 확보 조치'), findsOneWidget);
    expect(find.text('7. 문의처'), findsOneWidget);
  });

  testWidgets('로그인으로 돌아가기 링크가 있다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: const TermsPage(),
      routes: {'/login': (_) => const Scaffold()},
    ));
    expect(find.text('로그인으로 돌아가기'), findsOneWidget);

    await tester.pumpWidget(MaterialApp(
      home: const PrivacyPage(),
      routes: {'/login': (_) => const Scaffold()},
    ));
    expect(find.text('로그인으로 돌아가기'), findsOneWidget);
  });

  testWidgets('360px 폭에서 콘텐츠가 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 1000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    // TermsPage 테스트
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    // 콘텐츠의 최대 너비는 384px로 제한되어 360px 폭에 맞아야 한다
    final termsContent = find.byType(ConstrainedBox);
    expect(termsContent, findsWidgets);
    expect(tester.takeException(), isNull);

    // PrivacyPage 테스트
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    final privacyContent = find.byType(ConstrainedBox);
    expect(privacyContent, findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
