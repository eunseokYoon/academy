import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/student/homeworks/student_homework_controllers.dart';
import 'package:academy_app/features/student/homeworks/student_homework_detail_page.dart';
import 'package:academy_app/features/student/homeworks/student_homework_models.dart';
import 'package:academy_app/features/student/homeworks/student_homework_repository.dart';
import 'package:academy_app/features/student/homeworks/submission_media.dart';
import 'package:academy_app/shared/widgets/submit_button.dart';

StudentHomeworkDetail _detail({
  String kind = 'ONLINE',
  String status = 'NOT_SUBMITTED',
  bool resubmitRequired = false,
  List<SubmissionPhoto> photos = const [],
  SubmissionVideo? video,
  String? description,
}) => StudentHomeworkDetail(
  homeworkId: 7,
  title: '단어 3과',
  description: description,
  kind: kind,
  lessonDate: '2026-09-14',
  dueAt: '2026-09-21T12:00:00Z',
  classRoomName: 'A반',
  status: status,
  isLate: false,
  photos: photos,
  video: video,
  resubmitRequired: resubmitRequired,
);

class _Repo implements StudentHomeworkRepository {
  _Repo(this.current);

  StudentHomeworkDetail current;
  int detailCalls = 0;
  int submitCalls = 0;
  final List<int> deletedPhotos = [];
  Object? submitError;

  @override
  Future<StudentHomeworkDetail> detail(int homeworkId) async {
    detailCalls++;
    return current;
  }

  @override
  Future<void> submit(int homeworkId) async {
    submitCalls++;
    if (submitError != null) throw submitError!;
  }

  @override
  Future<void> deletePhoto(int homeworkId, int photoId) async =>
      deletedPhotos.add(photoId);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Uploader implements SubmissionUploader {
  _Uploader(this.repository);

  @override
  final _Repo repository;

  final List<int> photoSortOrders = [];
  final List<PickedVideo> videos = [];

  /// 순서대로 쓴다. null 이면 성공.
  final List<Object?> photoResults = [];

  @override
  Future<void> uploadPhoto(
    int homeworkId,
    PreparedPhoto photo, {
    required int sortOrder,
  }) async {
    photoSortOrders.add(sortOrder);
    final r = photoResults.isEmpty ? null : photoResults.removeAt(0);
    if (r != null) throw r;
  }

  @override
  Future<void> uploadVideo(
    int homeworkId,
    PickedVideo video, {
    void Function(double progress)? onProgress,
  }) async {
    videos.add(video);
  }
}

class _Picker implements MediaPicker {
  List<PreparedPhoto> photos = const [];
  PickedVideo? video;
  final List<(MediaFrom, int)> photoCalls = [];

  @override
  Future<List<PreparedPhoto>> pickPhotos(
    MediaFrom from, {
    required int limit,
  }) async {
    photoCalls.add((from, limit));
    return photos;
  }

  @override
  Future<PickedVideo?> pickVideo(MediaFrom from) async => video;
}

PreparedPhoto _photo() =>
    PreparedPhoto(bytes: Uint8List(4), contentType: 'image/webp');

class _Harness {
  _Harness(StudentHomeworkDetail d) : repo = _Repo(d), picker = _Picker() {
    uploader = _Uploader(repo);
  }

  final _Repo repo;
  final _Picker picker;
  late final _Uploader uploader;
  int changed = 0;

