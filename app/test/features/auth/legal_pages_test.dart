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

  testWidgets('확정 안 된 값은 Pending 으로 남긴다', (tester) async {
    // 상호·연락처·보관 기간을 그럴듯하게 채우면 그것이 그대로 고지가 된다.
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    expect(find.byType(Pending), findsWidgets);
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

  testWidgets('Pending 마크가 눈에 띈다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    final pending = find.byType(Pending);
    expect(pending, findsWidgets);
    // 각 Pending은 [label] 형태로 화면에 표시된다
    expect(find.text('[확정 예정]'), findsWidgets);
    expect(find.text('[담당자·연락처 확정 예정]'), findsOneWidget);
  });

  testWidgets('360px 짧은 화면에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 850);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaterialApp(home: TermsPage()));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const MaterialApp(home: PrivacyPage()));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
