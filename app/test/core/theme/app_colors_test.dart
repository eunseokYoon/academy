import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';

void main() {
  // 값의 정본은 frontend/tailwind.config.js 다. 여기 숫자를 고치려면 거기도 고쳐라.
  test('색 토큰이 웹 팔레트와 같다', () {
    expect(AppColors.brand900.toARGB32(), 0xFF1B2A44);
    expect(AppColors.brand600.toARGB32(), 0xFF1E5AA8);
    expect(AppColors.brand50.toARGB32(), 0xFFEFF4FC);
    expect(AppColors.accent500.toARGB32(), 0xFFD9542B);
    expect(AppColors.paper.toARGB32(), 0xFFEDF1F7);
  });
}
