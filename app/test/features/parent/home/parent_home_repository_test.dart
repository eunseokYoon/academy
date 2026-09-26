import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/features/parent/home/parent_home_repository.dart';

Map<String, dynamic> get _full => {
  'success': true,
  'data': {
    'student': {'studentId': 1, 'name': '김하늘', 'phone': '01012345678'},
    'nextExam': {
      'examType': 'MIDTERM',
      'startDate': '2026-10-01',
      'scopeNote': null,
      'dDay': 12,
    },
    'nextLessonDate': '2026-09-21',
    'nextLessonTime': '19:00',
    'nextLessonDDay': 2,
    'notices': {'totalCount': 3, 'recent': []},
    'pendingHomeworkCount': 2,
    'nextClinic': {
      'clinicId': 7,
      'clinicDate': '2026-09-20',
      'arrivalTime': '17:00',
      'dDay': 1,
    },
    'thisMonthAttendance': {
      'present': 6,
      'late': 1,
      'absent': 1,
      'sick': 0,
      'excused': 0,
      'makeup': 2,
    },
  },
  'error': null,
};

void main() {
  ParentHomeRepository repoWith(FakeAdapter adapter) {
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = adapter;
    return ParentHomeRepository(dio);
  }

  test('선택된 자녀의 홈을 한 번에 받는다', () async {
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: _full)],
    );
    final home = await repoWith(adapter).fetch(1);
    expect(adapter.received.single.path, '/api/parent/children/1/home');
    expect(home.studentName, '김하늘');
    expect(home.nextLessonDDay, 2);
    expect(home.pendingHomeworkCount, 2);
    expect(home.notices.totalCount, 3);
  });

  test('자녀 id 가 경로에 들어간다', () async {
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: _full)],
    );
    await repoWith(adapter).fetch(42);
    expect(adapter.received.single.path, '/api/parent/children/42/home');
  });

  test('없는 값은 null 로 남는다', () async {
    final partial = Map<String, dynamic>.from(_full);
    partial['data'] =
        Map<String, dynamic>.from(_full['data'] as Map<String, dynamic>)
          ..['nextExam'] = null
          ..['nextClinic'] = null
          ..['nextLessonDate'] = null
          ..['nextLessonTime'] = null
          ..['nextLessonDDay'] = null;
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: partial)],
    );
    final home = await repoWith(adapter).fetch(1);
    expect(home.nextExam, isNull);
    expect(home.nextClinic, isNull);
    expect(home.nextLessonDate, isNull);
    expect(home.nextLessonDDay, isNull);
  });

  test('수업 시각이 없으면 null 이다 — 지어내지 않는다', () async {
    // nextLessonTime 은 반의 요일 슬롯에서 온다. 없으면 날짜만 그린다.
    final noTime = Map<String, dynamic>.from(_full);
    noTime['data'] = Map<String, dynamic>.from(
      _full['data'] as Map<String, dynamic>,
    )..['nextLessonTime'] = null;
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: noTime)],
    );
    final home = await repoWith(adapter).fetch(1);
    expect(home.nextLessonTime, isNull);
    expect(home.nextLessonDate, '2026-09-21');
  });

  test('출석은 present + makeup 이다', () async {
    // MAKEUP(대체 등원)은 출석이다. 결석 쪽으로 세지 마라.
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: _full)],
    );
    final home = await repoWith(adapter).fetch(1);
    expect(home.thisMonthAttendance.present, 6);
    expect(home.thisMonthAttendance.makeup, 2);
    expect(home.thisMonthAttendance.attended, 8);
  });
}
