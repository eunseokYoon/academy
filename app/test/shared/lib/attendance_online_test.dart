import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/lib/attendance.dart';

void main() {
  test('ONLINE 을 읽고 「온라인」 보라 칩이다(2026-09-29)', () {
    expect(DayStatus.parse('ONLINE'), DayStatus.online);
    expect(DayStatus.online.label, '온라인');
    expect(dayStatusStyle(DayStatus.online).background, AppColors.violet100);
  });

  test('요약의 「출석」은 출석 + 대체 등원 + 온라인이다. 옛 서버(online 없음)는 0 이다', () {
    final s = AttendanceSummary.fromJson({
      'present': 3,
      'makeup': 1,
      'online': 2,
      'absent': 1,
    });
    expect(s.attended, 6);
    expect(AttendanceSummary.fromJson({'present': 3}).attended, 3);
  });
}
