import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/shared/notice/notice_board.dart';
import 'package:academy_app/shared/notice/notice_data.dart';

NoticeListItem _item(
  int id, {
  bool pinned = false,
  bool hasAttachment = false,
}) => NoticeListItem(
  noticeId: id,
  title: '공지 $id',
  pinned: pinned,
  hasAttachment: hasAttachment,
  publishedAt: '2026-09-0${id}T10:00:00+09:00',
);

class _Repo implements NoticeRepository {
  _Repo(this.items);

  final List<NoticeListItem> items;
  final List<int?> listCalls = [];
  final List<(int, int?)> detailCalls = [];
  final List<(int, int, int?)> downloadCalls = [];
  List<NoticeAttachment> attachments = const [];
  Object? downloadError;

  @override
  Future<List<NoticeListItem>> list(int? studentId) async {
    listCalls.add(studentId);
    return items;
  }

  @override
  Future<NoticeDetail> detail(int noticeId, int? studentId) async {
    detailCalls.add((noticeId, studentId));
    return NoticeDetail(
      noticeId: noticeId,
      title: '공지 $noticeId',
      content: '첫 줄\n<b>둘째 줄</b>',
      publishedAt: '2026-09-01T10:00:00+09:00',
      attachments: attachments,
    );
  }

  @override
  Future<String> downloadUrl(
    int noticeId,
    int attachmentId,
    int? studentId,
  ) async {
    downloadCalls.add((noticeId, attachmentId, studentId));
    final e = downloadError;
    if (e != null) throw e;
    return 'https://s3.example/file-$attachmentId?sig=x';
  }
}

