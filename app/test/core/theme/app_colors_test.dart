import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/core/theme/chart_colors.dart';

void main() {
  // 값의 정본은 frontend/tailwind.config.js 다. 여기 숫자를 고치려면 거기도 고쳐라.
  test('색 토큰이 웹 팔레트와 같다', () {
    expect(AppColors.brand900.toARGB32(), 0xFF1B2A44);
    expect(AppColors.brand600.toARGB32(), 0xFF1E5AA8);
    expect(AppColors.brand50.toARGB32(), 0xFFEFF4FC);
    expect(AppColors.brand400.toARGB32(), 0xFF5688D2);
    expect(AppColors.accent500.toARGB32(), 0xFFD9542B);
    expect(AppColors.accent600.toARGB32(), 0xFFBF4522);
    expect(AppColors.accent700.toARGB32(), 0xFF98371C);
    expect(AppColors.paper.toARGB32(), 0xFFEDF1F7);
  });

  // 값의 정본은 tailwind 기본 팔레트다(frontend/node_modules/tailwindcss/colors).
  // 웹이 쓰는 음영만 옮긴다.
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
    expect(AppColors.amber200.toARGB32(), 0xFFFDE68A);
    expect(AppColors.amber700.toARGB32(), 0xFFB45309);
    expect(AppColors.emerald50.toARGB32(), 0xFFECFDF5);
    expect(AppColors.emerald200.toARGB32(), 0xFFA7F3D0);
    expect(AppColors.emerald700.toARGB32(), 0xFF047857);
    expect(AppColors.emerald100.toARGB32(), 0xFFD1FAE5);
    expect(AppColors.emerald600.toARGB32(), 0xFF059669);
    expect(AppColors.emerald800.toARGB32(), 0xFF065F46);
    expect(AppColors.red100.toARGB32(), 0xFFFEE2E2);
    expect(AppColors.red400.toARGB32(), 0xFFF87171);
    expect(AppColors.red600.toARGB32(), 0xFFDC2626);
    expect(AppColors.red800.toARGB32(), 0xFF991B1B);
    expect(AppColors.amber100.toARGB32(), 0xFFFEF3C7);
    expect(AppColors.amber600.toARGB32(), 0xFFD97706);
    expect(AppColors.sky100.toARGB32(), 0xFFE0F2FE);
    expect(AppColors.sky200.toARGB32(), 0xFFBAE6FD);
    expect(AppColors.sky600.toARGB32(), 0xFF0284C7);
    expect(AppColors.sky800.toARGB32(), 0xFF075985);
    expect(AppColors.teal100.toARGB32(), 0xFFCCFBF1);
    expect(AppColors.teal200.toARGB32(), 0xFF99F6E4);
    expect(AppColors.teal800.toARGB32(), 0xFF115E59);
    expect(AppColors.blue100.toARGB32(), 0xFFDBEAFE);
    expect(AppColors.blue700.toARGB32(), 0xFF1D4ED8);
  });

  test('성적 막대 색이 웹 CorrectCountChart 상수와 같다', () {
    expect(ChartColors.internal.toARGB32(), 0xFF7A2E3A);
    expect(ChartColors.external.toARGB32(), 0xFF1B2A44);
    expect(ChartColors.marked.toARGB32(), 0xFFD9542B);
  });
}
