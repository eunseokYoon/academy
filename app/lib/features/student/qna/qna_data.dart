import 'dart:typed_data';

import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../core/api/s3_uploader.dart';
import '../homeworks/submission_media.dart';

/// 글·답글 하나에 붙는 사진 상한. 서버 `QnaService.MAX_PHOTOS` 와 같다.
const int kQnaMaxPhotos = 5;

/// 제목 상한. 서버 `@Size(max = 200)`.
const int kQnaTitleMax = 200;

class QnaPhoto {
  const QnaPhoto({required this.photoId, required this.url});

  final int photoId;

  /// presigned URL 이라 곧 만료된다. 깨지면 빈 자리로 둔다(웹과 같다).
  final String url;

  factory QnaPhoto.fromJson(Map<String, dynamic> json) => QnaPhoto(
    photoId: (json['photoId'] as num).toInt(),
    url: json['url'] as String,
  );
}

List<QnaPhoto> _photos(Object? raw) => [
  for (final e in (raw as List<dynamic>?) ?? const [])
    QnaPhoto.fromJson(e as Map<String, dynamic>),
];

/// 목록 한 줄. 백엔드 `QnaSummaryResponse`.
///
/// **상태 필드가 없다.** 답변 완료·미답변을 만들지 않기로 확정했다(2026-08-16)
/// — 강사 1명에게 200명분 상태를 닫는 절차가 병목이 된다.
class QnaSummary {
  const QnaSummary({
    required this.postId,
    required this.title,
    required this.authorName,
    required this.classRoomName,
    required this.isPublic,
    required this.hasPhoto,
    required this.answerCount,
  });

  final int postId;
  final String title;

  /// `students.name` 이다(3-1).
  final String authorName;
  final String classRoomName;
  final bool isPublic;
  final bool hasPhoto;
  final int answerCount;

  factory QnaSummary.fromJson(Map<String, dynamic> json) => QnaSummary(
    postId: (json['postId'] as num).toInt(),
    title: json['title'] as String,
    authorName: json['authorName'] as String,
    classRoomName: json['classRoomName'] as String,
    isPublic: json['isPublic'] as bool? ?? false,
    hasPhoto: json['hasPhoto'] as bool? ?? false,
    answerCount: (json['answerCount'] as num?)?.toInt() ?? 0,
  );
}

class QnaAnswer {
  const QnaAnswer({
    required this.answerId,
    required this.authorName,
    required this.byTeacher,
    required this.content,
    required this.photos,
  });

  final int answerId;
  final String authorName;

  /// 선생님 답글이면 칸 색이 다르다.
  final bool byTeacher;
  final String content;
  final List<QnaPhoto> photos;

  factory QnaAnswer.fromJson(Map<String, dynamic> json) => QnaAnswer(
    answerId: (json['answerId'] as num).toInt(),
    authorName: json['authorName'] as String,
    byTeacher: json['byTeacher'] as bool? ?? false,
    content: json['content'] as String,
    photos: _photos(json['photos']),
  );
}

/// 상세. 질문 하나와 그 아래 답글들. 백엔드 `QnaDetailResponse`.
class QnaDetail {
  const QnaDetail({
    required this.postId,
    required this.title,
    required this.authorName,
    required this.classRoomName,
    required this.isPublic,
    required this.content,
    required this.photos,
    required this.editable,
    required this.answers,
  });

  final int postId;
  final String title;
  final String authorName;
  final String classRoomName;
  final bool isPublic;
  final String content;
  final List<QnaPhoto> photos;

  /// 지금 보는 사람이 지울 수 있나. 버튼을 그리는 근거일 뿐 — 서버가 다시 막는다.
  final bool editable;
  final List<QnaAnswer> answers;

  factory QnaDetail.fromJson(Map<String, dynamic> json) => QnaDetail(
    postId: (json['postId'] as num).toInt(),
    title: json['title'] as String,
    authorName: json['authorName'] as String,
    classRoomName: json['classRoomName'] as String,
    isPublic: json['isPublic'] as bool? ?? false,
    content: json['content'] as String,
    photos: _photos(json['photos']),
    editable: json['editable'] as bool? ?? false,
    answers: [
      for (final e in (json['answers'] as List<dynamic>?) ?? const [])
        QnaAnswer.fromJson(e as Map<String, dynamic>),
    ],
  );
}

/// 글쓰기의 반 고르기. `GET /api/student/me` 의 `classRooms` 를 쓴다 — 별도
/// 엔드포인트를 두지 않는다(웹과 같다).
class QnaClassRoom {
  const QnaClassRoom({required this.classRoomId, required this.name});

  final int classRoomId;
  final String name;
}

/// 내 수강 후기. 학생당 하나다(12번 — `student_id UNIQUE`).
class MyReview {
  const MyReview({required this.rating, required this.content});

  /// 별 개수. 0.5 단위, 0.5~5.0. **서버가 이미 표시값으로 바꿔 준다** — 저장값
  /// (2배)으로 바꾸는 일은 서버 `Ratings` 한 곳이다. 앱에서 곱하지 마라.
  final double rating;
  final String content;

  factory MyReview.fromJson(Map<String, dynamic> json) => MyReview(
    rating: (json['rating'] as num).toDouble(),
    content: json['content'] as String,
  );
}

/// 목록 화면 한 벌.
class QnaBoardData {
  const QnaBoardData({
    required this.items,
    required this.myReview,
    required this.classRooms,
  });

  final List<QnaSummary> items;

  /// null 이면 아직 안 썼다 — 404 가 아니다.
  final MyReview? myReview;
  final List<QnaClassRoom> classRooms;
}

class QnaRepository {
  const QnaRepository(this._dio);

