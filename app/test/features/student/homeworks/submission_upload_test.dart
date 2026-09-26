import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/api/s3_uploader.dart';
import 'package:academy_app/features/student/homeworks/student_homework_repository.dart';
import 'package:academy_app/features/student/homeworks/submission_media.dart';

FakeReply _ok(Object? data) =>
    FakeReply(statusCode: 200, body: {'success': true, 'data': data});

Map<String, dynamic> _item({
  int id = 1,
  String kind = 'GRID',
  String status = 'NOT_SUBMITTED',
  bool resubmitRequired = false,
}) => {
  'homeworkId': id,
  'title': '숙제 $id',
  'description': null,
  'classRoomName': 'A반',
  'kind': kind,
  'lessonDate': '2026-09-14',
  'result': null,
  'completionRate': null,
  'resolvedByResubmission': false,
  'resubmitRequired': resubmitRequired,
  'dueAt': null,
  'status': status,
  'isLate': false,
  'photoCount': 0,
  'hasVideo': false,
  'remainingMinutes': null,
};

void main() {
  late FakeAdapter api;
  late StudentHomeworkRepository repo;

  StudentHomeworkRepository build(List<FakeReply> replies) {
    api = FakeAdapter(replies: replies);
    return StudentHomeworkRepository(
      Dio(BaseOptions(baseUrl: 'https://example.test'))
        ..httpClientAdapter = api,
    );
  }

  group('저장소', () {
    test('달을 안 고르면 연·월을 보내지 않고, 고르면 둘 다 보낸다', () async {
      repo = build([
        _ok({'items': []}),
        _ok({'items': []}),
      ]);
      await repo.list();
      await repo.list(year: 2026, month: 8);
      expect(api.received[0].queryParameters, isEmpty);
      expect(api.received[1].queryParameters, {'year': 2026, 'month': 8});
    });

    test('null 은 기본값으로 채우지 않는다 (14-3)', () async {
      repo = build([
        _ok({
          'items': [_item()],
        }),
      ]);
      final item = (await repo.list()).single;
      expect(item.result, isNull); // 미채점 — 0 이 아니다
      expect(item.remainingMinutes, isNull); // 마감 없음 — 0 이 아니다
      expect(item.dueAt, isNull);
    });

    test(
      '할 일 판정은 축이 둘이다 — GRID 는 resubmitRequired, ONLINE 은 status (4-7)',
      () async {
        repo = build([
          _ok({
            'items': [
              // ⭕ 받은 GRID: status 가 영원히 NOT_SUBMITTED 지만 할 일이 아니다.
              _item(id: 1, kind: 'GRID', status: 'NOT_SUBMITTED'),
              _item(id: 2, kind: 'GRID', resubmitRequired: true),
              _item(id: 3, kind: 'ONLINE', status: 'NOT_SUBMITTED'),
              _item(id: 4, kind: 'ONLINE', status: 'SUBMITTED'),
            ],
          }),
        ]);
        final items = await repo.list();
        expect(items.where((h) => h.isTodo).map((h) => h.homeworkId), [2, 3]);
      },
    );

    test('상세의 사진 배열이 없어도 죽지 않는다 (7-3)', () async {
      repo = build([
        _ok({
          'homework': {
            'id': 7,
            'title': 't',
            'description': null,
            'kind': 'ONLINE',
            'lessonDate': null,
            'dueAt': null,
            'classRoomName': 'A반',
          },
          'submission': {
            'id': 1,
            'status': 'NOT_SUBMITTED',
            'submittedAt': null,
            'isLate': false,
            'video': null,
          },
          'resubmitRequired': false,
        }),
      ]);
      final d = await repo.detail(7);
      expect(d.photos, isEmpty);
      expect(d.video, isNull);
      expect(d.canSubmit, isTrue); // ONLINE 은 언제나 낼 수 있다
    });
  });

  group('영상 거르기', () {
    test('확장자로 형식을 정한다 — mov 를 빼지 않는다 (6번)', () {
      expect(videoContentTypeOf('/a/b.MOV'), 'video/quicktime');
      expect(videoContentTypeOf('x.mp4'), 'video/mp4');
      expect(videoContentTypeOf('x.webm'), 'video/webm');
      expect(videoContentTypeOf('x.avi'), isNull);
      expect(videoContentTypeOf('noext'), isNull);
    });

    test('100MB 를 넘거나 모르는 형식이면 올리기 전에 막는다', () {
      const ok = PickedVideo(
        path: 'a.mp4',
        bytes: kMaxVideoBytes,
        contentType: 'video/mp4',
      );
      expect(videoProblem(ok), isNull);
      const big = PickedVideo(
        path: 'a.mp4',
        bytes: kMaxVideoBytes + 1,
        contentType: 'video/mp4',
      );
      expect(videoProblem(big), contains('100MB까지'));
      const odd = PickedVideo(path: 'a.avi', bytes: 10, contentType: null);
      expect(videoProblem(odd), 'mp4 · mov · webm 형식만 올릴 수 있습니다.');
    });
  });

  group('올리기 세 단계', () {
    final photo = PreparedPhoto(
      bytes: Uint8List.fromList(List.filled(10, 1)),
      contentType: 'image/webp',
    );

    test('발급 → S3 PUT → 등록 순서이고, S3 에는 인증 헤더가 없다', () async {
      repo = build([
        _ok({'uploadUrl': 'https://s3.test/put?sig=1', 's3Key': 'k1'}),
        _ok({'photoId': 9, 'photoCount': 1}),
      ]);
      final s3 = FakeAdapter(
        replies: const [FakeReply(statusCode: 200, body: {})],
      );
      final uploader = SubmissionUploader(
        repository: repo,
        s3: S3Uploader(Dio()..httpClientAdapter = s3),
      );
      await uploader.uploadPhoto(3, photo, sortOrder: 2);

      expect(
        api.received[0].path,
        '/api/student/homeworks/3/photos/upload-url',
      );
      expect(api.received[0].data, {'contentType': 'image/webp', 'bytes': 10});
      final put = s3.received.single;
      expect(put.method, 'PUT');
      expect(put.uri.toString(), 'https://s3.test/put?sig=1');
      // 서명에 들어간 형식과 같아야 한다.
      expect(put.headers['content-type'], 'image/webp');
      expect(put.headers.containsKey('Authorization'), isFalse);
      expect(api.received[1].path, '/api/student/homeworks/3/photos');
      expect(api.received[1].data, {
        's3Key': 'k1',
        'sortOrder': 2,
        'bytes': 10,
      });
    });

    test('S3 가 실패하면 등록하지 않고 웹과 같은 문구로 알린다', () async {
      repo = build([
        _ok({'uploadUrl': 'https://s3.test/put', 's3Key': 'k1'}),
      ]);
      final s3 = FakeAdapter(
        replies: const [FakeReply(statusCode: 403, body: {})],
      );
      final uploader = SubmissionUploader(
        repository: repo,
        s3: S3Uploader(Dio()..httpClientAdapter = s3),
      );
      await expectLater(
        uploader.uploadPhoto(3, photo, sortOrder: 1),
        throwsA(
          isA<UploadFailure>().having(
            (e) => e.message,
            'message',
            '사진을 올리지 못했습니다. 다시 시도해 주세요.',
          ),
        ),
      );
      expect(api.received, hasLength(1)); // 등록 요청이 안 나갔다
    });

    test('발급을 서버가 거절하면 서버 문구가 그대로 올라간다', () async {
      repo = build([
        const FakeReply(
          statusCode: 409,
          body: {
            'success': false,
            'data': null,
            'error': {'code': 'NOT_EDITABLE', 'message': '제출할 수 없는 숙제입니다.'},
          },
        ),
      ]);
      final uploader = SubmissionUploader(repository: repo, s3: S3Uploader());
      await expectLater(
        uploader.uploadPhoto(3, photo, sortOrder: 1),
        throwsA(
          isA<ApiException>().having(
            (e) => e.message,
            'message',
            '제출할 수 없는 숙제입니다.',
          ),
        ),
      );
    });
  });
}
