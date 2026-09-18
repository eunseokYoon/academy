import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/app_theme.dart';
import 'package:academy_app/shared/widgets/form_error.dart';

void main() {
  testWidgets('message 가 null 이면 아무것도 그리지 않는다', (tester) async {
    // 오류가 없을 때 빈 상자가 자리를 차지하면 폼이 들썩인다.
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: FormError(message: null))),
    );
    expect(find.byType(Text), findsNothing);
  });

  testWidgets('서버 문구를 그대로 보여준다', (tester) async {
    // 백엔드 ErrorCode 의 message 가 이미 한국어 사용자 문구다.
    // 앱에서 문구를 다시 만들거나 감싸지 마라.
    const message = '아이디 또는 비밀번호가 올바르지 않습니다.';
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: FormError(message: message)),
      ),
    );
    expect(find.text(message), findsOneWidget);

    // 스타일을 확인한다 — 서버 메시지가 신뢰할 수 있으려면 매번 같아야 한다.
    final textWidget = tester.widget<Text>(find.byType(Text));
    expect(textWidget.style?.fontSize, 14);
    expect(textWidget.style?.color?.toARGB32(), AppColors.red700.toARGB32());

    // 배경과 테두리를 확인한다.
    final containerWidget = tester.widget<Container>(find.byType(Container));
    final decoration = containerWidget.decoration! as BoxDecoration;
    expect(decoration.color?.toARGB32(), AppColors.red50.toARGB32());
    expect(decoration.borderRadius, BorderRadius.circular(AppRadii.xl));
    expect(
      decoration.border?.top.color.toARGB32(),
      AppColors.red200.toARGB32(),
    );
    expect(
      containerWidget.padding,
      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    );
  });
}
