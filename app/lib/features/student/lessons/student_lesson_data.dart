import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/lesson/lesson_report.dart';

/// S-5 목록의 한 줄. 백엔드 `LessonReportListItemResponse`.
class StudentLessonListItem {
  const StudentLessonListItem({
    required this.lessonId,
    required this.lessonDate,
    required this.title,
    required this.classRoomName,
    required this.hasVideo,
    required this.isNew,
    required this.homeworkTitle,
  });

  final int lessonId;
  final String lessonDate;
  final String? title;
  final String classRoomName;
  final bool hasVideo;

  /// 최근 7일 안에 공개된 수업. 서버가 정한다.
  final bool isNew;
  final String? homeworkTitle;

  factory StudentLessonListItem.fromJson(Map<String, dynamic> json) =>
      StudentLessonListItem(
        lessonId: (json['lessonId'] as num).toInt(),
        lessonDate: json['lessonDate'] as String,
        title: json['title'] as String?,
        classRoomName: json['classRoomName'] as String,
        hasVideo: json['hasVideo'] as bool? ?? false,
        isNew: json['isNew'] as bool? ?? false,
        homeworkTitle: json['homeworkTitle'] as String?,
      );
}

class StudentLessonPage {
  const StudentLessonPage({required this.items, required this.totalPages});

  final List<StudentLessonListItem> items;
  final int totalPages;
}

/// 수업 영상 한 줄. 제목이 비면 화면이 「영상 N」으로 채운다.
///
/// **`embedUrl` 은 서버가 만든다**(`YoutubeUrls.embedUrlOf` 한 곳, 2026-09-04).
/// 앱에서 링크를 다시 조립하지 마라 — 일부 공개 재생목록이 안 열리던 문제를
/// 서버가 그 한 곳에서 비켜 간다.
class LessonVideo {
  const LessonVideo({required this.title, required this.embedUrl});

  final String? title;

  /// null 이면 열 수 없는 링크다 — 그 줄은 누를 수 없다.
  final String? embedUrl;

  factory LessonVideo.fromJson(Map<String, dynamic> json) => LessonVideo(
    title: json['title'] as String?,
    embedUrl: json['embedUrl'] as String?,
  );
}

/// S-5 상세. **학생 전용 모델이다** — 학부모는 [ParentLessonDetail] 을 쓴다.
class StudentLessonDetail {
  const StudentLessonDetail({
    required this.lessonId,
    required this.lessonDate,
    required this.title,
    required this.classRoomName,
    required this.videos,
    required this.notes,
    required this.homework,
    required this.homeworkDescription,
    required this.attendance,
  });

  final int lessonId;
  final String lessonDate;
  final String? title;
  final String classRoomName;
  final List<LessonVideo> videos;
  final LessonNotes notes;
  final LessonHomework? homework;

  /// 숙제 내용·재제출 상세(4-8). 학생에게만 온다.
  final String? homeworkDescription;

  /// null 이면 아직 확정 전이다. 결석이 아니다.
  final DayStatus? attendance;

  factory StudentLessonDetail.fromJson(Map<String, dynamic> json) {
    final hw = json['homework'] as Map<String, dynamic>?;
    return StudentLessonDetail(
      lessonId: (json['lessonId'] as num).toInt(),
      lessonDate: json['lessonDate'] as String,
      title: json['title'] as String?,
      classRoomName: json['classRoomName'] as String,
      // 새 배열 필드는 `?? []` 로 받는다(7-3).
      videos: ((json['videos'] as List<dynamic>?) ?? const [])
          .map((e) => LessonVideo.fromJson(e as Map<String, dynamic>))
          .toList(),
      notes: LessonNotes.fromJson(json),
      homework: hw == null ? null : LessonHomework.fromJson(hw),
      homeworkDescription: hw?['description'] as String?,
      attendance: json['attendanceStatus'] == null
          ? null
          : DayStatus.parse(json['attendanceStatus'] as String),
    );
  }
}

/// 목록 한 번의 조건. 달이 null 이면 「전체 월」이다.
typedef LessonQuery = ({int year, int? month, int page});

class StudentLessonRepository {
  const StudentLessonRepository(this._dio);

  final Dio _dio;

  /// 재원 기간 밖·미공개 수업은 서버가 이미 거른다(7). 앱에서 다시 거르지 마라.
  Future<StudentLessonPage> list(LessonQuery q) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/lessons',
      queryParameters: {
        'year': q.year,
        if (q.month != null) 'month': q.month,
        'page': q.page,
      },
    ),
    (data) {
      final map = (data as Map<String, dynamic>?) ?? const {};
      return StudentLessonPage(
        items: ((map['items'] as List<dynamic>?) ?? const [])
            .map(
              (e) => StudentLessonListItem.fromJson(e as Map<String, dynamic>),
            )
            .toList(),
        totalPages: (map['totalPages'] as num?)?.toInt() ?? 0,
      );
    },
  );

  Future<StudentLessonDetail> detail(int lessonId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/lessons/$lessonId'),
    (data) => StudentLessonDetail.fromJson(data! as Map<String, dynamic>),
  );
}

class StudentLessonsController
    extends ParamController<LessonQuery, StudentLessonPage> {
  StudentLessonsController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentLessonRepository _repository;

  @override
  Future<StudentLessonPage> fetch(LessonQuery param) => _repository.list(param);
}

class StudentLessonDetailController
    extends ParamController<int, StudentLessonDetail> {
  StudentLessonDetailController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentLessonRepository _repository;

  @override
  Future<StudentLessonDetail> fetch(int param) => _repository.detail(param);
}
