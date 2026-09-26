import 'package:dio/dio.dart';

import '../../../core/api/api_response.dart';
import 'student_homework_models.dart';

/// 학생 숙제 API. 엔드포인트는 `StudentHomeworkController` 가 정본이다.
///
/// **제출 경로의 차단은 서버 한 곳이다**(`findEditableSubmission`, CLAUDE.md
/// 4-4). 화면이 버튼을 안 그리는 건 안내일 뿐이라 여기서 검사를 흉내 내지
/// 마라 — 서버가 409 로 막고 그 문구를 그대로 보여주면 된다.
class StudentHomeworkRepository {
  const StudentHomeworkRepository(this._dio);

  final Dio _dio;

  /// 첫 페이지(20건)만 받는다 — 웹과 같다.
  ///
  /// [year]·[month] 는 **둘 다 보내야** 달 필터가 걸린다(서버가 연·월이 다
  /// 있어야 범위를 건다). 달을 걸면 수업에 안 붙은 ONLINE 숙제는 빠진다.
  Future<List<StudentHomeworkItem>> list({int? year, int? month}) {
    assert((year == null) == (month == null), '연·월은 함께 보낸다');
    return unwrapCall(
      () => _dio.get<Map<String, dynamic>>(
        '/api/student/homeworks',
        queryParameters: {
          if (year != null && month != null) ...{'year': year, 'month': month},
        },
      ),
      (data) =>
          (((data as Map<String, dynamic>?)?['items'] as List<dynamic>?) ??
                  const [])
              .map(
                (e) => StudentHomeworkItem.fromJson(e as Map<String, dynamic>),
              )
              .toList(),
    );
  }

  /// 「이번 주에 낼 것」 — 반마다 가장 최근 공개된 수업의 수업 숙제 글(4-7).
  Future<List<HomeworkNote>> notes() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/homeworks/notes'),
    (data) => ((data as List<dynamic>?) ?? const [])
        .map((e) => HomeworkNote.fromJson(e as Map<String, dynamic>))
        .toList(),
  );

  Future<StudentHomeworkDetail> detail(int homeworkId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/homeworks/$homeworkId'),
    (data) => StudentHomeworkDetail.fromJson(data! as Map<String, dynamic>),
  );

  Future<UploadTarget> photoUploadUrl(
    int homeworkId, {
    required String contentType,
    required int bytes,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/photos/upload-url',
      data: {'contentType': contentType, 'bytes': bytes},
    ),
    (data) => UploadTarget.fromJson(data! as Map<String, dynamic>),
  );

  Future<void> registerPhoto(
    int homeworkId, {
    required String s3Key,
    required int sortOrder,
    required int bytes,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/photos',
      data: {'s3Key': s3Key, 'sortOrder': sortOrder, 'bytes': bytes},
    ),
    (_) {},
  );

  Future<void> deletePhoto(int homeworkId, int photoId) => unwrapCall(
    () => _dio.delete<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/photos/$photoId',
    ),
    (_) {},
  );

  Future<UploadTarget> videoUploadUrl(
    int homeworkId, {
    required String contentType,
    required int bytes,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/video/upload-url',
      data: {'contentType': contentType, 'bytes': bytes},
    ),
    (data) => UploadTarget.fromJson(data! as Map<String, dynamic>),
  );

  /// 이미 영상이 있으면 덮어쓴다. 서버가 이전 파일을 S3 에서 지운다.
  Future<void> registerVideo(
    int homeworkId, {
    required String s3Key,
    required int bytes,
  }) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/video',
      data: {'s3Key': s3Key, 'bytes': bytes},
    ),
    (_) {},
  );

  Future<void> deleteVideo(int homeworkId) => unwrapCall(
    () => _dio.delete<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/video',
    ),
    (_) {},
  );

  /// 제출 확정. GRID 재제출이면 서버가 이 자리에서 ⭕로 바꾼다(4-5) — 그
  /// 뒤로는 수정이 잠긴다.
  Future<void> submit(int homeworkId) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/homeworks/$homeworkId/submit',
    ),
    (_) {},
  );
}
