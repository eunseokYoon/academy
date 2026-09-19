import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path_drawing/path_drawing.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/icons/app_icon.dart';
import 'package:academy_app/shared/icons/icon_paths.dart';

void main() {
  test('13종이 모두 있고 전부 파싱된다', () {
    // 웹 Icon.tsx 의 PATHS 키와 같아야 한다. 빠지면 레일·탭 바가 그릴 수 없다.
    expect(AppIconName.values.length, 13);
    for (final name in AppIconName.values) {
      final d = kIconPaths[name];
      expect(d, isNotNull, reason: '$name 의 패스가 없다');
      // 파싱이 던지면 화면에서 통째로 안 그려진다. 여기서 잡는다.
      final path = parseSvgPathData(d!);
      final b = path.getBounds();
      // 웹 viewBox 가 0 0 24 24 다. 벗어나면 옮겨 적다 틀린 것이다.
      expect(b.left, greaterThanOrEqualTo(-1));
      expect(b.top, greaterThanOrEqualTo(-1));
      expect(b.right, lessThanOrEqualTo(25));
      expect(b.bottom, lessThanOrEqualTo(25));
    }
  });

  testWidgets('색과 굵기를 받은 대로 그린다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AppIcon(
            AppIconName.home,
            color: AppColors.accent500,
            strokeWidth: 2.2,
          ),
        ),
      ),
    );
    final painter = tester.widget<CustomPaint>(
      find.descendant(
        of: find.byType(AppIcon),
        matching: find.byType(CustomPaint),
      ),
    );
    final p = painter.painter! as IconPainter;
    expect(p.color.toARGB32(), AppColors.accent500.toARGB32());
    expect(p.strokeWidth, 2.2);
    expect(p.name, AppIconName.home);
  });

  testWidgets('기본 굵기는 웹과 같은 1.7 이다', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: AppIcon(AppIconName.chart, color: AppColors.slate400),
        ),
      ),
    );
    final painter = tester.widget<CustomPaint>(
      find.descendant(
        of: find.byType(AppIcon),
        matching: find.byType(CustomPaint),
      ),
    );
    expect((painter.painter! as IconPainter).strokeWidth, 1.7);
  });
}