Future<void> _pump(
  WidgetTester tester,
  _Repo repo, {
  int? studentId,
  UrlOpener? openUrl,
}) async {
  tester.view.physicalSize = const Size(390 * 3, 1600 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final c = NoticeListController(repository: repo);
  addTearDown(c.dispose);
  await c.load(studentId);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: ListenableBuilder(
            listenable: c,
            builder: (_, _) => NoticeBoard(
              controller: c,
              studentId: studentId,
              openUrl: openUrl ?? (_) async => true,
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Dio _dio(FakeAdapter adapter) =>
    Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = adapter;

const _emptyPage = FakeReply(
  statusCode: 200,
  body: {
    'success': true,
    'data': {'items': []},
  },
);

void main() {
  group('저장소', () {
    test('학생은 studentId 를 보내지 않고, 학부모는 보낸다', () async {
      final adapter = FakeAdapter(replies: [_emptyPage, _emptyPage]);
      final repo = NoticeRepository(_dio(adapter));
      await repo.list(null);
      await repo.list(7);
      expect(adapter.received[0].path, '/api/notices');
      expect(adapter.received[0].queryParameters, isEmpty);
      expect(adapter.received[1].queryParameters, {'studentId': 7});
    });

    test('상세의 attachments 가 없어도 빈 목록으로 받는다 (7-3)', () async {
      // 2026-08-24 에 웹이 옛 응답의 attachments.length 로 공지 화면째 죽었다.
      final adapter = FakeAdapter(
        replies: [
          const FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': {
                'noticeId': 3,
                'title': '옛 공지',
                'content': '본문',
                'pinned': false,
                'publishedAt': '2026-08-01T09:00:00+09:00',
              },
            },
          ),
        ],
      );
      final detail = await NoticeRepository(_dio(adapter)).detail(3, null);
      expect(detail.attachments, isEmpty);
      expect(detail.dateLabel, '2026.08.01');
    });

    test('받기 주소는 누를 때 받고, 자녀 id 를 함께 보낸다', () async {
      final adapter = FakeAdapter(
        replies: [
          const FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': {
                'downloadUrl': 'https://s3/x',
                'fileName': 'a.pdf',
                'expiresIn': 300,
              },
            },
          ),
        ],
      );
      final url = await NoticeRepository(_dio(adapter)).downloadUrl(3, 4, 7);
      expect(url, 'https://s3/x');
      expect(
        adapter.received.single.path,
        '/api/notices/3/attachments/4/download-url',
      );
      expect(adapter.received.single.queryParameters, {'studentId': 7});
    });

    test('크기 표기가 웹 formatBytes 와 같다', () {
      expect(formatBytes(900), '900B');
      expect(formatBytes(1536), '2KB');
      expect(formatBytes(5 * 1024 * 1024 + 300000), '5.3MB');
    });
  });

  testWidgets('목록은 서버 순서 그대로이고 고정·자료 표시가 붙는다', (tester) async {
    await _pump(
      tester,
      _Repo([_item(1, pinned: true), _item(2, hasAttachment: true), _item(3)]),
    );
    final ys = [
      for (final id in [1, 2, 3])
        tester.getTopLeft(find.byKey(ValueKey('notice-$id'))).dy,
    ];
    expect(ys, orderedEquals([...ys]..sort()));
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('notice-1')),
        matching: find.byKey(const Key('notice-pinned')),
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('notice-pinned')), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('notice-2')),
        matching: find.byKey(const Key('notice-attachment')),
      ),
      findsOneWidget,
    );
    expect(find.text('09/01'), findsOneWidget);
  });

  testWidgets('공지가 없으면 안내 한 줄이다', (tester) async {
    await _pump(tester, _Repo([]));
    expect(find.text('등록된 공지가 없습니다.'), findsOneWidget);
  });

  testWidgets('줄을 누르면 상세가 뜨고, 본문은 글자 그대로다', (tester) async {
    final repo = _Repo([_item(1)]);
    await _pump(tester, repo, studentId: 7);
    await tester.tap(find.byKey(const ValueKey('notice-1')));
    await tester.pumpAndSettle();
    expect(repo.detailCalls, [(1, 7)]);
    // HTML 로 해석하지 않는다 — 태그가 글자로 남는다.
    expect(find.text('첫 줄\n<b>둘째 줄</b>'), findsOneWidget);
    expect(find.text('2026.09.01'), findsOneWidget);
  });

  testWidgets('받기를 누르면 그때 주소를 받아 기기에 넘긴다', (tester) async {
    final repo = _Repo([_item(1, hasAttachment: true)])
      ..attachments = const [
        NoticeAttachment(attachmentId: 4, fileName: '안내문.pdf', bytes: 2048),
      ];
    final opened = <Uri>[];
    await _pump(
      tester,
      repo,
      studentId: 7,
      openUrl: (uri) async {
        opened.add(uri);
        return true;
      },
    );
    await tester.tap(find.byKey(const ValueKey('notice-1')));
    await tester.pumpAndSettle();
    // 상세를 열었다고 미리 받지 않는다 — 5분짜리다.
    expect(repo.downloadCalls, isEmpty);
    expect(find.text('📎 안내문.pdf (2KB)'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('attachment-4')));
    await tester.pumpAndSettle();
    expect(repo.downloadCalls, [(1, 4, 7)]);
    expect(opened, [Uri.parse('https://s3.example/file-4?sig=x')]);
  });

  testWidgets('못 열거나 서버가 거절하면 시트 안에 문구가 뜬다', (tester) async {
    final repo = _Repo([_item(1, hasAttachment: true)])
      ..attachments = const [
        NoticeAttachment(attachmentId: 4, fileName: 'a.pdf', bytes: null),
      ];
    await _pump(tester, repo, openUrl: (_) async => false);
    await tester.tap(find.byKey(const ValueKey('notice-1')));
    await tester.pumpAndSettle();
    // 크기를 모르면 적지 않는다.
    expect(find.text('📎 a.pdf'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('attachment-4')));
    await tester.pumpAndSettle();
    expect(find.text('파일을 열 수 없습니다. 잠시 후 다시 시도해 주세요.'), findsOneWidget);

    repo.downloadError = const ApiException(
      code: 'NOT_FOUND',
      message: '공지를 찾을 수 없습니다.',
    );
    await tester.tap(find.byKey(const ValueKey('attachment-4')));
    await tester.pumpAndSettle();
    expect(find.text('공지를 찾을 수 없습니다.'), findsOneWidget);
  });
}
