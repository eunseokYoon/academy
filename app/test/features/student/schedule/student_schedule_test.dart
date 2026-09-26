import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/features/student/schedule/student_schedule_data.dart';
import 'package:academy_app/features/student/schedule/student_schedule_page.dart';

StudentClinic _clinic(
  int id, {
  String date = '2026-09-29',
  List<String> slots = const ['17:00', '18:00', '19:00'],
  bool full = false,
  String? mine,
}) => StudentClinic(
  clinicId: id,
  clinicDate: date,
  startTime: '17:00',
  endTime: '20:00',
  slots: slots,
  full: full,
  myReservation: mine == null ? null : MyClinicReservation(arrivalTime: mine),
);

const _mon = LessonSlot(
  lessonId: 1,
  classRoomName: 'A반',
  lessonDate: '2026-09-28',
  startTime: '19:00:00',
);
const _wed = LessonSlot(
  lessonId: 2,
  classRoomName: 'B반',
  lessonDate: '2026-09-30',
  startTime: null,
);

class _Repo implements StudentScheduleRepository {
  _Repo({
    required this.clinicList,
    this.requests = const [],
    this.changeError,
    this.requestError,
    this.myLessons = const [_mon],
  });

  final List<LessonSlot> myLessons;

  List<StudentClinic> clinicList;
  List<LessonChangeRequest> requests;
  Object? changeError;
  Object? requestError;
  final List<(String, String)> clinicCalls = [];
  final List<Map<String, Object?>> changes = [];
  final List<Map<String, Object?>> lessonRequests = [];

  /// 후보 요청마다 끝낼 시점을 테스트가 정한다.
  final Map<int, Completer<List<LessonSlot>>> candidateCalls = {};

  @override
  Future<List<StudentClinic>> clinics(String from, String to) async {
    clinicCalls.add((from, to));
    return clinicList;
  }

  @override
  Future<List<LessonChangeRequest>> lessonChanges() async => requests;

  @override
  Future<void> changeClinic(
    int clinicId, {
    required int? targetClinicId,
    required String arrivalTime,
    required String reason,
  }) async {
    final e = changeError;
    if (e != null) throw e;
    changes.add({
      'clinicId': clinicId,
      'targetClinicId': targetClinicId,
      'arrivalTime': arrivalTime,
      'reason': reason,
    });
  }

  @override
  Future<List<LessonSlot>> changeableLessons() async => myLessons;

  @override
  Future<List<LessonSlot>> changeCandidates(int fromLessonId) {
    final c = Completer<List<LessonSlot>>();
    candidateCalls[fromLessonId] = c;
    return c.future;
  }

  @override
  Future<void> requestLessonChange({
    required int fromLessonId,
    required int toLessonId,
    required String reason,
  }) async {
    final e = requestError;
    if (e != null) throw e;
    lessonRequests.add({
      'from': fromLessonId,
      'to': toLessonId,
      'reason': reason,
    });
  }
}

