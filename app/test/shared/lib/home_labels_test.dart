import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/lib/home_labels.dart';

void main() {
  // 학생·학부모 두 홈이 같이 쓰는 기준점이다. 요일 인덱스가 한 칸
  // 밀리면(예: `weekday % 7`) 두 화면이 함께 틀린다.
  test('월요일과 일요일을 맞게 적는다', () {
    expect(todayLabel(now: DateTime(2026, 9, 21)), '2026년 9월 21일 월요일');
    expect(todayLabel(now: DateTime(2026, 9, 27)), '2026년 9월 27일 일요일');
  });
}
