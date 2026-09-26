import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/lib/homework_labels.dart';
import 'package:academy_app/shared/widgets/app_badge.dart';

void main() {
  test('남은 시간은 웹 remainingLabel 과 경계가 같다', () {
    expect(remainingLabel(0), '0분 남음');
    expect(remainingLabel(59), '59분 남음');
    expect(remainingLabel(60), '1시간 남음');
    expect(remainingLabel(60 * 24 - 1), '23시간 남음');
    expect(remainingLabel(60 * 24), '1일 남음');
    expect(remainingLabel(-1), '마감 1분 지남');
    expect(remainingLabel(-60), '마감 1시간 지남');
    expect(remainingLabel(-60 * 24 * 3), '마감 3일 지남');
  });

  test('마감 시각은 기기 시간대가 아니라 KST 다', () {
    // UTC 12:30 = KST 21:30. `.toLocal()` 이면 러너 시간대에 따라 갈라진다.
    expect(formatDueAt('2026-09-21T12:30:00Z'), '9월 21일 21:30');
    // UTC 로 온 값이 KST 로 날짜를 넘긴다.
    expect(formatDueAt('2026-09-21T16:00:00Z'), '9월 22일 01:00');
    expect(formatDueAt('2026-09-21T21:00:00+09:00'), '9월 21일 21:00');
  });

  test('result 가 null 이면 「미채점」이지 0% 가 아니다 (4-2)', () {
    expect(gradeLabel(null, null, false), '미채점');
    expect(gradeTone(null), BadgeTone.neutral);
    expect(gradeLabel('DONE', null, false), '완료');
    expect(gradeLabel('DONE', null, true), '완료 (재제출)');
    expect(gradeLabel('PARTIAL', 40, false), '일부 40%');
    expect(gradeLabel('NOT_DONE', null, false), '미완료');
    expect(gradeTone('DONE'), BadgeTone.ok);
    expect(gradeTone('PARTIAL'), BadgeTone.warn);
    expect(gradeTone('NOT_DONE'), BadgeTone.danger);
  });

  test('제출축 글자', () {
    expect(submissionLabel('SUBMITTED'), '제출 완료');
    expect(submissionLabel('NOT_SUBMITTED'), '미제출');
  });

  test('수업일 라벨은 해가 다르면 연도를 붙인다', () {
    expect(lessonDayLabel('2026-08-03', thisYear: 2026), '8월 3일 수업');
    expect(lessonDayLabel('2025-12-30', thisYear: 2026), '2025년 12월 30일 수업');
    expect(lessonDayShort('2026-08-03'), '8/3');
  });

  test('수업일로 묶으면 최근이 위, 수업일 없는 것은 맨 아래 한 덩어리다', () {
    final items = [
      ('a', '2026-08-03'),
      ('b', null),
      ('c', '2026-08-10'),
      ('d', '2026-08-03'),
      ('e', null),
    ];
    final groups = groupByLessonDay(items, (i) => i.$2, thisYear: 2026);
    expect(groups.map((g) => g.lessonDate).toList(), [
      '2026-08-10',
      '2026-08-03',
      null,
    ]);
    expect(groups[1].items.map((i) => i.$1).toList(), ['a', 'd']);
    expect(groups.last.label, '수업일 없음');
    expect(groups.last.items.map((i) => i.$1).toList(), ['b', 'e']);
  });

  test('수업일 없는 것이 없으면 그 묶음도 없다', () {
    final groups = groupByLessonDay([('a', '2026-08-03')], (i) => i.$2);
    expect(groups, hasLength(1));
  });
}
