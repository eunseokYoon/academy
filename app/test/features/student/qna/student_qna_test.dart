import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/api/s3_uploader.dart';
import 'package:academy_app/features/student/homeworks/submission_media.dart';
import 'package:academy_app/features/student/qna/qna_data.dart';
import 'package:academy_app/features/student/qna/student_qna_detail_page.dart';
import 'package:academy_app/features/student/qna/student_qna_page.dart';
import 'package:academy_app/shared/widgets/star_rating.dart';

const _summary = QnaSummary(
  postId: 31,
  title: '관계대명사 질문',
  authorName: '김하늘',
  classRoomName: 'A반',
  isPublic: false,
  hasPhoto: true,
  answerCount: 2,
);

const _roomA = QnaClassRoom(classRoomId: 1, name: 'A반');
const _roomB = QnaClassRoom(classRoomId: 2, name: 'B반');

QnaDetail _detail({bool editable = true}) => QnaDetail(
  postId: 31,
  title: '관계대명사 질문',
  authorName: '김하늘',
  classRoomName: 'A반',
  isPublic: true,
  content: 'that 과 which',
  photos: const [],
  editable: editable,
  answers: const [
    QnaAnswer(
      answerId: 1,
      authorName: '선생님',
      byTeacher: true,
      content: '수업 때 봐요',
      photos: [],
    ),
    QnaAnswer(
      answerId: 2,
      authorName: '김하늘',
      byTeacher: false,
      content: '감사합니다',
      photos: [],
    ),
  ],
);

class _Repo implements QnaRepository {
  _Repo({
    this.items = const [_summary],
    this.review,
    this.rooms = const [_roomA],
  });

  List<QnaSummary> items;
  MyReview? review;
  List<QnaClassRoom> rooms;
  int listCalls = 0;
  int detailCalls = 0;
  final List<Map<String, Object?>> created = [];
  final List<Map<String, Object?>> answers = [];
  final List<Map<String, Object?>> reviews = [];
  final List<int> deleted = [];
  int reviewDeletes = 0;

  @override
  Future<List<QnaSummary>> list() async {
    listCalls++;
    return items;
  }

  @override
  Future<MyReview?> myReview() async => review;

  @override
  Future<List<QnaClassRoom>> classRooms() async => rooms;

  @override
  Future<QnaDetail> detail(int postId) async {
    detailCalls++;
    return _detail();
  }

  @override
  Future<void> create({
    required int classRoomId,
    required String title,
    required String content,
    required bool isPublic,
    required List<String> s3Keys,
  }) async => created.add({
    'classRoomId': classRoomId,
    'title': title,
    'content': content,
    'isPublic': isPublic,
    's3Keys': s3Keys,
  });

  @override
  Future<void> answer(
    int postId, {
    required String content,
    required List<String> s3Keys,
  }) async =>
      answers.add({'postId': postId, 'content': content, 's3Keys': s3Keys});

  @override
  Future<void> delete(int postId) async => deleted.add(postId);

  @override
  Future<void> saveReview({
    required bool exists,
    required double rating,
    required String content,
  }) async =>
      reviews.add({'exists': exists, 'rating': rating, 'content': content});

  @override
  Future<void> deleteReview() async => reviewDeletes++;

  @override
  Future<({String uploadUrl, String s3Key})> uploadUrl({
    required String contentType,
    required int bytes,
  }) => throw UnimplementedError();
}

class _Picker implements MediaPicker {
  _Picker(this.count);

  final int count;
  final List<int> limits = [];

  @override
  Future<List<PreparedPhoto>> pickPhotos(
    MediaFrom from, {
    required int limit,
  }) async {
    limits.add(limit);
    return [
      for (var i = 0; i < count; i++)
        PreparedPhoto(
          // 1×1 투명 PNG — Image.memory 가 풀 수 있어야 한다.
          bytes: _png,
          contentType: 'image/webp',
        ),
    ];
  }

  @override
  Future<PickedVideo?> pickVideo(MediaFrom from) => throw UnimplementedError();
}