Future<StudentScheduleController> _pump(
  WidgetTester tester,
  _Repo repo, {
  VoidCallback? onClinicChanged,
}) async {
  tester.view.physicalSize = const Size(360 * 3, 1800 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final c = StudentScheduleController(repository: repo);
  addTearDown(c.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: StudentSchedulePage(
          controller: c,
          onClinicChanged: onClinicChanged ?? () {},
          today: () => '2026-09-27',
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return c;
}

void main() {
  group('날짜', () {
    test('kstToday 는 기기 시간대가 아니라 KST 날짜다', () {
      // UTC 15:30 = KST 다음 날 00:30.
      expect(kstToday(DateTime.utc(2026, 9, 30, 15, 30)), '2026-10-01');
      expect(kstToday(DateTime.utc(2026, 9, 30, 14, 59)), '2026-09-30');
    });

    test('addDaysTo 는 달을 넘긴다', () {
      expect(addDaysTo('2026-09-27', 20), '2026-10-17');
    });

    test('수업 칸 라벨은 시각을 다섯 자로 자르고, 없으면 뺀다', () {
      expect(lessonSlotLabel(_mon), '09-28 (월) A반 19:00');
      expect(lessonSlotLabel(_wed), '09-30 (수) B반');
    });
  });

  group('저장소', () {
    test('변경은 targetClinicId 를 null 로도 보낸다(같은 클리닉 안 시각 변경)', () async {
      const ok = FakeReply(
        statusCode: 200,
        body: {'success': true, 'data': null},
      );
      final adapter = FakeAdapter(replies: [ok]);
      final repo = StudentScheduleRepository(
        Dio(BaseOptions(baseUrl: 'https://x.test'))
          ..httpClientAdapter = adapter,
      );
      await repo.changeClinic(
        5,
        targetClinicId: null,
        arrivalTime: '18:00',
        reason: '학원 늦음',
      );
      final r = adapter.received.single;
      expect(r.method, 'PATCH');
      expect(r.path, '/api/student/clinics/5/reservation');
      expect(r.data, {
        'targetClinicId': null,
        'arrivalTime': '18:00',
        'reason': '학원 늦음',
      });
    });
  });

  group('화면', () {
    testWidgets('오늘부터 20일을 부르고, 배정된 클리닉만 「내 클리닉」이다', (tester) async {
      final repo = _Repo(
        clinicList: [
          _clinic(1, mine: '18:00'),
          _clinic(2, date: '2026-10-01'),
        ],
      );
      await _pump(tester, repo);
      expect(repo.clinicCalls, [('2026-09-27', '2026-10-17')]);
      expect(find.text('스케줄 관리'), findsOneWidget);
      expect(find.text('09-29 18:00 도착'), findsOneWidget);
      expect(find.byKey(const ValueKey('clinic-1')), findsOneWidget);
      // 배정 안 된 클리닉은 이동 후보일 뿐 화면에 안 그린다(신청 화면 금지).
      expect(find.byKey(const ValueKey('clinic-2')), findsNothing);
      // 취소 버튼은 없다(10-2).
      expect(find.text('취소'), findsNothing);
      expect(find.textContaining('취소'), findsNothing);
    });

    testWidgets('배정이 없으면 안내를 띄운다', (tester) async {
      await _pump(tester, _Repo(clinicList: [_clinic(2)]));
      expect(find.text('스케줄 관리'), findsOneWidget);
      expect(find.text('배정된 클리닉이 없습니다.'), findsOneWidget);
    });

    testWidgets('이동 후보에서 자기·꽉 찬 곳·이미 배정된 곳을 뺀다', (tester) async {
      final data = StudentScheduleData(
        clinics: [
          _clinic(1, mine: '18:00'),
          _clinic(2),
          _clinic(3, full: true),
          _clinic(4, mine: '17:00'),
        ],
        requests: const [],
      );
      expect(data.moveTargets(data.clinics.first).map((c) => c.clinicId), [2]);
    });

    testWidgets('사유가 없으면 못 바꾸고, 바꾸면 알리고 다시 부른다', (tester) async {
      var changed = 0;
      final repo = _Repo(clinicList: [_clinic(1, mine: '18:00')]);
      await _pump(tester, repo, onClinicChanged: () => changed++);

      await tester.tap(find.byKey(const ValueKey('clinic-change-1')));
      await tester.pumpAndSettle();
      final submit = find.byKey(const Key('change-submit'));
      expect(
        tester
            .widget<FilledButton>(
              find.descendant(of: submit, matching: find.byType(FilledButton)),
            )
            .onPressed,
        isNull,
      );

      await tester.tap(find.byKey(const ValueKey('slot-19:00')));
      await tester.enterText(
        find.descendant(
          of: find.byKey(const Key('change-reason')),
          matching: find.byType(TextField),
        ),
        '  학교 보충  ',
      );
      await tester.pumpAndSettle();
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(repo.changes.single, {
        'clinicId': 1,
        'targetClinicId': null,
        'arrivalTime': '19:00',
        'reason': '학교 보충',
      });
      expect(changed, 1);
      // 시트가 닫히고 화면은 새로 받았다.
      expect(find.byKey(const Key('change-submit')), findsNothing);
      expect(repo.clinicCalls, hasLength(2));
    });

    testWidgets('옮길 곳에 고른 시각이 없으면 그곳의 첫 슬롯을 보낸다', (tester) async {
      final repo = _Repo(
        clinicList: [
          _clinic(1, mine: '19:00'),
          _clinic(2, date: '2026-10-02', slots: const ['17:00', '18:00']),
        ],
      );
      await _pump(tester, repo);
      await tester.tap(find.byKey(const ValueKey('clinic-change-1')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('change-target')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('10-02 (금) 17:00~20:00').last);
      await tester.pumpAndSettle();
      // 새 클리닉의 슬롯만 보인다.
      expect(find.byKey(const ValueKey('slot-19:00')), findsNothing);

      await tester.enterText(
        find.descendant(
          of: find.byKey(const Key('change-reason')),
          matching: find.byType(TextField),
        ),
        '이동',
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('change-submit')));
      await tester.pumpAndSettle();
      expect(repo.changes.single['targetClinicId'], 2);
      expect(repo.changes.single['arrivalTime'], '17:00');
    });

    testWidgets('변경이 실패하면 서버 문구를 띄우고 시트를 남긴다', (tester) async {
      var changed = 0;
      final repo = _Repo(
        clinicList: [_clinic(1, mine: '18:00')],
        changeError: const ApiException(code: 'X', message: '정원이 찼습니다.'),
      );
      await _pump(tester, repo, onClinicChanged: () => changed++);
      await tester.tap(find.byKey(const ValueKey('clinic-change-1')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byKey(const Key('change-reason')),
          matching: find.byType(TextField),
        ),
        '사유',
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('change-submit')));
      await tester.pumpAndSettle();
      expect(find.text('정원이 찼습니다.'), findsOneWidget);
      expect(find.byKey(const Key('change-submit')), findsOneWidget);
      expect(changed, 0);
    });
  });

  group('수업일 변경', () {
    Future<void> openAndPickFrom(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('lesson-change-open')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('lc-from')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('09-28 (월) A반 19:00').last);
      await tester.pumpAndSettle();
    }

    testWidgets('요청 목록을 상태 배지와 함께 그린다', (tester) async {
      await _pump(
        tester,
        _Repo(
          clinicList: const [],
          requests: const [
            LessonChangeRequest(
              requestId: 9,
              from: _mon,
              to: _wed,
              reason: '가족 행사',
              status: 'REJECTED',
            ),
          ],
        ),
      );
      expect(find.text('09-28 (월) A반 19:00'), findsOneWidget);
      expect(find.text('→ 09-30 (수) B반'), findsOneWidget);
      expect(find.text('거절됨'), findsOneWidget);
      expect(find.text('사유 · 가족 행사'), findsOneWidget);
    });

    testWidgets('못 가는 수업을 고르면 그 후보로 요청을 보낸다', (tester) async {
      final repo = _Repo(clinicList: const []);
      await _pump(tester, repo);
      await openAndPickFrom(tester);
      repo.candidateCalls[1]!.complete(const [_wed]);
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('lc-to-1-1')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('09-30 (수) B반').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byKey(const Key('lc-reason')),
          matching: find.byType(TextField),
        ),
        '가족 행사',
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('lc-submit')));
      await tester.pumpAndSettle();
      expect(repo.lessonRequests.single, {
        'from': 1,
        'to': 2,
        'reason': '가족 행사',
      });
      expect(find.byKey(const Key('lc-submit')), findsNothing);
    });

    testWidgets('못 가는 수업을 바꾸면 앞 수업의 늦은 후보가 덮지 않는다', (tester) async {
      const tue = LessonSlot(
        lessonId: 3,
        classRoomName: 'A반',
        lessonDate: '2026-10-06',
        startTime: '19:00',
      );
      const nextWeek = LessonSlot(
        lessonId: 4,
        classRoomName: 'C반',
        lessonDate: '2026-10-07',
        startTime: '18:00',
      );
      final repo = _Repo(clinicList: const [], myLessons: const [_mon, tue]);
      await _pump(tester, repo);
      await openAndPickFrom(tester);
      await tester.tap(find.byKey(const Key('lc-from')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('10-06 (화) A반 19:00').last);
      await tester.pumpAndSettle();

      repo.candidateCalls[3]!.complete(const [nextWeek]);
      await tester.pumpAndSettle();
      // 앞 수업(1)의 응답이 늦게 온다.
      repo.candidateCalls[1]!.complete(const [_wed]);
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('lc-to-3-1')));
      await tester.pumpAndSettle();
      expect(find.text('10-07 (수) C반 18:00'), findsWidgets);
      expect(find.text('09-30 (수) B반'), findsNothing);
    });

    testWidgets('중복 요청은 앱 문구로 알린다', (tester) async {
      final repo = _Repo(
        clinicList: const [],
        requestError: const ApiException(
          code: 'DUPLICATE_RESOURCE',
          message: '이미 존재합니다.',
        ),
      );
      await _pump(tester, repo);
      await openAndPickFrom(tester);
      repo.candidateCalls[1]!.complete(const [_wed]);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('lc-to-1-1')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('09-30 (수) B반').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.descendant(
          of: find.byKey(const Key('lc-reason')),
          matching: find.byType(TextField),
        ),
        '사유',
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('lc-submit')));
      await tester.pumpAndSettle();
      expect(find.text('이미 그 수업에 변경 요청을 보냈습니다.'), findsOneWidget);
    });
  });
}
