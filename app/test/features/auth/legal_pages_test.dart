import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/auth/legal_info.dart';
import 'package:academy_app/features/auth/privacy_page.dart';
import 'package:academy_app/features/auth/terms_page.dart';
import 'package:academy_app/shared/widgets/legal.dart';

void main() {
  // 2026-10-03 학원이 확정한 값. 예전에는 Pending 으로 남겨 두었다(빈칸이면 스토어 심사·법정 고지가 막힌다).
  testWidgets('처리방침에 학원이 확정한 값이 들어 있고 빈칸이 없다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.textContaining('확정 예정'), findsNothing);
    expect(find.textContaining('확인 필요'), findsNothing);
    expect(find.textContaining(legalOperator), findsWidgets);
    expect(find.textContaining('$legalOfficer · $legalPhone'), findsOneWidget);
    expect(find.textContaining('퇴원한 날로부터 12개월'), findsOneWidget);
    expect(find.textContaining('Amazon Web Services'), findsOneWidget);
    expect(find.textContaining('Supabase'), findsOneWidget);
    expect(find.textContaining('최대 4주)'), findsOneWidget);
  });

  testWidgets('처리방침과 약관이 계정 삭제 방법을 알린다(스토어 요구)', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.textContaining('「계정 삭제」'), findsOneWidget);
    expect(find.textContaining(accountDeletionUrl), findsOneWidget);
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    expect(find.textContaining('계정을 삭제할 수 있습니다'), findsOneWidget);
    expect(find.textContaining(legalPhone), findsOneWidget);
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
    expect(find.text('제8조 (문의처)'), findsOneWidget);
    expect(find.text('부칙'), findsOneWidget);
  });

  testWidgets('처방침의 절 제목들을 확인한다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.text('1. 수집하는 항목'), findsOneWidget);
    expect(find.text('2. 수집·이용 목적'), findsOneWidget);
    expect(find.text('3. 보관 기간'), findsOneWidget);
    expect(find.text('4. 제3자 제공과 처리 위탁'), findsOneWidget);
    expect(find.text('5. 개인정보의 국외 이전'), findsOneWidget);
    expect(find.text('6. 이용자의 권리'), findsOneWidget);
    expect(find.text('7. 안전성 확보 조치'), findsOneWidget);
    expect(find.text('8. 개인정보 보호책임자와 문의처'), findsOneWidget);
    expect(find.text('9. 시행일'), findsOneWidget);
  });

  testWidgets('국외 이전의 거부 방법이 가리키는 스위치가 앱에 있다', (tester) async {
    // 문구가 「내 정보에서 알림을 끄면」이다. 스위치를 지우면 고지가 거짓이 된다 —
    // 스위치는 PushSettingTile(학생 S-7·학부모 P-5)이고 smoke_test 가 화면에서 잡는다.
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(
      find.textContaining('「내 정보」에서 알림을 끄면', findRichText: true),
      findsOneWidget,
    );
  });

  testWidgets('로그인으로 돌아가기 링크가 있다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: const TermsPage(),
        routes: {'/login': (_) => const Scaffold()},
      ),
    );
    expect(find.text('로그인으로 돌아가기'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        home: const PrivacyPage(),
        routes: {'/login': (_) => const Scaffold()},
      ),
    );
    expect(find.text('로그인으로 돌아가기'), findsOneWidget);
  });

  testWidgets('360px 폭에서 콘텐츠가 넘치지 않는다', (tester) async {
    // 페이지가 SingleChildScrollView 에 싸여 있어서 세로 넘침은 테스트할 수 없다.
    // 높이 1000은 테스트 실행 중 일정한 상태를 유지하기 위한 값이고 실측한
    // 값은 아니다 — 이 테스트가 잡는 건 가로 넘침이다: Row나 unbreakable
    // Text 같은 고정폭 위젯이 있으면 360px 너비에서 RenderFlex 오류가 난다.
    tester.view.physicalSize = const Size(360, 1000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    // TermsPage 테스트
    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
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
