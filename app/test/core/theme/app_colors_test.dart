import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';

void main() {
  // 값의 정본은 frontend/tailwind.config.js 다. 여기 숫자를 고치려면 거기도 고쳐라.
  test('색 토큰이 웹 팔레트와 같다', () {
    expect(AppColors.brand900.toARGB32(), 0xFF1B2A44);
    expect(AppColors.brand600.toARGB32(), 0xFF1E5AA8);
    expect(AppColors.brand50.toARGB32(), 0xFFEFF4FC);
    expect(AppColors.accent500.toARGB32(), 0xFFD9542B);
    expect(AppColors.accent600.toARGB32(), 0xFFBF4522);
    expect(AppColors.accent700.toARGB32(), 0xFF98371C);
    expect(AppColors.paper.toARGB32(), 0xFFEDF1F7);
  });

  // 값의 정본은 tailwind 기본 팔레트다(frontend/node_modules/tailwindcss/colors).
  // 웹이 인증 화면에서 쓰는 음영만 옮긴다 — emerald·sky 는 쓰지 않으므로 넣지 않는다.
  test('상태색이 tailwind 기본 팔레트와 같다', () {
    expect(AppColors.slate50.toARGB32(), 0xFFF8FAFC);
    expect(AppColors.slate100.toARGB32(), 0xFFF1F5F9);
    expect(AppColors.slate200.toARGB32(), 0xFFE2E8F0);
    expect(AppColors.slate300.toARGB32(), 0xFFCBD5E1);
    expect(AppColors.slate400.toARGB32(), 0xFF94A3B8);
    expect(AppColors.slate500.toARGB32(), 0xFF64748B);
    expect(AppColors.slate600.toARGB32(), 0xFF475569);
    expect(AppColors.slate700.toARGB32(), 0xFF334155);
    expect(AppColors.slate900.toARGB32(), 0xFF0F172A);
    expect(AppColors.red50.toARGB32(), 0xFFFEF2F2);
    expect(AppColors.red200.toARGB32(), 0xFFFECACA);
    expect(AppColors.red700.toARGB32(), 0xFFB91C1C);
    expect(AppColors.amber50.toARGB32(), 0xFFFFFBEB);
    expect(AppColors.amber800.toARGB32(), 0xFF92400E);
    expect(AppColors.amber900.toARGB32(), 0xFF78350F);
  });
}