  final Dio _dio;

  /// 내 반 전체의 첫 쪽(20개). 웹도 첫 쪽만 그린다.
  Future<List<QnaSummary>> list() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/qna'),
    (data) => [
      for (final e
          in ((data as Map<String, dynamic>?)?['items'] as List<dynamic>?) ??
              const [])
        QnaSummary.fromJson(e as Map<String, dynamic>),
    ],
  );

  Future<QnaDetail> detail(int postId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/qna/$postId'),
    (data) => QnaDetail.fromJson(data! as Map<String, dynamic>),
  );

  Future<List<QnaClassRoom>> classRooms() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/me'),
    (data) => [
      for (final e
          in ((data as Map<String, dynamic>?)?['classRooms']
                  as List<dynamic>?) ??
              const [])
        QnaClassRoom(
          classRoomId: ((e as Map<String, dynamic>)['classRoomId'] as num)
              .toInt(),
          name: e['name'] as String,
        ),
    ],
  );

  /// **공개 기본값은 비공개다**(웹과 같다) — 호출부가 정한다.
  Future<void> create({
    required int classRoomId,
    required String title,
    required String content,
    required bool isPublic,
    required List<String> s3Keys,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/qna',
      data: {
        'classRoomId': classRoomId,
        'title': title,
        'content': content,
        'isPublic': isPublic,
        's3Keys': s3Keys,
      },
    ),
    (_) {},
  );

  Future<void> answer(
    int postId, {
    required String content,
    required List<String> s3Keys,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/qna/$postId/comments',
      data: {'content': content, 's3Keys': s3Keys},
    ),
    (_) {},
  );

  /// 질문을 지우면 답글·사진이 함께 사라진다(서버 CASCADE).
  Future<void> delete(int postId) => unwrapCall(
    () => _dio.delete<Map<String, dynamic>>('/api/student/qna/$postId'),
    (_) {},
  );

  Future<({String uploadUrl, String s3Key})> uploadUrl({
    required String contentType,
    required int bytes,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/qna/photos/upload-url',
      data: {'contentType': contentType, 'bytes': bytes},
    ),
    (data) {
      final map = data! as Map<String, dynamic>;
      return (
        uploadUrl: map['uploadUrl'] as String,
        s3Key: map['s3Key'] as String,
      );
    },
  );

  /// 안 썼으면 null 이다(404 가 아니다).
  Future<MyReview?> myReview() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/reviews/me'),
    (data) =>
        data == null ? null : MyReview.fromJson(data as Map<String, dynamic>),
  );

  /// [exists] 면 고치고, 아니면 새로 쓴다. 새 후기를 두 번 만드는 길은 없다.
  Future<void> saveReview({
    required bool exists,
    required double rating,
    required String content,
  }) {
    final body = {'rating': rating, 'content': content};
    return unwrapCall(
      () => exists
          ? _dio.patch<Map<String, dynamic>>(
              '/api/student/reviews/me',
              data: body,
            )
          : _dio.post<Map<String, dynamic>>('/api/student/reviews', data: body),
      (_) {},
    );
  }

  Future<void> deleteReview() => unwrapCall(
    () => _dio.delete<Map<String, dynamic>>('/api/student/reviews/me'),
    (_) {},
  );
}

/// 올린 사진 한 장. 글이 저장되기 전에 올라가서 서버는 [s3Key] 만 받는다
/// (서명이 `userId` 에 묶인다 — 11-2). [bytes] 는 미리보기다 — 올라간 파일을
/// 다시 받아 보여줄 이유가 없다.
class QnaUploadedPhoto {
  const QnaUploadedPhoto({required this.s3Key, required this.bytes});

  final String s3Key;
  final Uint8List bytes;
}

/// 발급 → S3 직접 PUT. 등록은 글·답글 작성 요청이 `s3Keys` 로 함께 한다.
///
/// 사진은 [MediaPicker] 가 이미 줄였다(6번 — Android WebP, iOS JPEG). 서버
/// `QnaMediaKeys` 가 둘 다 받는다. **영상은 받지 않는다** — 숙제 영상 100MB
/// 상한을 게시판이 우회하게 된다.
class QnaUploader {
  QnaUploader({required this._repository, required this._s3});

  final QnaRepository _repository;
  final S3Uploader _s3;

  Future<QnaUploadedPhoto> upload(PreparedPhoto photo) async {
    final target = await _repository.uploadUrl(
      contentType: photo.contentType,
      bytes: photo.bytes.length,
    );
    try {
      await _s3.put(
        target.uploadUrl,
        body: photo.bytes,
        contentType: photo.contentType,
        length: photo.bytes.length,
      );
    } on S3UploadException {
      throw const UploadFailure('사진을 올리지 못했습니다. 다시 시도해 주세요.');
    }
    return QnaUploadedPhoto(s3Key: target.s3Key, bytes: photo.bytes);
  }
}

/// 질문 목록. 매개변수가 없어 빈 레코드다.
class QnaBoardController extends ParamController<(), QnaBoardData> {
  QnaBoardController({required this.repository, super.staleAfter, super.now});

  /// 화면의 폼(질문·후기)이 직접 부른다.
  final QnaRepository repository;

  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<QnaBoardData> fetch(() param) async {
    final (items, review, rooms) = await all3(
      repository.list(),
      repository.myReview(),
      repository.classRooms(),
    );
    return QnaBoardData(items: items, myReview: review, classRooms: rooms);
  }
}

class QnaDetailController extends ParamController<int, QnaDetail> {
  QnaDetailController({required this.repository, super.staleAfter, super.now});

  final QnaRepository repository;

  @override
  Future<QnaDetail> fetch(int param) => repository.detail(param);
}