final _png = Uint8List.fromList(const [
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, //
  0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4, 0x89, 0x00, 0x00, 0x00,
  0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
  0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
  0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
]);

/// 올림을 테스트가 끝낸다 — 올리는 중 상태를 볼 수 있게.
class _Uploader implements QnaUploader {
  final List<Completer<QnaUploadedPhoto>> pending = [];
  int _n = 0;

  @override
  Future<QnaUploadedPhoto> upload(PreparedPhoto photo) {
    final c = Completer<QnaUploadedPhoto>();
    pending.add(c);
    return c.future;
  }

  void finishAll() {
    for (final c in pending) {
      if (!c.isCompleted) {
        c.complete(QnaUploadedPhoto(s3Key: 'qna/k${_n++}', bytes: _png));
      }
    }
  }
}

FilledButton _button(WidgetTester tester, String key) => tester.widget(
  find.descendant(
    of: find.byKey(Key(key)),
    matching: find.byType(FilledButton),
  ),
);

Finder _field(String key) =>
    find.descendant(of: find.byKey(Key(key)), matching: find.byType(TextField));

Future<GoRouter> _pumpRouter(WidgetTester tester, Widget page) async {
  tester.view.physicalSize = const Size(360 * 3, 2400 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final router = GoRouter(
    initialLocation: '/start',
    routes: [
      GoRoute(
        path: '/start',
        builder: (_, _) => Scaffold(body: page),
      ),
      GoRoute(
        path: '/student/qna',
        builder: (_, _) => const Text('목록 화면'),
        routes: [
          GoRoute(
            path: ':id',
            builder: (_, s) => Text('상세 ${s.pathParameters['id']}'),
          ),
        ],
      ),
    ],
  );
  addTearDown(router.dispose);
  await tester.pumpWidget(MaterialApp.router(routerConfig: router));
  await tester.pumpAndSettle();
  return router;
}

Future<(QnaBoardController, GoRouter)> _pumpBoard(
  WidgetTester tester,
  _Repo repo, {
  MediaPicker? picker,
  QnaUploader? uploader,
}) async {
  final c = QnaBoardController(repository: repo);
  addTearDown(c.dispose);
  final router = await _pumpRouter(
    tester,
    StudentQnaPage(
      controller: c,
      uploader: uploader ?? _Uploader(),
      picker: picker ?? _Picker(1),
    ),
  );
  return (c, router);
}

void main() {
  group('저장소', () {
    Dio dioWith(FakeAdapter adapter) =>
        Dio(BaseOptions(baseUrl: 'https://x.test'))
          ..httpClientAdapter = adapter;
    const ok = FakeReply(
      statusCode: 200,
      body: {'success': true, 'data': null},
    );

    test('후기가 없으면 null 이다(404 가 아니다)', () async {
      final repo = QnaRepository(dioWith(FakeAdapter(replies: [ok])));
      expect(await repo.myReview(), isNull);
    });

    test('후기는 있으면 PATCH, 없으면 POST 다', () async {
      final adapter = FakeAdapter(replies: [ok, ok]);
      final repo = QnaRepository(dioWith(adapter));
      await repo.saveReview(exists: false, rating: 3.5, content: '좋아요');
      await repo.saveReview(exists: true, rating: 4, content: '좋아요');
      expect(adapter.received[0].method, 'POST');
      expect(adapter.received[0].path, '/api/student/reviews');
      // 별 개수 그대로 보낸다 — 2배 저장값으로 바꾸는 건 서버 Ratings 다.
      expect(adapter.received[0].data, {'rating': 3.5, 'content': '좋아요'});
      expect(adapter.received[1].method, 'PATCH');
      expect(adapter.received[1].path, '/api/student/reviews/me');
    });

    test('반 목록은 /student/me 의 classRooms 에서 id 와 함께 받는다', () async {
      final adapter = FakeAdapter(
        replies: [
          const FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': {
                'name': '김하늘',
                'classRooms': [
                  {'classRoomId': 7, 'name': 'A반'},
                ],
              },
            },
          ),
        ],
      );
      final rooms = await QnaRepository(dioWith(adapter)).classRooms();
      expect(adapter.received.single.path, '/api/student/me');
      expect(rooms.single.classRoomId, 7);
    });

    test('업로더는 S3 가 실패하면 앱 문구로 바꾼다', () async {
      final api = FakeAdapter(
        replies: [
          const FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': {'uploadUrl': 'https://s3.test/put', 's3Key': 'qna/1'},
            },
          ),
        ],
      );
      final s3 = FakeAdapter(
        replies: [const FakeReply(statusCode: 403, body: {})],
      );
      final uploader = QnaUploader(
        repository: QnaRepository(dioWith(api)),
        s3: S3Uploader(Dio()..httpClientAdapter = s3),
      );
      await expectLater(
        uploader.upload(PreparedPhoto(bytes: _png, contentType: 'image/jpeg')),
        throwsA(
          isA<UploadFailure>().having(
            (e) => e.message,
            'message',
            '사진을 올리지 못했습니다. 다시 시도해 주세요.',
          ),
        ),
      );
      // 발급 요청이 기기가 준 형식(iOS JPEG)을 그대로 보낸다.
      expect(api.received.single.data, {
        'contentType': 'image/jpeg',
        'bytes': _png.length,
      });
      expect(s3.received.single.headers['Authorization'], isNull);
    });
  });

  group('목록', () {
    testWidgets('줄에 자물쇠·답글 수·사진이 붙고, 누르면 상세로 간다', (tester) async {
      final (_, router) = await _pumpBoard(tester, _Repo());
      expect(find.text('질의응답'), findsOneWidget);
      expect(find.text('🔒 관계대명사 질문'), findsOneWidget);
      expect(find.text('답글 2'), findsOneWidget);
      expect(find.text('김하늘 · A반 · 사진'), findsOneWidget);
      // 상태 배지는 없다(2026-08-16).
      expect(find.textContaining('답변'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('qna-31')));
      await tester.pumpAndSettle();
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        '/student/qna/31',
      );
    });

    testWidgets('질문이 없으면 안내한다', (tester) async {
      await _pumpBoard(tester, _Repo(items: const []));
      expect(find.text('질의응답'), findsOneWidget);
      expect(find.text('아직 질문이 없습니다. 궁금한 걸 물어보세요.'), findsOneWidget);
    });

    testWidgets('후기 버튼 라벨이 내 후기 유무를 따른다', (tester) async {
      await _pumpBoard(tester, _Repo());
      expect(find.text('수강 후기'), findsOneWidget);
    });

    testWidgets('내 후기가 있으면 「내 후기」다', (tester) async {
      await _pumpBoard(
        tester,
        _Repo(review: const MyReview(rating: 4.5, content: '좋아요')),
      );
      expect(find.text('내 후기'), findsOneWidget);
    });
  });

  group('질문 쓰기', () {
    Future<void> fill(WidgetTester tester) async {
      await tester.enterText(_field('qna-title'), '  질문 제목 ');
      await tester.enterText(_field('qna-content'), '내용입니다');
      await tester.pumpAndSettle();
    }

    testWidgets('기본은 비공개로 올리고, 반이 하나면 고르는 칸이 없다', (tester) async {
      final repo = _Repo();
      await _pumpBoard(tester, repo);
      await tester.tap(find.byKey(const Key('qna-write-toggle')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('qna-question-form')), findsOneWidget);
      expect(find.byKey(const Key('qna-class')), findsNothing);
      expect(_button(tester, 'qna-submit').onPressed, isNull);

      await fill(tester);
      await tester.tap(find.byKey(const Key('qna-submit')));
      await tester.pumpAndSettle();
      expect(repo.created.single, {
        'classRoomId': 1,
        'title': '질문 제목',
        'content': '내용입니다',
        'isPublic': false,
        's3Keys': <String>[],
      });
      // 폼이 닫히고 목록을 다시 받았다.
      expect(find.byKey(const Key('qna-question-form')), findsNothing);
      expect(repo.listCalls, 2);
    });

    testWidgets('공개를 켜면 공개로 올린다', (tester) async {
      final repo = _Repo();
      await _pumpBoard(tester, repo);
      await tester.tap(find.byKey(const Key('qna-write-toggle')));
      await tester.pumpAndSettle();
      await fill(tester);
      await tester.tap(find.byKey(const Key('qna-public')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('qna-submit')));
      await tester.pumpAndSettle();
      expect(repo.created.single['isPublic'], isTrue);
    });

    testWidgets('반이 둘이면 골라서 올린다', (tester) async {
      final repo = _Repo(rooms: const [_roomA, _roomB]);
      await _pumpBoard(tester, repo);
      await tester.tap(find.byKey(const Key('qna-write-toggle')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('qna-class')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('B반').last);
      await tester.pumpAndSettle();
      await fill(tester);
      await tester.tap(find.byKey(const Key('qna-submit')));
      await tester.pumpAndSettle();
      expect(repo.created.single['classRoomId'], 2);
    });

    testWidgets('사진을 올리는 동안은 못 내고, 다 올리면 키를 함께 보낸다', (tester) async {
      final repo = _Repo();
      final picker = _Picker(2);
      final uploader = _Uploader();
      await _pumpBoard(tester, repo, picker: picker, uploader: uploader);
      await tester.tap(find.byKey(const Key('qna-write-toggle')));
      await tester.pumpAndSettle();
      await fill(tester);

      await tester.tap(find.byKey(const Key('qna-add-photo')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('source-gallery')));
      await tester.pump();
      await tester.pump();
      expect(picker.limits, [kQnaMaxPhotos]);
      expect(_button(tester, 'qna-submit').onPressed, isNull);
      expect(find.text('올리는 중'), findsOneWidget);

      // 한 장씩 올린다 — 첫 장이 끝나야 둘째가 시작된다.
      uploader.finishAll();
      await tester.pump();
      await tester.pump();
      uploader.finishAll();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('qna-preview-1')), findsOneWidget);

      // 첫 장을 뺀다.
      await tester.tap(find.byKey(const ValueKey('qna-remove-0')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('qna-submit')));
      await tester.pumpAndSettle();
      expect(repo.created.single['s3Keys'], ['qna/k1']);
    });
  });

  group('후기', () {
    testWidgets('빈 채로는 못 내고, 이유를 보여준다', (tester) async {
      await _pumpBoard(tester, _Repo());
      await tester.tap(find.byKey(const Key('qna-review-toggle')));
      await tester.pumpAndSettle();
      expect(find.text('별점과 내용을 모두 입력해 주세요.'), findsOneWidget);
      expect(_button(tester, 'review-save').onPressed, isNull);
      // 새 후기에는 삭제가 없다.
      expect(find.byKey(const Key('review-delete')), findsNothing);

      await tester.tap(find.byKey(const ValueKey('star-3.5')));
      await tester.pumpAndSettle();
      expect(find.text('내용을 적어 주세요.'), findsOneWidget);
    });

    testWidgets('내용만 있으면 별점을 고르라고 한다', (tester) async {
      await _pumpBoard(tester, _Repo());
      await tester.tap(find.byKey(const Key('qna-review-toggle')));
      await tester.pumpAndSettle();
      await tester.enterText(_field('review-content'), '재밌어요');
      await tester.pumpAndSettle();
      expect(find.text('별점을 선택해 주세요.'), findsOneWidget);
      expect(_button(tester, 'review-save').onPressed, isNull);
    });

    testWidgets('반쪽 별을 누르면 0.5 단위로 저장한다', (tester) async {
      final repo = _Repo();
      await _pumpBoard(tester, repo);
      await tester.tap(find.byKey(const Key('qna-review-toggle')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('star-3.5')));
      await tester.enterText(_field('review-content'), '재밌어요');
      await tester.pumpAndSettle();
      expect(find.text('3.5'), findsOneWidget);
      await tester.tap(find.byKey(const Key('review-save')));
      await tester.pumpAndSettle();
      expect(repo.reviews.single, {
        'exists': false,
        'rating': 3.5,
        'content': '재밌어요',
      });
    });

    testWidgets('있는 후기는 채워서 열고, 확인해야 지운다', (tester) async {
      final repo = _Repo(review: const MyReview(rating: 4, content: '좋아요'));
      await _pumpBoard(tester, repo);
      await tester.tap(find.byKey(const Key('qna-review-toggle')));
      await tester.pumpAndSettle();
      expect(find.text('4.0'), findsOneWidget);
      expect(find.text('좋아요'), findsOneWidget);

      // 고치면 새로 만들지 않고 PATCH 한다(학생당 하나).
      await tester.tap(find.byKey(const ValueKey('star-4.5')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('review-save')));
      await tester.pumpAndSettle();
      expect(repo.reviews.single, {
        'exists': true,
        'rating': 4.5,
        'content': '좋아요',
      });
      await tester.tap(find.byKey(const Key('qna-review-toggle')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('review-delete')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('취소'));
      await tester.pumpAndSettle();
      expect(repo.reviewDeletes, 0);

      await tester.tap(find.byKey(const Key('review-delete')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('confirm-delete')));
      await tester.pumpAndSettle();
      expect(repo.reviewDeletes, 1);
    });
  });

  group('별점', () {
    testWidgets('입력 별은 56 이고 360 카드에서 넘치지 않는다', (tester) async {
      tester.view.physicalSize = const Size(360 * 3, 800 * 3);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Padding(
              // 화면 p-4 + 카드 p-4.
              padding: const EdgeInsets.all(32),
              child: StarRating(value: 5, onChanged: (_) {}),
            ),
          ),
        ),
      );
      expect(tester.getSize(find.byKey(const ValueKey('star-5.0'))).height, 56);
      expect(tester.takeException(), isNull);
    });
  });

  group('상세', () {
    Future<(_Repo, List<int>, GoRouter)> pumpDetail(WidgetTester tester) async {
      final repo = _Repo();
      final changed = <int>[];
      final c = QnaDetailController(repository: repo);
      addTearDown(c.dispose);
      final router = await _pumpRouter(
        tester,
        StudentQnaDetailPage(
          postId: 31,
          controller: c,
          uploader: _Uploader(),
          picker: _Picker(1),
          onChanged: () => changed.add(1),
        ),
      );
      return (repo, changed, router);
    }

    testWidgets('질문과 답글을 그리고 선생님 답글은 남색 칸이다', (tester) async {
      await pumpDetail(tester);
      expect(find.text('관계대명사 질문'), findsOneWidget);
      expect(find.text('that 과 which'), findsOneWidget);
      final teacher = tester.widget<Container>(
        find.byKey(const ValueKey('qna-answer-1')),
      );
      final student = tester.widget<Container>(
        find.byKey(const ValueKey('qna-answer-2')),
      );
      expect(
        (teacher.decoration! as BoxDecoration).color,
        isNot((student.decoration! as BoxDecoration).color),
      );
    });

    testWidgets('답글을 쓰면 보내고 비우고, 목록을 낡게 표시한다', (tester) async {
      final (repo, changed, _) = await pumpDetail(tester);
      expect(_button(tester, 'answer-submit').onPressed, isNull);
      await tester.enterText(_field('answer-content'), '  한 번 더요 ');
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('answer-submit')));
      await tester.pumpAndSettle();
      expect(repo.answers.single, {
        'postId': 31,
        'content': '한 번 더요',
        's3Keys': <String>[],
      });
      expect(changed, [1]);
      expect(repo.detailCalls, 2);
      expect(
        tester.widget<TextField>(_field('answer-content')).controller!.text,
        '',
      );
    });

    testWidgets('질문 삭제는 확인한 뒤 지우고 목록으로 간다', (tester) async {
      final (repo, changed, router) = await pumpDetail(tester);
      await tester.tap(find.byKey(const Key('qna-delete')));
      await tester.pumpAndSettle();
      expect(find.text('질문을 지우면 답글도 함께 사라집니다. 지울까요?'), findsOneWidget);
      await tester.tap(find.byKey(const Key('confirm-delete')));
      await tester.pumpAndSettle();
      expect(repo.deleted, [31]);
      expect(changed, [1]);
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        '/student/qna',
      );
    });
  });
}
