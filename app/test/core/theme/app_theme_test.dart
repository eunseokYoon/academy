import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/app_theme.dart';

void main() {
  test('반경은 웹의 rounded-lg·xl·2xl 셋뿐이다', () {
    // 웹이 쓰는 값만 둔다. 화면에서 숫자를 발명하지 못하게 하는 것이 목적이다.
    expect(AppRadii.lg, 8);
    expect(AppRadii.xl, 12);
    expect(AppRadii.xxl, 16);
  });

  test('shadow-card 는 두 겹이고 남색이 섞여 있다', () {
    // Material elevation 은 한 겹 회색이라 이 배경과 따로 논다.
    // 웹 tailwind.config.js 의 boxShadow.card 를 그대로 옮긴 값이다.
    expect(AppShadows.card.length, 2);
    expect(AppShadows.card[0].offset, const Offset(0, 1));
    expect(AppShadows.card[0].blurRadius, 2);
    expect(AppShadows.card[1].offset, const Offset(0, 6));
    expect(AppShadows.card[1].blurRadius, 16);
    expect(AppShadows.card[1].spreadRadius, -6);
    // 회색이 아니라 남색이다 — rgba(20,41,77,…)
    for (final s in AppShadows.card) {
      expect(s.color.r, lessThan(s.color.b), reason: '남색이어야 한다');
    }
  });

  test('테마가 Pretendard 와 paper 배경을 유지한다', () {
    final t = AppTheme.light();
    expect(t.textTheme.bodyMedium?.fontFamily, 'Pretendard');
    expect(t.scaffoldBackgroundColor, AppColors.paper);
  });
}
