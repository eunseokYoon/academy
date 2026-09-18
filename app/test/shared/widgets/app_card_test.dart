import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_theme.dart';
import 'package:academy_app/shared/widgets/app_card.dart';

void main() {
  testWidgets('카드가 두 겹 남색 그림자를 쓴다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: AppCard(child: Text('내용'))),
      ),
    );

    final box = tester.widget<Container>(find.byType(Container));
    final d = box.decoration! as BoxDecoration;
    // Material elevation 이 아니라 테마의 두 겹 그림자를 그대로 쓴다.
    expect(d.boxShadow, AppShadows.card);
    expect(d.borderRadius, BorderRadius.circular(AppRadii.xxl));
    expect(find.text('내용'), findsOneWidget);
  });
}