  Future<void> pump(WidgetTester tester) async {
    // 사진 칸·영상 칸·버튼이 한 화면에 들어오게 세로를 늘린다.
    tester.view.physicalSize = const Size(390 * 3, 2000 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final c = StudentHomeworkDetailController(repository: repo);
    addTearDown(c.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StudentHomeworkDetailPage(
            homeworkId: 7,
            controller: c,
            uploader: uploader,
            picker: picker,
            onChanged: () => changed++,
            videoView: (url) => Text('VIDEO:$url'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }
}

SubmitButton _submit(WidgetTester tester) =>
    tester.widget<SubmitButton>(find.byKey(const Key('submit')));

Future<void> _choose(WidgetTester tester, Key button, Key source) async {
  await tester.tap(find.byKey(button));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(source));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('다시 낼 게 없는 GRID 는 안내문만 있고 배지가 없다 (4-2)', (tester) async {
    await _Harness(_detail(kind: 'GRID')).pump(tester);
    expect(find.byKey(const Key('homework-header')), findsOneWidget);
    expect(find.text('다시 제출할 숙제가 아닙니다.'), findsOneWidget);
    expect(find.byKey(const Key('submission-badge')), findsNothing);
    expect(find.byKey(const Key('photo-card')), findsNothing);
    expect(find.byKey(const Key('submit')), findsNothing);
  });

  testWidgets('재제출을 낸 GRID 는 잠긴다 (4-5)', (tester) async {
    await _Harness(_detail(kind: 'GRID', status: 'SUBMITTED')).pump(tester);
    expect(find.text('제출했습니다. 더 이상 수정할 수 없습니다.'), findsOneWidget);
  });

  testWidgets('재제출이 열린 GRID 는 제출 화면이고 마감은 「다시 제출 마감」, KST 다', (tester) async {
    await _Harness(
      _detail(kind: 'GRID', resubmitRequired: true, description: '3번 다시'),
    ).pump(tester);
    expect(find.byKey(const Key('photo-card')), findsOneWidget);
    expect(find.text('다시 제출 마감 9월 21일 21:00'), findsOneWidget);
    expect(find.text('3번 다시'), findsOneWidget);
    expect(find.byKey(const Key('submission-badge')), findsOneWidget);
  });

  testWidgets('사진·영상이 없으면 제출 버튼이 꺼져 있다', (tester) async {
    await _Harness(_detail()).pump(tester);
    expect(find.byKey(const Key('need-media')), findsOneWidget);
    expect(_submit(tester).onPressed, isNull);
  });

  testWidgets('앨범에서 고른 사진을 한 장씩 올리고, 목록·홈을 낡게 표시한다', (tester) async {
    final h = _Harness(
      _detail(
        photos: const [SubmissionPhoto(photoId: 1, url: 'u1', sortOrder: 1)],
      ),
    );
    h.picker.photos = [_photo(), _photo()];
    await h.pump(tester);
    final before = h.repo.detailCalls;

    await _choose(tester, const Key('add-photo'), const Key('source-gallery'));

    // 남은 칸만큼만 고르게 한다(10 - 1).
    expect(h.picker.photoCalls.single, (MediaFrom.gallery, 9));
    // 이미 있는 1장 뒤에 이어 붙인다.
    expect(h.uploader.photoSortOrders, [2, 3]);
    expect(h.changed, 1);
    expect(h.repo.detailCalls, greaterThan(before)); // 새로 받았다
  });

  testWidgets('실패한 사진은 자리에 남고, 다시 시도 전까지 제출을 막는다', (tester) async {
    final h = _Harness(
      _detail(
        photos: const [SubmissionPhoto(photoId: 1, url: 'u1', sortOrder: 1)],
      ),
    );
    h.picker.photos = [_photo()];
    h.uploader.photoResults.add(const UploadFailure('사진을 올리지 못했습니다.'));
    await h.pump(tester);

    await _choose(tester, const Key('add-photo'), const Key('source-camera'));

    expect(find.text('실패'), findsOneWidget);
    expect(_submit(tester).onPressed, isNull);
    expect(h.changed, 0);

    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.text('실패'), findsNothing);
    expect(_submit(tester).onPressed, isNotNull);
    expect(h.changed, 1);
  });

  testWidgets('사진 삭제', (tester) async {
    final h = _Harness(
      _detail(
        photos: const [SubmissionPhoto(photoId: 4, url: 'u', sortOrder: 1)],
      ),
    );
    await h.pump(tester);
    await tester.tap(find.byKey(const ValueKey('delete-photo-4')));
    await tester.pumpAndSettle();
    expect(h.repo.deletedPhotos, [4]);
    expect(h.changed, 1);
  });

  testWidgets('100MB 넘는 영상은 올리지 않고 이유를 알린다', (tester) async {
    final h = _Harness(_detail());
    h.picker.video = const PickedVideo(
      path: 'a.mov',
      bytes: kMaxVideoBytes + 1,
      contentType: 'video/quicktime',
    );
    await h.pump(tester);
    await _choose(tester, const Key('add-video'), const Key('source-gallery'));
    expect(h.uploader.videos, isEmpty);
    expect(find.textContaining('영상은 100MB까지입니다'), findsOneWidget);
  });

  testWidgets('영상이 있으면 플레이어와 「영상 바꾸기」가 보인다', (tester) async {
    await _Harness(
      _detail(video: const SubmissionVideo(url: 'https://v', bytes: 10)),
    ).pump(tester);
    expect(find.text('VIDEO:https://v'), findsOneWidget);
    expect(find.text('영상 바꾸기'), findsOneWidget);
    expect(_submit(tester).onPressed, isNotNull); // 영상만 있어도 낼 수 있다
  });

  testWidgets('제출하면 새로 받고 목록·홈을 낡게 표시한다', (tester) async {
    final h = _Harness(
      _detail(
        photos: const [SubmissionPhoto(photoId: 1, url: 'u', sortOrder: 1)],
      ),
    );
    await h.pump(tester);
    h.repo.current = _detail(
      status: 'SUBMITTED',
      photos: const [SubmissionPhoto(photoId: 1, url: 'u', sortOrder: 1)],
    );
    await tester.tap(find.byKey(const Key('submit')));
    await tester.pumpAndSettle();
    expect(h.repo.submitCalls, 1);
    expect(h.changed, 1);
    expect(_submit(tester).label, '다시 제출하기');
  });

  testWidgets('제출을 서버가 거절하면 서버 문구 그대로다', (tester) async {
    final h = _Harness(
      _detail(
        photos: const [SubmissionPhoto(photoId: 1, url: 'u', sortOrder: 1)],
      ),
    );
    h.repo.submitError = const ApiException(
      code: 'NOT_EDITABLE',
      message: '제출할 수 없는 숙제입니다.',
    );
    await h.pump(tester);
    await tester.tap(find.byKey(const Key('submit')));
    await tester.pumpAndSettle();
    expect(find.text('제출할 수 없는 숙제입니다.'), findsOneWidget);
    expect(h.changed, 0);
  });

  testWidgets('360px 에서 가로로 넘치지 않는다', (tester) async {
    final h = _Harness(
      _detail(
        photos: [
          for (var i = 1; i <= 10; i++)
            SubmissionPhoto(photoId: i, url: 'u$i', sortOrder: i),
        ],
        video: const SubmissionVideo(url: 'v', bytes: 1),
        description: '긴 설명 ' * 40,
      ),
    );
    await h.pump(tester);
    tester.view.physicalSize = const Size(360 * 3, 2000 * 3);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('photo-card')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
