import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/notices/parent_notices_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/features/student/notices/student_notices_page.dart';
import 'package:academy_app/shared/notice/notice_data.dart';

class _Repo implements NoticeRepository {
  final List<int?> listCalls = [];

  @override
  Future<List<NoticeListItem>> list(int? studentId) async {
    listCalls.add(studentId);
    return [
      NoticeListItem(
        noticeId: 1,
        title: '자녀 $studentId 의 공지',
        pinned: false,
        hasAttachment: false,
        publishedAt: '2026-09-01T10:00:00+09:00',
      ),
    ];
  }

  final List<(int, int?)> detailCalls = [];

  @override
  Future<NoticeDetail> detail(int noticeId, int? studentId) async {
    detailCalls.add((noticeId, studentId));
    return NoticeDetail(
      noticeId: noticeId,
      title: '상세',
      content: '본문',
      publishedAt: '2026-09-01T10:00:00+09:00',
      attachments: const [],
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  testWidgets('학생 공지는 자녀 id 없이 부른다', (tester) async {
    final repo = _Repo();
    final c = NoticeListController(repository: repo);
    addTearDown(c.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StudentNoticesPage(controller: c, openUrl: (_) async => true),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(repo.listCalls, [null]);
    expect(find.text('학원 공지 · 안내'), findsOneWidget);
    expect(find.text('자녀 null 의 공지'), findsOneWidget);
  });

  testWidgets('학부모 공지는 고른 자녀 기준이고, 자녀를 바꾸면 그 아이 것을 부른다', (tester) async {
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = FakeAdapter(
        replies: const [
          FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': [
                {'studentId': 1, 'name': '김하늘'},
                {'studentId': 2, 'name': '김바다'},
              ],
            },
          ),
        ],
      );
    final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
    addTearDown(sc.dispose);
    final repo = _Repo();
    final c = NoticeListController(repository: repo);
    addTearDown(c.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ParentNoticesPage(
            controller: c,
            selectedChild: sc,
            openUrl: (_) async => true,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(repo.listCalls, [1]);
    expect(find.text('자녀 1 의 공지'), findsOneWidget);

    await sc.select(2);
    await tester.pumpAndSettle();
    expect(repo.listCalls, [1, 2]);
    expect(find.text('자녀 2 의 공지'), findsOneWidget);
    expect(find.text('자녀 1 의 공지'), findsNothing);

    // 상세도 그 아이 기준이다 — 반 범위 공지가 자녀마다 달라서다.
    await tester.tap(find.byKey(const ValueKey('notice-1')));
    await tester.pumpAndSettle();
    expect(repo.detailCalls, [(1, 2)]);
  });
}
