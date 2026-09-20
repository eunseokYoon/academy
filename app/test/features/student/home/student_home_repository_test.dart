import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/features/student/home/student_home_repository.dart';

Map<String, dynamic> get _full => {
  'success': true,
  'data': {
    'student': {'name': '김하늘'},
    'nextLesson': {
      'lessonDate': '2026-09-21',
      'startTime': '19:00',
      'dDay': 2,
      'classRoomName': 'A고 2학년 목요일반',
    },
    'nextExam': {
      'examType': 'MIDTERM',
      'startDate': '2026-10-01',
      'scopeNote': '3과~5과',
      'dDay': 12,
    },
    'nextClinic': {
      'clinicId': 7,
      'clinicDate': '2026-09-20',
      'arrivalTime': '17:00',
      'dDay': 1,
    },
    'currentHomeworks': [
      {
        'homeworkId': 11,
        'title': '단어 3과',
        'dueAt': '2026-09-21T21:00:00+09:00',
        'status': 'NOT_SUBMITTED',
        'remainingMinutes': 1500,
      },
    ],
    'lastLesson': {
      'lessonId': 5,
      'lessonDate': '2026-09-14',
      'title': '5과 정리',
      'videoId': 'abc123',
      'embedUrl': 'https://www.youtube.com/embed/abc123',
      'videoCount': 2,
      'content': '본문 해석',
      'homeworkNote': '단어 3과 외워오기',
    },
    'notices': {
      'totalCount': 7,
      'recent': [
        {
          'noticeId': 3,
          'title': '추석 휴원 안내',
          'pinned': true,
          'hasAttachment': false,
          'publishedAt': '2026-09-15T10:00:00+09:00',
        },
      ],
    },
  },
  'error': null,
};

