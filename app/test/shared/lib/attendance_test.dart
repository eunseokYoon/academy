import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/lib/attendance.dart';

void main() {
  test('확정 전(null)과 모르는 값은 PENDING 이다', () {
    expect(DayStatus.parse(null), DayStatus.pending);
    expect(DayStatus.parse('SOMETHING_NEW'), DayStatus.pending);
    expect(DayStatus.parse('MAKEUP'), DayStatus.makeup);
    expect(DayStatus.parse('ABSENT'), DayStatus.absent);
  });

  test('요약의 출석은 present + makeup 이다 (5-1)', () {
    final s = AttendanceSummary.fromJson({
      'present': 3,
      'late': 1,
      'absent': 0,
      'sick': 0,
      'excused': 0,
      'makeup': 2,
    });
    expect(s.attended, 5);
  });

  test('옛 응답에 makeup 이 없어도 0 으로 받는다', () {
    // 배포 순서가 backend → 앱이어도 옛 서버를 만날 수 있다(7-3).
    final s = AttendanceSummary.fromJson({
      'present': 3,
      'late': 0,
      'absent': 0,
      'sick': 0,
      'excused': 0,
    });
    expect(s.attended, 3);
  });

  test('숙제 완료율이 null 이면 null 로 남는다 — 0 으로 채우지 않는다', () {
    final m = AttendanceMonth.fromJson({
      'year': 2026,
      'month': 9,
      'summary': <String, dynamic>{},
      'homeworkCompletionRate': null,
    });
    expect(m.homeworkCompletionRate, isNull);
    expect(m.days, isEmpty);
  });

  test('달을 넘긴다 — 해가 바뀌는 곳 포함', () {
    expect(shiftMonth((year: 2026, month: 12), 1), (year: 2027, month: 1));
    expect(shiftMonth((year: 2026, month: 1), -1), (year: 2025, month: 12));
    expect(shiftMonth((year: 2026, month: 9), 1), (year: 2026, month: 10));
  });

  test('달의 첫날·마지막 날', () {
    expect(monthRange((year: 2026, month: 9)), ('2026-09-01', '2026-09-30'));
    expect(monthRange((year: 2028, month: 2)), ('2028-02-01', '2028-02-29'));
    expect(monthRange((year: 2026, month: 12)), ('2026-12-01', '2026-12-31'));
  });
}