void main() {
  StudentHomeRepository repoWith(FakeAdapter adapter) {
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = adapter;
    return StudentHomeRepository(dio);
  }

  test('한 번의 호출로 홈 전체를 받는다', () async {
    // 백엔드가 여러 도메인을 조합해 단일 API 로 내려준다. 추가 호출을 붙이지 마라.
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: _full)],
    );
    final home = await repoWith(adapter).fetch();

    expect(adapter.received.single.path, '/api/student/home');
    expect(home.studentName, '김하늘');
    expect(home.nextLesson!.dDay, 2);
    expect(home.nextLesson!.classRoomName, 'A고 2학년 목요일반');
    expect(home.nextExam!.dDay, 12);
    expect(home.nextExam!.scopeNote, '3과~5과');
    expect(home.nextClinic!.arrivalTime, '17:00');
    expect(home.nextClinic!.clinicId, 7);
    expect(home.nextClinic!.clinicDate, '2026-09-20');
    expect(home.nextClinic!.dDay, 1);
    expect(home.currentHomeworks.single.title, '단어 3과');
    expect(home.currentHomeworks.single.dueAt, '2026-09-21T21:00:00+09:00');
    expect(home.currentHomeworks.single.status, 'NOT_SUBMITTED');
    expect(home.currentHomeworks.single.remainingMinutes, 1500);
    expect(home.lastLesson!.videoCount, 2);
    expect(home.lastLesson!.videoId, 'abc123');
    expect(home.lastLesson!.embedUrl, 'https://www.youtube.com/embed/abc123');
    expect(home.notices.totalCount, 7);
    expect(home.notices.recent.single.title, '추석 휴원 안내');
    // pinned는 NoticeCard(Task 10)의 고정 배지가 그대로 쓰는 값이다 — 파싱을
    // 빠뜨리면 배지가 항상 안 뜬다.
    expect(home.notices.recent.single.pinned, isTrue);
    expect(home.notices.recent.single.hasAttachment, isFalse);
  });

  test('없는 값은 null 로 남는다 — 기본값을 채우지 않는다', () async {
    // nextExam 이 null 인데 D-0 을 채우면 시험이 오늘로 읽힌다.
    final partial = Map<String, dynamic>.from(_full);
    partial['data'] =
        Map<String, dynamic>.from(_full['data'] as Map<String, dynamic>)
          ..['nextLesson'] = null
          ..['nextExam'] = null
          ..['nextClinic'] = null
          ..['lastLesson'] = null
          ..['currentHomeworks'] = <dynamic>[];
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: partial)],
    );
    final home = await repoWith(adapter).fetch();

    expect(home.nextLesson, isNull);
    expect(home.nextExam, isNull);
    expect(home.nextClinic, isNull);
    expect(home.lastLesson, isNull);
    expect(home.currentHomeworks, isEmpty);
    // 이름과 공지는 항상 온다.
    expect(home.studentName, '김하늘');
    expect(home.notices.totalCount, 7);
  });

  test('lastLesson 이 있는데 videoId 와 embedUrl 이 null 이면 그대로 둔다', () async {
    // 수업 영상이 등록되지 않은 경우. 화면이 비디오 블록을 전혀 그리지 않아야 한다.
    final noVideo = Map<String, dynamic>.from(_full);
    final data = Map<String, dynamic>.from(
      _full['data'] as Map<String, dynamic>,
    );
    data['lastLesson'] = {
      'lessonId': 5,
      'lessonDate': '2026-09-14',
      'title': '5과 정리',
      'videoId': null,
      'embedUrl': null,
      'videoCount': 0,
      'content': '본문 해석',
      'homeworkNote': '단어 3과 외워오기',
    };
    noVideo['data'] = data;
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: noVideo)],
    );
    final home = await repoWith(adapter).fetch();

    expect(home.lastLesson, isNotNull);
    expect(home.lastLesson!.lessonId, 5);
    expect(home.lastLesson!.title, '5과 정리');
    expect(home.lastLesson!.content, '본문 해석');
    expect(home.lastLesson!.homeworkNote, '단어 3과 외워오기');
    // 비디오 필드는 null 로 남는다.
    expect(home.lastLesson!.videoId, isNull);
    expect(home.lastLesson!.embedUrl, isNull);
  });

  test('startTime 이 없으면 null 이다 — 시각을 지어내지 않는다', () async {
    final noTime = Map<String, dynamic>.from(_full);
    final data = Map<String, dynamic>.from(
      _full['data'] as Map<String, dynamic>,
    );
    data['nextLesson'] = {
      'lessonDate': '2026-09-21',
      'startTime': null,
      'dDay': 2,
      'classRoomName': 'A반',
    };
    noTime['data'] = data;
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: noTime)],
    );
    final home = await repoWith(adapter).fetch();

    expect(home.nextLesson!.startTime, isNull);
    expect(home.nextLesson!.lessonDate, '2026-09-21');
  });

  test('scopeNote 이 없으면 null 이다 — 시험 범위를 지어내지 않는다', () async {
    final noScope = Map<String, dynamic>.from(_full);
    final data = Map<String, dynamic>.from(
      _full['data'] as Map<String, dynamic>,
    );
    data['nextExam'] = {
      'examType': 'MIDTERM',
      'startDate': '2026-10-01',
      'dDay': 12,
      // scopeNote 는 생략
    };
    noScope['data'] = data;
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: noScope)],
    );
    final home = await repoWith(adapter).fetch();

    expect(home.nextExam!.scopeNote, isNull);
    expect(home.nextExam!.examType, 'MIDTERM');
  });

  test('날짜는 서버 문자열 그대로다 — 파싱해서 다시 만들지 않는다', () async {
    final adapter = FakeAdapter(
      replies: [FakeReply(statusCode: 200, body: _full)],
    );
    final home = await repoWith(adapter).fetch();

    expect(home.nextLesson!.lessonDate, '2026-09-21');
    expect(home.notices.recent.single.publishedAt, '2026-09-15T10:00:00+09:00');
  });
}
